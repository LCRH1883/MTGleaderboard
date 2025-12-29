package com.intagri.mtgleader.ui.game

import androidx.lifecycle.*
import com.intagri.mtgleader.R
import com.intagri.mtgleader.model.TabletopType
import com.intagri.mtgleader.livedata.SingleLiveEvent
import com.intagri.mtgleader.model.counter.CounterModel
import com.intagri.mtgleader.model.counter.CounterTemplateModel
import com.intagri.mtgleader.model.player.PlayerModel
import com.intagri.mtgleader.model.player.PlayerSetupModel
import com.intagri.mtgleader.persistence.GameRepository
import com.intagri.mtgleader.view.counter.edit.CounterSelectionUiModel
import com.intagri.mtgleader.view.counter.edit.RearrangeCounterUiModel
import com.intagri.mtgleader.view.TableLayoutPosition
import dagger.hilt.android.lifecycle.HiltViewModel
import java.lang.IllegalArgumentException
import java.util.ArrayDeque
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope

@HiltViewModel
class GameViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        const val DEFAULT_TURN_TIMER_SECONDS = 5 * 60
        const val MAX_OVERTIME_SECONDS = 99 * 60 + 99
    }

    private val setupPlayers =
        savedStateHandle.get<List<PlayerSetupModel>>(GameActivity.ARGS_SETUP_PLAYERS)
            ?: throw IllegalArgumentException("PlayerSetupModels must be passed in intent")

    val startingLife = gameRepository.startingLife
    val tabletopType = gameRepository.tabletopType

    //Maps player ids to their available counters
    private val availableCountersMap: MutableMap<Int, List<CounterTemplateModel>> = mutableMapOf()

    private val playerMap: MutableMap<Int, GamePlayerUiModel> = mutableMapOf()

    private val _players = MutableLiveData<List<GamePlayerUiModel>>()
    val players: LiveData<List<GamePlayerUiModel>> = _players

    private val _keepScreenOn = MutableLiveData<Boolean>()
    val keepScreenOn: LiveData<Boolean> = _keepScreenOn

    private val _hideNavigation = MutableLiveData<Boolean>()
    val hideNavigation: LiveData<Boolean> = _hideNavigation

    private val _turnCount = MutableLiveData<Int>(1)
    val turnCount: LiveData<Int> = _turnCount

    private val _turnTimerEnabled = MutableLiveData<Boolean>(true)
    val turnTimerEnabled: LiveData<Boolean> = _turnTimerEnabled

    private val _turnTimerDurationSeconds = MutableLiveData<Int>(DEFAULT_TURN_TIMER_SECONDS)
    val turnTimerDurationSeconds: LiveData<Int> = _turnTimerDurationSeconds

    private val _turnTimerSeconds = MutableLiveData<Int>(DEFAULT_TURN_TIMER_SECONDS)
    val turnTimerSeconds: LiveData<Int> = _turnTimerSeconds

    private val _turnTimerOvertime = MutableLiveData<Boolean>(false)
    val turnTimerOvertime: LiveData<Boolean> = _turnTimerOvertime

    private val _turnTimerAlarmEvent = SingleLiveEvent<Unit>()
    val turnTimerAlarmEvent: LiveData<Unit> = _turnTimerAlarmEvent

    private val _turnTimerExpiredEvent = SingleLiveEvent<Unit>()
    val turnTimerExpiredEvent: LiveData<Unit> = _turnTimerExpiredEvent

    private val _playerRotationClockwise = MutableLiveData<Boolean>(true)
    val playerRotationClockwise: LiveData<Boolean> = _playerRotationClockwise

    private val _currentTurnPlayerId = MutableLiveData<Int?>(null)
    val currentTurnPlayerId: LiveData<Int?> = _currentTurnPlayerId

    private val _startingPlayerSelected = MutableLiveData<Boolean>(false)
    val startingPlayerSelected: LiveData<Boolean> = _startingPlayerSelected

    private var startingPlayerId: Int? = null
    private var startingPlayerSelectionEnabled = false
    private val turnHistory = ArrayDeque<TurnHistoryEntry>()
    private val playerOrderIds = setupPlayers.map { it.id }
    private val clockwiseTurnPositions = listOf(
        TableLayoutPosition.TOP_PANEL,
        TableLayoutPosition.RIGHT_PANEL_1,
        TableLayoutPosition.RIGHT_PANEL_2,
        TableLayoutPosition.RIGHT_PANEL_3,
        TableLayoutPosition.BOTTOM_PANEL,
        TableLayoutPosition.LEFT_PANEL_3,
        TableLayoutPosition.LEFT_PANEL_2,
        TableLayoutPosition.LEFT_PANEL_1,
    )
    private var turnTimerJob: Job? = null

    /**
     * Maps player id to an ordered list of selected counter template ids
     * Counters that are removed are replaced with null, to assist in preserving user-selected
     * order. Once changes are confirmed, nulls are removed
     */
    private val pendingCounterSelectionMap: MutableMap<Int, MutableList<Int?>> = mutableMapOf()

    init {
        _keepScreenOn.value = gameRepository.keepScreenOn
        _hideNavigation.value = gameRepository.hideNavigation
        initializePlayers()
    }

    private fun initializePlayers() {
        playerMap.clear()
        availableCountersMap.clear()
        pendingCounterSelectionMap.clear()
        for (i in setupPlayers.indices) {

            val player = GamePlayerUiModel(
                PlayerModel(
                    id = setupPlayers[i].id,
                    life = startingLife,
                    lifeCounter = setupPlayers[i].profile?.lifeCounter,
                    colorResId = setupPlayers[i].color.resId ?: R.color.white,
                ),
                pullToReveal = tabletopType != TabletopType.LIST,
                rearrangeButtonEnabled = false,
            )
            playerMap[i] = player
            availableCountersMap[i] = setupPlayers[i].profile?.counters ?: emptyList()

            /**
             * Make sure pending map of selection changes is in sync with whatever
             * the player starts with
             */
            pendingCounterSelectionMap[player.model.id] =
                player.model.counters.map { counter ->
                    counter.template.id
                }.toMutableList()

            playerMap[i]?.counterSelections = generateSelectionUiModelsForPlayer(i)
            playerMap[i]?.rearrangeCounters = generateRearrangeUiModelsForPlayer(i)
        }
        applyStartingPlayerState()
        applyCurrentTurnState()
        _players.value = playerMap.values.toList()
        _startingPlayerSelected.value = startingPlayerId != null
    }

    private fun applyStartingPlayerState() {
        val selectionActive = startingPlayerSelectionEnabled && startingPlayerId == null
        playerMap.values.forEach { player ->
            player.isStartingPlayer = startingPlayerId == player.model.id
            player.isStartingPlayerSelectable = selectionActive
        }
    }

    private fun applyCurrentTurnState() {
        val currentId = _currentTurnPlayerId.value
        playerMap.values.forEach { player ->
            player.isCurrentTurnPlayer = player.model.id == currentId
        }
    }

    private fun updatePlayerState() {
        applyStartingPlayerState()
        applyCurrentTurnState()
        _players.value = playerMap.values.toList()
        _startingPlayerSelected.value = startingPlayerId != null
    }

    fun startStartingPlayerSelection() {
        if (startingPlayerId != null) {
            return
        }
        startingPlayerSelectionEnabled = true
        updatePlayerState()
    }

    fun selectStartingPlayer(playerId: Int) {
        if (!startingPlayerSelectionEnabled || startingPlayerId != null) {
            return
        }
        startingPlayerId = playerId
        startingPlayerSelectionEnabled = false
        _currentTurnPlayerId.value = playerId
        turnHistory.clear()
        resetTurnTimer()
        updatePlayerState()
    }

    fun selectRandomStartingPlayer() {
        if (startingPlayerId != null) {
            return
        }
        val playerIds = playerMap.values.map { it.model.id }
        if (playerIds.isNotEmpty()) {
            startingPlayerId = playerIds.random()
            startingPlayerSelectionEnabled = false
            _currentTurnPlayerId.value = startingPlayerId
            turnHistory.clear()
            resetTurnTimer()
            updatePlayerState()
        }
    }

    fun incrementPlayerLife(playerId: Int, lifeDifference: Int = 1) {
        playerMap[playerId]?.let {
            it.model = it.model.copy(
                life = it.model.life + lifeDifference
            )
            _players.value = playerMap.values.toList()
        }
    }

    fun incrementCounter(playerId: Int, counterId: Int, amountDifference: Int = 1) {
        playerMap[playerId]?.let { player: GamePlayerUiModel ->
            player.model.counters.find {
                it.template.id == counterId
            }?.let { counter: CounterModel ->
                val counterIndex = player.model.counters.indexOf(counter)
                val countersList = player.model.counters.toMutableList()
                countersList[counterIndex] =
                    counter.copy(amount = counter.amount + amountDifference)
                player.model = player.model.copy(counters = countersList)
                _players.value = playerMap.values.toList()
            }
        }
    }

    fun editCounters(playerId: Int) {
        playerMap[playerId]?.let { player ->
            player.currentMenu = GamePlayerUiModel.Menu.EDIT_COUNTERS
        }
        _players.value = playerMap.values.toList()
    }

    fun rearrangeCounters(playerId: Int) {
        playerMap[playerId]?.let { player ->
            player.currentMenu = GamePlayerUiModel.Menu.REARRANGE_COUNTERS
        }
        _players.value = playerMap.values.toList()
    }

    fun roll(playerId: Int) {
        playerMap[playerId]?.let { player ->
            player.currentMenu = GamePlayerUiModel.Menu.ROLL
        }
        _players.value = playerMap.values.toList()
    }

    fun closeSubMenu(playerId: Int) {
        playerMap[playerId]?.let { player ->
            player.currentMenu = GamePlayerUiModel.Menu.MAIN
        }
        cancelCounterChanges(playerId)
        _players.value = playerMap.values.toList()
    }

    fun selectCounter(playerId: Int, counterTemplateId: Int) {
        playerMap[playerId]?.let { player ->
            if (player.currentMenu != GamePlayerUiModel.Menu.EDIT_COUNTERS) {
                //Wrong menu is open which is an undefined state
                return
            }
            if (!pendingCounterSelectionMap.containsKey(playerId)) {
                pendingCounterSelectionMap[playerId] = mutableListOf()
            }
            val existingIndex =
                player.model.counters.indexOfFirst { counter -> counter.template.id == counterTemplateId }
            if (existingIndex != -1) {
                /**
                 * The player had this counter prior to editing. This means we left a null space
                 * when removing it, and it should be added in that same spot to preserve the
                 * user-selected order of counters
                 */
                pendingCounterSelectionMap[playerId]?.set(existingIndex, counterTemplateId)
            } else {
                //This is a new counter for the player, so we will add to the end
                pendingCounterSelectionMap[playerId]?.add(counterTemplateId)
            }
            player.counterSelections = generateSelectionUiModelsForPlayer(playerId)
            player.rearrangeCounters = generateRearrangeUiModelsForPlayer(playerId)
            _players.value = playerMap.values.toList()
        }
    }

    fun deselectCounter(playerId: Int, counterTemplateId: Int) {
        playerMap[playerId]?.let { player ->
            if (player.currentMenu != GamePlayerUiModel.Menu.EDIT_COUNTERS) {
                //Wrong menu is open which is an undefined state
                return
            }
            if (!pendingCounterSelectionMap.containsKey(playerId)) {
                pendingCounterSelectionMap[playerId] = mutableListOf()
            }
            /**
             * Set value to null instead of removing. This allows us to preserve ordering if that
             * counter is added back in before saving
             */
            val counterIndex =
                pendingCounterSelectionMap[playerId]?.indexOf(counterTemplateId) ?: -1
            if (counterIndex != -1) {
                pendingCounterSelectionMap[playerId]?.set(counterIndex, null)
                player.counterSelections = generateSelectionUiModelsForPlayer(playerId)
                player.rearrangeCounters = generateRearrangeUiModelsForPlayer(playerId)
                _players.value = playerMap.values.toList()
            }
        }
    }

    fun moveCounter(playerId: Int, oldPosition: Int, newPosition: Int) {
        playerMap[playerId]?.let { player ->
            if (player.currentMenu != GamePlayerUiModel.Menu.REARRANGE_COUNTERS) {
                //Wrong menu is open which is an undefined state
                return
            }
            if (!pendingCounterSelectionMap.containsKey(playerId)) {
                pendingCounterSelectionMap[playerId] =
                    player.model.counters.map { it.template.id }.toMutableList()
            }
            val pendingCounterSize = pendingCounterSelectionMap[playerId]?.size ?: 0
            if (oldPosition >= pendingCounterSize || newPosition >= pendingCounterSize) {
                return
            }
            pendingCounterSelectionMap[playerId]?.get(oldPosition)?.let {
                pendingCounterSelectionMap[playerId]?.removeAt(oldPosition)
                pendingCounterSelectionMap[playerId]?.add(newPosition, it)
            }
            player.rearrangeCounters = generateRearrangeUiModelsForPlayer(playerId)
        }
        _players.value = playerMap.values.toList()
    }

    /**
     * Does a diff of pending changes and currently added counters to see which need to be removed
     * or added
     */
    fun confirmCounterChanges(playerId: Int) {
        playerMap[playerId]?.let { uiModel ->

            pendingCounterSelectionMap[playerId]?.let { pendingCounterSelection ->
                var newCounter = false

                //for every pending id, either find the counter if it exists, or create a new counter
                //The order of pendingCounterSelection will be used
                val newCounters = pendingCounterSelection.mapNotNull {
                    uiModel.model.counters.find { oldCounter -> oldCounter.template.id == it }
                        ?: availableCountersMap[playerId]?.find { availableCounter -> availableCounter.id == it }
                            ?.let { template ->
                                newCounter = true
                                CounterModel(template = template, amount = template.startingValue)
                            }
                }

                uiModel.model = uiModel.model.copy(counters = newCounters)
                uiModel.rearrangeButtonEnabled = uiModel.model.counters.size > 1
                uiModel.newCounterAdded = newCounter
                uiModel.currentMenu = GamePlayerUiModel.Menu.MAIN
                _players.value = playerMap.values.toList()
            }
        }
    }

    /**
     * Removes pending changes and resets selection state to the player's currently added
     * counters. Private method -- closeSubMenu can be used publicly
     */
    private fun cancelCounterChanges(playerId: Int) {
        pendingCounterSelectionMap[playerId]?.clear()
        playerMap[playerId]?.model?.counters?.let {
            for (counterModel in it) {
                pendingCounterSelectionMap[playerId]?.add(counterModel.template.id)
            }
        }
        playerMap[playerId]?.counterSelections = generateSelectionUiModelsForPlayer(playerId)
        playerMap[playerId]?.rearrangeCounters = generateRearrangeUiModelsForPlayer(playerId)
        _players.value = playerMap.values.toList()
    }

    /**
     * takes current/pending selections and creates selection uiModels that are passed to the edit
     * counters recyclerview
     */
    private fun generateSelectionUiModelsForPlayer(playerId: Int): List<CounterSelectionUiModel> {
        return playerMap[playerId]?.let {
            availableCountersMap[playerId]?.map {
                CounterSelectionUiModel(
                    it,
                    pendingCounterSelectionMap[playerId]?.contains(it.id) == true
                )
            }
        } ?: emptyList()
    }

    private fun generateRearrangeUiModelsForPlayer(playerId: Int): List<RearrangeCounterUiModel> {
        return playerMap[playerId]?.let {
            pendingCounterSelectionMap[playerId]?.mapNotNull {
                availableCountersMap[playerId]?.find { availableCounter -> availableCounter.id == it }
                    ?.let {
                        RearrangeCounterUiModel(
                            it
                        )
                    }
            }
        } ?: emptyList()
    }

    fun setKeepScreenOn(keepScreenOn: Boolean) {
        gameRepository.keepScreenOn = keepScreenOn
        _keepScreenOn.value = keepScreenOn
    }

    fun setHideNavigation(hideNavigation: Boolean) {
        gameRepository.hideNavigation = hideNavigation
        _hideNavigation.value = hideNavigation
    }

    fun setPlayerRotationClockwise(clockwise: Boolean) {
        _playerRotationClockwise.value = clockwise
        updatePlayerState()
    }

    fun setTurnTimerEnabled(enabled: Boolean) {
        _turnTimerEnabled.value = enabled
        if (enabled) {
            resetTurnTimer()
        } else {
            _turnTimerOvertime.value = false
            stopTurnTimer()
        }
    }

    fun setTurnTimerDuration(minutes: Int, seconds: Int) {
        val clampedMinutes = minutes.coerceIn(0, 99)
        val clampedSeconds = seconds.coerceIn(0, 59)
        val totalSeconds = clampedMinutes * 60 + clampedSeconds
        _turnTimerDurationSeconds.value = totalSeconds
        _turnTimerSeconds.value = totalSeconds
        _turnTimerOvertime.value = false
        if (_turnTimerEnabled.value == true && _currentTurnPlayerId.value != null) {
            startTurnTimer()
        } else {
            stopTurnTimer()
        }
    }

    fun endTurn(playerId: Int) {
        val currentId = _currentTurnPlayerId.value ?: return
        if (currentId != playerId) {
            return
        }
        val order = getTurnOrderIds()
        val currentIndex = order.indexOf(currentId)
        if (currentIndex == -1 || order.isEmpty()) {
            return
        }
        val nextIndex = (currentIndex + 1) % order.size
        val nextPlayerId = order[nextIndex]
        val incrementedTurn = startingPlayerId != null && nextPlayerId == startingPlayerId
        turnHistory.addLast(TurnHistoryEntry(currentId, incrementedTurn))
        _currentTurnPlayerId.value = nextPlayerId
        if (incrementedTurn) {
            _turnCount.value = (_turnCount.value ?: 1) + 1
        }
        resetTurnTimer()
        updatePlayerState()
    }

    fun goBackTurn() {
        if (turnHistory.isEmpty()) {
            return
        }
        val entry = turnHistory.removeLast()
        _currentTurnPlayerId.value = entry.previousPlayerId
        if (entry.incrementedTurn) {
            val currentCount = _turnCount.value ?: 1
            _turnCount.value = maxOf(1, currentCount - 1)
        }
        resetTurnTimer()
        updatePlayerState()
    }

    private fun getTurnOrderIds(): List<Int> {
        val clockwise = _playerRotationClockwise.value != false
        val baseOrder = playerOrderIds
        if (tabletopType == TabletopType.LIST || tabletopType == TabletopType.NONE) {
            return if (clockwise) baseOrder else baseOrder.reversed()
        }
        val positions = tabletopType.positions
        if (positions.isEmpty()) {
            return if (clockwise) baseOrder else baseOrder.reversed()
        }
        if (positions.contains(TableLayoutPosition.SOLO_PANEL)) {
            return baseOrder.take(1)
        }
        val positionToPlayer = mutableMapOf<TableLayoutPosition, Int>()
        for (i in positions.indices) {
            if (i < baseOrder.size) {
                positionToPlayer[positions[i]] = baseOrder[i]
            }
        }
        val orderedPlayers = clockwiseTurnPositions
            .filter { positionToPlayer.containsKey(it) }
            .mapNotNull { positionToPlayer[it] }
        return if (clockwise) orderedPlayers else orderedPlayers.reversed()
    }

    fun resetGame() {
        startingPlayerId = null
        startingPlayerSelectionEnabled = false
        turnHistory.clear()
        _currentTurnPlayerId.value = null
        _turnCount.value = 1
        resetTurnTimer()
        initializePlayers()
    }

    private data class TurnHistoryEntry(
        val previousPlayerId: Int,
        val incrementedTurn: Boolean,
    )

    private fun resetTurnTimer() {
        val durationSeconds = _turnTimerDurationSeconds.value ?: DEFAULT_TURN_TIMER_SECONDS
        _turnTimerSeconds.value = durationSeconds
        _turnTimerOvertime.value = false
        if (_turnTimerEnabled.value == true && _currentTurnPlayerId.value != null) {
            startTurnTimer()
        } else {
            stopTurnTimer()
        }
    }

    private fun startTurnTimer() {
        stopTurnTimer()
        turnTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val isOvertime = _turnTimerOvertime.value == true
                if (isOvertime) {
                    val currentSeconds = _turnTimerSeconds.value ?: 0
                    val nextSeconds = currentSeconds - 1
                    if (-nextSeconds >= MAX_OVERTIME_SECONDS) {
                        _turnTimerSeconds.value = -MAX_OVERTIME_SECONDS
                        _turnTimerExpiredEvent.call()
                        stopTurnTimer()
                        return@launch
                    }
                    _turnTimerSeconds.value = nextSeconds
                    continue
                }
                val currentSeconds = _turnTimerSeconds.value ?: 0
                if (currentSeconds <= 0) {
                    _turnTimerSeconds.value = 0
                    _turnTimerOvertime.value = true
                    _turnTimerAlarmEvent.call()
                    continue
                }
                _turnTimerSeconds.value = currentSeconds - 1
            }
        }
    }

    private fun stopTurnTimer() {
        turnTimerJob?.cancel()
        turnTimerJob = null
    }
}
