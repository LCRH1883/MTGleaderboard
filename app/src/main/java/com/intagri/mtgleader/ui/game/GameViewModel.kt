package com.intagri.mtgleader.ui.game

import android.os.SystemClock
import androidx.lifecycle.*
import com.intagri.mtgleader.R
import com.intagri.mtgleader.model.TabletopType
import com.intagri.mtgleader.model.counter.CounterModel
import com.intagri.mtgleader.model.counter.CounterTemplateModel
import com.intagri.mtgleader.model.player.PlayerModel
import com.intagri.mtgleader.model.player.PlayerSetupModel
import com.intagri.mtgleader.persistence.GameRepository
import com.intagri.mtgleader.persistence.gamesession.GameCounterSnapshot
import com.intagri.mtgleader.persistence.gamesession.GameParticipantEntity
import com.intagri.mtgleader.persistence.gamesession.GameParticipantType
import com.intagri.mtgleader.persistence.gamesession.GameSessionEntity
import com.intagri.mtgleader.persistence.gamesession.GameSessionRepository
import com.intagri.mtgleader.persistence.gamesession.GameSessionStatus
import com.intagri.mtgleader.persistence.gamesession.GameSessionWithParticipants
import com.intagri.mtgleader.persistence.gamesession.PlacementUtils
import com.intagri.mtgleader.persistence.matches.MatchPayloadDto
import com.intagri.mtgleader.persistence.matches.MatchPlayerDto
import com.intagri.mtgleader.persistence.matches.MatchRepository
import com.intagri.mtgleader.persistence.stats.local.LocalStatsUpdater
import com.intagri.mtgleader.view.counter.edit.CounterSelectionUiModel
import com.intagri.mtgleader.view.counter.edit.RearrangeCounterUiModel
import com.intagri.mtgleader.view.TableLayoutPosition
import com.intagri.mtgleader.util.TimestampUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import java.lang.IllegalArgumentException
import java.util.ArrayDeque
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import com.intagri.mtgleader.livedata.SingleLiveEvent

@HiltViewModel
class GameViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val gameSessionRepository: GameSessionRepository,
    private val matchRepository: MatchRepository,
    private val localStatsUpdater: LocalStatsUpdater,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        const val DEFAULT_TURN_TIMER_SECONDS = 5 * 60
        const val MAX_OVERTIME_SECONDS = 99 * 60 + 99
        private const val AUTO_SAVE_INTERVAL_MS = 30_000L
        private const val SAVE_DEBOUNCE_MS = 500L
    }

    private val setupPlayers =
        savedStateHandle.get<List<PlayerSetupModel>>(GameActivity.ARGS_SETUP_PLAYERS)
            ?: throw IllegalArgumentException("PlayerSetupModels must be passed in intent")

    private val resumeMatchId: String? =
        savedStateHandle.get<String>(GameActivity.ARGS_RESUME_MATCH_ID)
    private val resumeTabletopType: TabletopType? =
        savedStateHandle.get<String>(GameActivity.ARGS_TABLETOP_TYPE)?.let { typeName ->
            runCatching { TabletopType.valueOf(typeName) }.getOrNull()
        }
    private val resumeStartingLife: Int? =
        savedStateHandle.get<Int>(GameActivity.ARGS_STARTING_LIFE)

    private val startingLife = resumeStartingLife ?: gameRepository.startingLife
    val tabletopType = resumeTabletopType ?: gameRepository.tabletopType

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

    private val _matchCompletedEvent = SingleLiveEvent<String>()
    val matchCompletedEvent: LiveData<String> = _matchCompletedEvent

    private val _confirmEliminationEvent = SingleLiveEvent<PendingElimination>()
    val confirmEliminationEvent: LiveData<PendingElimination> = _confirmEliminationEvent

    private val _gamePaused = MutableLiveData<Boolean>(false)
    val gamePaused: LiveData<Boolean> = _gamePaused

    private val _gameElapsedSeconds = MutableLiveData<Long>(0L)
    val gameElapsedSeconds: LiveData<Long> = _gameElapsedSeconds

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
    private var gameClockJob: Job? = null
    private var autoSaveJob: Job? = null
    private var pendingSaveJob: Job? = null
    private var gameStartElapsedMs: Long = SystemClock.elapsedRealtime()
    private var gamePausedAtMs: Long? = null
    private var gamePausedDurationMs: Long = 0L
    private var currentTurnStartElapsedMs: Long? = null
    private var turnPausedAtMs: Long? = null

    private val playerTurnTimeMs: MutableMap<Int, Long> = mutableMapOf()
    private val playerTurnsTaken: MutableMap<Int, Int> = mutableMapOf()
    private val eliminationInfo: MutableMap<Int, EliminationInfo> = mutableMapOf()
    private val pendingEliminations: MutableMap<Int, PendingElimination> = mutableMapOf()
    private val assignedUsers: MutableMap<Int, AssignedUser> = mutableMapOf()
    private val guestNames: MutableMap<Int, String> = mutableMapOf()

    private var localMatchId: String = resumeMatchId ?: UUID.randomUUID().toString()
    private var createdAtEpoch: Long = System.currentTimeMillis()
    private var startedAtEpoch: Long? = createdAtEpoch
    private var sessionReady = false
    private var matchCompleted = false

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
        startGameClock()
        startAutoSave()
        if (resumeMatchId != null) {
            viewModelScope.launch {
                restoreSession(resumeMatchId)
            }
        } else {
            sessionReady = true
            scheduleSave(true)
        }
    }


    private fun initializePlayers() {
        playerMap.clear()
        availableCountersMap.clear()
        pendingCounterSelectionMap.clear()
        playerTurnTimeMs.clear()
        playerTurnsTaken.clear()
        eliminationInfo.clear()
        pendingEliminations.clear()
        guestNames.clear()
        assignedUsers.clear()
        matchCompleted = false
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
            playerTurnTimeMs[player.model.id] = 0L
            playerTurnsTaken[player.model.id] = 0
            setupPlayers[i].assignedUserId?.let { userId ->
                val displayName = setupPlayers[i].assignedDisplayName
                    ?: setupPlayers[i].assignedUsername
                    ?: setupPlayers[i].tempName
                    ?: setupPlayers[i].profile?.name
                    ?: "Player ${i + 1}"
                assignedUsers[i] = AssignedUser(
                    userId = userId,
                    displayName = displayName,
                    username = setupPlayers[i].assignedUsername,
                    avatarUrl = setupPlayers[i].assignedAvatarUrl,
                )
            }
            if (!assignedUsers.containsKey(i)) {
                val tempName = setupPlayers[i].tempName
                if (!tempName.isNullOrBlank()) {
                    guestNames[i] = tempName
                }
            }

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
        updatePlayerState()
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

    private fun applyAssignedLabels() {
        playerMap.forEach { (playerId, player) ->
            val assignedUser = assignedUsers[playerId]
            if (assignedUser != null) {
                player.assignedUserLabel = formatAssignedLabel(assignedUser)
                player.assignedAvatarUrl = assignedUser.avatarUrl
            } else {
                player.assignedUserLabel = guestNames[playerId]
                player.assignedAvatarUrl = null
            }
        }
    }

    private fun formatAssignedLabel(assignedUser: AssignedUser?): String? {
        if (assignedUser == null) {
            return null
        }
        val baseName = assignedUser.displayName.ifBlank {
            assignedUser.username ?: ""
        }
        val username = assignedUser.username?.takeIf { it.isNotBlank() }
        return when {
            baseName.isNotBlank() && username != null && baseName != username ->
                "$baseName (@$username)"
            baseName.isNotBlank() -> baseName
            username != null -> "@$username"
            else -> assignedUser.userId
        }
    }

    private fun updatePlayerState() {
        applyStartingPlayerState()
        applyCurrentTurnState()
        applyAssignedLabels()
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
        currentTurnStartElapsedMs = SystemClock.elapsedRealtime()
        resetTurnTimer()
        updatePlayerState()
        scheduleSave()
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
            currentTurnStartElapsedMs = SystemClock.elapsedRealtime()
            resetTurnTimer()
            updatePlayerState()
            scheduleSave()
        }
    }

    fun incrementPlayerLife(playerId: Int, lifeDifference: Int = 1) {
        playerMap[playerId]?.let {
            it.model = it.model.copy(
                life = it.model.life + lifeDifference
            )
            recordEliminationIfNeeded(playerId)
            _players.value = playerMap.values.toList()
            scheduleSave()
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
                scheduleSave()
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
                scheduleSave()
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
        scheduleSave()
    }

    fun setTurnTimerEnabled(enabled: Boolean) {
        _turnTimerEnabled.value = enabled
        if (enabled) {
            resetTurnTimer()
        } else {
            _turnTimerOvertime.value = false
            stopTurnTimer()
        }
        scheduleSave()
    }

    fun setTurnTimerDuration(minutes: Int, seconds: Int) {
        val clampedMinutes = minutes.coerceIn(0, 99)
        val clampedSeconds = seconds.coerceIn(0, 59)
        val totalSeconds = clampedMinutes * 60 + clampedSeconds
        _turnTimerDurationSeconds.value = totalSeconds
        _turnTimerSeconds.value = totalSeconds
        _turnTimerOvertime.value = false
        if (_turnTimerEnabled.value == true && _currentTurnPlayerId.value != null &&
            _gamePaused.value != true
        ) {
            startTurnTimer()
        } else {
            stopTurnTimer()
        }
        scheduleSave()
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
        recordTurnEnd(SystemClock.elapsedRealtime())
        turnHistory.addLast(TurnHistoryEntry(currentId, incrementedTurn))
        _currentTurnPlayerId.value = nextPlayerId
        if (incrementedTurn) {
            _turnCount.value = (_turnCount.value ?: 1) + 1
        }
        currentTurnStartElapsedMs = SystemClock.elapsedRealtime()
        resetTurnTimer()
        updatePlayerState()
        scheduleSave()
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
        currentTurnStartElapsedMs = SystemClock.elapsedRealtime()
        resetTurnTimer()
        updatePlayerState()
        scheduleSave()
    }

    private fun getTurnOrderIds(): List<Int> {
        val aliveIds = getAlivePlayerIds()
        if (aliveIds.isEmpty()) {
            return playerOrderIds
        }
        val ordered = buildTurnOrder(playerOrderIds)
        return ordered.filter { aliveIds.contains(it) }
    }

    fun resetGame() {
        val now = System.currentTimeMillis()
        createdAtEpoch = now
        startedAtEpoch = now
        startingPlayerId = null
        startingPlayerSelectionEnabled = false
        turnHistory.clear()
        _currentTurnPlayerId.value = null
        _turnCount.value = 1
        currentTurnStartElapsedMs = null
        turnPausedAtMs = null
        assignedUsers.clear()
        resetGameClock()
        resetTurnTimer()
        initializePlayers()
        scheduleSave(true)
    }

    fun confirmElimination(playerId: Int) {
        if (eliminationInfo.containsKey(playerId)) {
            pendingEliminations.remove(playerId)
            return
        }
        val pending = pendingEliminations.remove(playerId)
        val eliminatedTurnNumber = pending?.eliminatedTurnNumber ?: (_turnCount.value ?: 1)
        val eliminatedDuringSeatIndex = pending?.eliminatedDuringSeatIndex ?: _currentTurnPlayerId.value
        eliminationInfo[playerId] = EliminationInfo(
            eliminatedTurnNumber = eliminatedTurnNumber,
            eliminatedDuringSeatIndex = eliminatedDuringSeatIndex,
        )
        val aliveIds = getAlivePlayerIds()
        if (aliveIds.size <= 1) {
            completeMatch()
            return
        }
        if (_currentTurnPlayerId.value == playerId) {
            moveToNextAliveTurn(playerId)
        }
        updatePlayerState()
        scheduleSave()
    }

    fun cancelElimination(playerId: Int) {
        pendingEliminations.remove(playerId)
    }

    fun completeMatch() {
        if (matchCompleted) {
            return
        }
        matchCompleted = true
        if (!sessionReady) {
            return
        }
        viewModelScope.launch {
            val currentTurnNumber = _turnCount.value ?: 1
            val currentTurnPlayerId = _currentTurnPlayerId.value
            val nowElapsedMs = SystemClock.elapsedRealtime()
            val currentTurnElapsedMs = resolveCurrentTurnElapsedMs(nowElapsedMs, currentTurnPlayerId)
            stopTurnTimer()
            stopGameClock()
            autoSaveJob?.cancel()
            pendingSaveJob?.cancel()

            val baseParticipants = buildParticipantSnapshots(
                currentTurnPlayerId = currentTurnPlayerId,
                currentTurnElapsedMs = currentTurnElapsedMs,
                placeOverrides = null,
                includeActiveTurn = true,
            )
            val places = PlacementUtils.computePlaces(baseParticipants)
            val participants = baseParticipants.map { participant ->
                participant.copy(place = places[participant.seatIndex])
            }
            val session = buildSessionSnapshot(
                status = GameSessionStatus.COMPLETED,
                endedAtEpoch = System.currentTimeMillis(),
                pendingSync = true,
                currentTurnNumber = currentTurnNumber,
                currentTurnPlayerId = currentTurnPlayerId,
            )
            gameSessionRepository.saveSnapshot(session, participants)
            localStatsUpdater.recordCompletedMatch(participants)

            val updatedAt = TimestampUtils.nowRfc3339Millis()
            val payload = buildMatchPayload(participants)
            matchRepository.recordMatchFromSession(
                localId = localMatchId,
                clientMatchId = localMatchId,
                updatedAt = updatedAt,
                payload = payload,
            )
            _matchCompletedEvent.value = localMatchId
        }
    }

    fun pauseGame() {
        if (_gamePaused.value == true) {
            return
        }
        _gamePaused.value = true
        gamePausedAtMs = SystemClock.elapsedRealtime()
        if (turnPausedAtMs == null) {
            turnPausedAtMs = gamePausedAtMs
        }
        stopTurnTimer()
        updateGameClock()
        scheduleSave()
    }

    fun resumeGame() {
        if (_gamePaused.value != true) {
            return
        }
        val now = SystemClock.elapsedRealtime()
        val pausedAt = gamePausedAtMs ?: now
        gamePausedDurationMs += now - pausedAt
        gamePausedAtMs = null
        turnPausedAtMs?.let {
            currentTurnStartElapsedMs = currentTurnStartElapsedMs?.plus(now - it)
        }
        turnPausedAtMs = null
        _gamePaused.value = false
        if (_turnTimerEnabled.value == true && _currentTurnPlayerId.value != null) {
            startTurnTimer()
        }
        updateGameClock()
        scheduleSave()
    }

    fun togglePause() {
        if (_gamePaused.value == true) {
            resumeGame()
        } else {
            pauseGame()
        }
    }

    fun assignPlayerToUser(
        playerId: Int,
        userId: String,
        displayName: String,
        username: String?,
        avatarUrl: String?
    ) {
        assignedUsers[playerId] = AssignedUser(
            userId = userId,
            displayName = displayName,
            username = username,
            avatarUrl = avatarUrl,
        )
        updatePlayerState()
        scheduleSave()
    }

    fun clearAssignedUser(playerId: Int) {
        if (assignedUsers.remove(playerId) != null) {
            if (!guestNames.containsKey(playerId)) {
                guestNames[playerId] = "Player ${playerId + 1}"
            }
            updatePlayerState()
            scheduleSave()
        }
    }

    fun getAssignedUserId(playerId: Int): String? {
        return assignedUsers[playerId]?.userId
    }

    fun setGuestName(playerId: Int, name: String) {
        val trimmed = name.trim().ifBlank { "Player ${playerId + 1}" }
        guestNames[playerId] = trimmed
        updatePlayerState()
        scheduleSave()
    }

    fun getGuestName(playerId: Int): String? {
        return guestNames[playerId]
    }

    private data class TurnHistoryEntry(
        val previousPlayerId: Int,
        val incrementedTurn: Boolean,
    )

    private data class EliminationInfo(
        val eliminatedTurnNumber: Int,
        val eliminatedDuringSeatIndex: Int?,
    )

    data class PendingElimination(
        val playerId: Int,
        val playerName: String,
        val eliminatedTurnNumber: Int,
        val eliminatedDuringSeatIndex: Int?,
    )

    private data class AssignedUser(
        val userId: String,
        val displayName: String,
        val username: String?,
        val avatarUrl: String?,
    )

    private fun recordTurnEnd(nowMs: Long) {
        val currentId = _currentTurnPlayerId.value ?: return
        val startedAt = currentTurnStartElapsedMs ?: nowMs
        val elapsedMs = (nowMs - startedAt).coerceAtLeast(0L)
        playerTurnTimeMs[currentId] = (playerTurnTimeMs[currentId] ?: 0L) + elapsedMs
        playerTurnsTaken[currentId] = (playerTurnsTaken[currentId] ?: 0) + 1
    }

    private fun recordEliminationIfNeeded(playerId: Int) {
        if (eliminationInfo.containsKey(playerId)) {
            return
        }
        if (pendingEliminations.containsKey(playerId)) {
            return
        }
        val life = playerMap[playerId]?.model?.life ?: return
        if (life > 0) {
            return
        }
        val pending = PendingElimination(
            playerId = playerId,
            playerName = resolvePlayerDisplayName(playerId),
            eliminatedTurnNumber = _turnCount.value ?: 1,
            eliminatedDuringSeatIndex = _currentTurnPlayerId.value,
        )
        pendingEliminations[playerId] = pending
        _confirmEliminationEvent.value = pending
    }

    private fun startAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            while (true) {
                delay(AUTO_SAVE_INTERVAL_MS)
                if (sessionReady) {
                    persistSnapshot()
                }
            }
        }
    }

    private fun scheduleSave(immediate: Boolean = false) {
        if (!sessionReady) {
            return
        }
        pendingSaveJob?.cancel()
        pendingSaveJob = viewModelScope.launch {
            if (!immediate) {
                delay(SAVE_DEBOUNCE_MS)
            }
            persistSnapshot()
        }
    }

    fun requestImmediateSave() {
        if (!sessionReady) {
            return
        }
        viewModelScope.launch {
            persistSnapshot()
        }
    }

    private suspend fun persistSnapshot() {
        val currentTurnNumber = _turnCount.value ?: 1
        val currentTurnPlayerId = _currentTurnPlayerId.value
        val nowElapsedMs = SystemClock.elapsedRealtime()
        val currentTurnElapsedMs = resolveCurrentTurnElapsedMs(nowElapsedMs, currentTurnPlayerId)
        val session = buildSessionSnapshot(
            status = GameSessionStatus.IN_PROGRESS,
            endedAtEpoch = null,
            pendingSync = false,
            currentTurnNumber = currentTurnNumber,
            currentTurnPlayerId = currentTurnPlayerId,
        )
        val participants = buildParticipantSnapshots(
            currentTurnPlayerId = currentTurnPlayerId,
            currentTurnElapsedMs = currentTurnElapsedMs,
            placeOverrides = null,
            includeActiveTurn = false,
        )
        gameSessionRepository.saveSnapshot(session, participants)
    }

    private fun buildSessionSnapshot(
        status: String,
        endedAtEpoch: Long?,
        pendingSync: Boolean,
        currentTurnNumber: Int,
        currentTurnPlayerId: Int?,
    ): GameSessionEntity {
        return GameSessionEntity(
            localMatchId = localMatchId,
            clientMatchId = localMatchId,
            createdAtEpoch = createdAtEpoch,
            startedAtEpoch = startedAtEpoch,
            endedAtEpoch = endedAtEpoch,
            tabletopType = tabletopType.name,
            status = status,
            startingSeatIndex = startingPlayerId,
            currentTurnNumber = currentTurnNumber,
            currentActiveSeatIndex = currentTurnPlayerId,
            turnOwnerSeatIndex = currentTurnPlayerId,
            turnRotationClockwise = _playerRotationClockwise.value != false,
            turnTimerEnabled = _turnTimerEnabled.value == true,
            turnTimerDurationSeconds = _turnTimerDurationSeconds.value ?: DEFAULT_TURN_TIMER_SECONDS,
            turnTimerSeconds = _turnTimerSeconds.value ?: DEFAULT_TURN_TIMER_SECONDS,
            turnTimerOvertime = _turnTimerOvertime.value == true,
            gamePaused = _gamePaused.value == true,
            gameElapsedSeconds = _gameElapsedSeconds.value ?: 0L,
            pendingSync = pendingSync,
            backendMatchId = null,
            updatedAtEpoch = System.currentTimeMillis(),
        )
    }

    private fun buildParticipantSnapshots(
        currentTurnPlayerId: Int?,
        currentTurnElapsedMs: Long,
        placeOverrides: Map<Int, Int>?,
        includeActiveTurn: Boolean,
    ): List<GameParticipantEntity> {
        return playerMap.entries.mapNotNull { (seatIndex, player) ->
            val setupPlayer = setupPlayers.getOrNull(seatIndex) ?: return@mapNotNull null
            val assignedUser = assignedUsers[seatIndex]
            val guestName = guestNames[seatIndex] ?: setupPlayer.tempName
            val isGuest = assignedUser == null && !guestName.isNullOrBlank()
            val fallbackName = if (isGuest) {
                guestName
            } else {
                setupPlayer.profile?.name
            } ?: "Player ${seatIndex + 1}"
            val displayName = assignedUser?.displayName ?: fallbackName
            val participantType = when {
                assignedUser != null -> GameParticipantType.ACCOUNT
                isGuest -> GameParticipantType.GUEST
                else -> GameParticipantType.LOCAL_PROFILE
            }
            val elimination = eliminationInfo[seatIndex]
            val baseTurnTimeMs = playerTurnTimeMs[seatIndex] ?: 0L
            val totalTurnTimeMs = if (seatIndex == currentTurnPlayerId) {
                baseTurnTimeMs + currentTurnElapsedMs
            } else {
                baseTurnTimeMs
            }
            val turnsTaken = (playerTurnsTaken[seatIndex] ?: 0) + if (
                includeActiveTurn && seatIndex == currentTurnPlayerId
            ) 1 else 0
            GameParticipantEntity(
                localMatchId = localMatchId,
                seatIndex = seatIndex,
                participantType = participantType,
                profileName = setupPlayer.profile?.name,
                userId = assignedUser?.userId,
                guestName = if (isGuest) guestName else null,
                displayName = displayName,
                colorName = setupPlayer.color.name,
                startingLife = startingLife,
                currentLife = player.model.life,
                countersJson = gameSessionRepository.encodeCounters(player.model.counters),
                eliminatedTurnNumber = elimination?.eliminatedTurnNumber,
                eliminatedDuringSeatIndex = elimination?.eliminatedDuringSeatIndex,
                place = placeOverrides?.get(seatIndex),
                totalTurnTimeMs = totalTurnTimeMs,
                turnsTaken = turnsTaken,
            )
        }
    }

    private fun resolvePlayerDisplayName(playerId: Int): String {
        val setupPlayer = setupPlayers.getOrNull(playerId)
        val assignedUser = assignedUsers[playerId]
        val guestName = guestNames[playerId] ?: setupPlayer?.tempName
        val isGuest = assignedUser == null && !guestName.isNullOrBlank()
        val fallback = if (isGuest) {
            guestName
        } else {
            setupPlayer?.profile?.name
        } ?: "Player ${playerId + 1}"
        return assignedUser?.displayName ?: fallback
    }

    private fun getAlivePlayerIds(): List<Int> {
        return playerOrderIds.filter { !eliminationInfo.containsKey(it) }
    }

    private fun moveToNextAliveTurn(eliminatedPlayerId: Int) {
        val alive = getAlivePlayerIds()
        if (alive.isEmpty()) {
            return
        }
        val order = buildTurnOrder(playerOrderIds)
        if (order.isEmpty()) {
            return
        }
        val eliminatedIndex = order.indexOf(eliminatedPlayerId)
        val nextAlive = if (eliminatedIndex == -1) {
            order.firstOrNull { alive.contains(it) } ?: alive.first()
        } else {
            (1..order.size)
                .map { offset -> order[(eliminatedIndex + offset) % order.size] }
                .firstOrNull { alive.contains(it) } ?: alive.first()
        }
        _currentTurnPlayerId.value = nextAlive
        currentTurnStartElapsedMs = SystemClock.elapsedRealtime()
        resetTurnTimer()
    }

    private fun buildTurnOrder(baseOrder: List<Int>): List<Int> {
        if (baseOrder.isEmpty()) {
            return emptyList()
        }
        val clockwise = _playerRotationClockwise.value != false
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

    private fun buildMatchPayload(
        participants: List<GameParticipantEntity>,
    ): MatchPayloadDto {
        val winnerSeat = participants.filter { it.place == 1 }
            .minByOrNull { it.seatIndex }
            ?.seatIndex
        val durationSeconds = _gameElapsedSeconds.value ?: 0L
        val players = participants.map { participant ->
            val guestName = when (participant.participantType) {
                GameParticipantType.LOCAL_PROFILE -> participant.profileName
                GameParticipantType.GUEST -> participant.guestName ?: participant.displayName
                else -> null
            }
            MatchPlayerDto(
                seatIndex = participant.seatIndex,
                seat = participant.seatIndex,
                userId = participant.userId,
                guestName = guestName,
                displayName = participant.displayName,
                profileName = participant.profileName,
                life = participant.currentLife,
                counters = emptyList(),
                place = participant.place,
                eliminatedTurnNumber = participant.eliminatedTurnNumber,
                eliminatedDuringSeatIndex = participant.eliminatedDuringSeatIndex,
                totalTurnTimeMs = participant.totalTurnTimeMs,
                turnsTaken = participant.turnsTaken,
            )
        }
        return MatchPayloadDto(
            players = players,
            winnerSeat = winnerSeat,
            durationSeconds = durationSeconds,
            tabletopType = tabletopType.name,
        )
    }

    private fun resolveCurrentTurnElapsedMs(
        nowElapsedMs: Long,
        currentTurnPlayerId: Int?,
    ): Long {
        return when {
            currentTurnPlayerId == null || currentTurnStartElapsedMs == null -> 0L
            _gamePaused.value == true && turnPausedAtMs != null ->
                (turnPausedAtMs ?: nowElapsedMs) - (currentTurnStartElapsedMs ?: nowElapsedMs)
            _gamePaused.value == true -> 0L
            else -> (nowElapsedMs - (currentTurnStartElapsedMs ?: nowElapsedMs)).coerceAtLeast(0L)
        }.coerceAtLeast(0L)
    }

    private suspend fun restoreSession(matchId: String) {
        val snapshot = gameSessionRepository.getSession(matchId)
        if (snapshot == null) {
            sessionReady = true
            scheduleSave(true)
            return
        }
        applySessionState(snapshot)
        sessionReady = true
    }

    private fun applySessionState(snapshot: GameSessionWithParticipants) {
        val session = snapshot.session
        localMatchId = session.localMatchId
        createdAtEpoch = session.createdAtEpoch
        startedAtEpoch = session.startedAtEpoch
        startingPlayerId = session.startingSeatIndex
        startingPlayerSelectionEnabled = false
        _turnCount.value = session.currentTurnNumber
        _currentTurnPlayerId.value = session.currentActiveSeatIndex
        _playerRotationClockwise.value = session.turnRotationClockwise
        _turnTimerEnabled.value = session.turnTimerEnabled
        _turnTimerDurationSeconds.value = session.turnTimerDurationSeconds
        _turnTimerSeconds.value = session.turnTimerSeconds
        _turnTimerOvertime.value = session.turnTimerOvertime
        applyGameClockState(session.gameElapsedSeconds, session.gamePaused)

        playerTurnTimeMs.clear()
        playerTurnsTaken.clear()
        eliminationInfo.clear()

        snapshot.participants.forEach { participant ->
            val playerId = participant.seatIndex
            val uiModel = playerMap[playerId] ?: return@forEach
            val counters = resolveCounters(
                playerId,
                gameSessionRepository.decodeCounters(participant.countersJson)
            )
            uiModel.model = uiModel.model.copy(
                life = participant.currentLife,
                counters = counters,
            )
            pendingCounterSelectionMap[playerId] = counters.map { it.template.id }.toMutableList()
            uiModel.counterSelections = generateSelectionUiModelsForPlayer(playerId)
            uiModel.rearrangeCounters = generateRearrangeUiModelsForPlayer(playerId)
            playerTurnTimeMs[playerId] = participant.totalTurnTimeMs ?: 0L
            playerTurnsTaken[playerId] = participant.turnsTaken ?: 0
            if (participant.eliminatedTurnNumber != null) {
                eliminationInfo[playerId] = EliminationInfo(
                    participant.eliminatedTurnNumber,
                    participant.eliminatedDuringSeatIndex
                )
            }
            if (!participant.userId.isNullOrBlank()) {
                assignedUsers[playerId] = AssignedUser(
                    userId = participant.userId,
                    displayName = participant.displayName,
                    username = null,
                    avatarUrl = null,
                )
            } else if (
                participant.participantType == GameParticipantType.GUEST &&
                !participant.guestName.isNullOrBlank()
            ) {
                guestNames[playerId] = participant.guestName
            }
        }

        currentTurnStartElapsedMs =
            if (_currentTurnPlayerId.value != null) SystemClock.elapsedRealtime() else null
        updatePlayerState()

        if (_gamePaused.value == true ||
            _currentTurnPlayerId.value == null ||
            _turnTimerEnabled.value != true
        ) {
            stopTurnTimer()
        } else {
            startTurnTimer()
        }
    }

    private fun applyGameClockState(elapsedSeconds: Long, paused: Boolean) {
        val now = SystemClock.elapsedRealtime()
        gameStartElapsedMs = now - elapsedSeconds * 1000L
        gamePausedDurationMs = 0L
        gamePausedAtMs = if (paused) now else null
        _gamePaused.value = paused
        _gameElapsedSeconds.value = elapsedSeconds
    }

    private fun resolveCounters(
        playerId: Int,
        snapshots: List<GameCounterSnapshot>
    ): List<CounterModel> {
        val available = availableCountersMap[playerId].orEmpty()
        return snapshots.mapNotNull { snapshot ->
            available.find { it.id == snapshot.templateId }?.let { template ->
                CounterModel(snapshot.amount, template)
            }
        }
    }

    private fun resetTurnTimer() {
        val durationSeconds = _turnTimerDurationSeconds.value ?: DEFAULT_TURN_TIMER_SECONDS
        _turnTimerSeconds.value = durationSeconds
        _turnTimerOvertime.value = false
        if (_turnTimerEnabled.value == true && _currentTurnPlayerId.value != null &&
            _gamePaused.value != true
        ) {
            startTurnTimer()
        } else {
            stopTurnTimer()
        }
    }

    private fun startTurnTimer() {
        if (_gamePaused.value == true) {
            return
        }
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

    private fun startGameClock() {
        stopGameClock()
        gameClockJob = viewModelScope.launch {
            while (true) {
                updateGameClock()
                delay(1000)
            }
        }
    }

    private fun stopGameClock() {
        gameClockJob?.cancel()
        gameClockJob = null
    }

    private fun resetGameClock() {
        gameStartElapsedMs = SystemClock.elapsedRealtime()
        gamePausedAtMs = null
        gamePausedDurationMs = 0L
        _gamePaused.value = false
        updateGameClock()
    }

    private fun updateGameClock() {
        val now = SystemClock.elapsedRealtime()
        val effectiveNow = gamePausedAtMs ?: now
        val elapsedMs = (effectiveNow - gameStartElapsedMs - gamePausedDurationMs).coerceAtLeast(0L)
        _gameElapsedSeconds.postValue(elapsedMs / 1000L)
    }

    override fun onCleared() {
        super.onCleared()
        autoSaveJob?.cancel()
        pendingSaveJob?.cancel()
    }
}
