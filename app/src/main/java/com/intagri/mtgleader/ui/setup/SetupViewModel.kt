package com.intagri.mtgleader.ui.setup

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intagri.mtgleader.model.TabletopType
import com.intagri.mtgleader.model.player.PlayerColor
import com.intagri.mtgleader.model.player.PlayerSetupModel
import com.intagri.mtgleader.model.player.PlayerProfileModel
import com.intagri.mtgleader.persistence.GameRepository
import com.intagri.mtgleader.persistence.ProfileRepository
import com.intagri.mtgleader.persistence.gamesession.GameSessionRepository
import com.intagri.mtgleader.persistence.gamesession.GameSessionWithParticipants
import com.intagri.mtgleader.ui.settings.profiles.manage.ProfileUiModel
import com.intagri.mtgleader.util.GameSetupUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val profilesRepository: ProfileRepository,
    private val gameSessionRepository: GameSessionRepository,
) : ViewModel() {

    private val _startingLife = MutableLiveData<Int>()
    val startingLife: LiveData<Int> get() = _startingLife

    private val _keepScreenOn = MutableLiveData<Boolean>()
    val keepScreenOn: LiveData<Boolean> get() = _keepScreenOn

    private val _hideNavigation = MutableLiveData<Boolean>()
    val hideNavigation: LiveData<Boolean> get() = _hideNavigation

    private val _numberOfPlayers = MutableLiveData<Int>()
    val numberOfPlayers: LiveData<Int> get() = _numberOfPlayers

    private val _setupPlayers = MutableLiveData<List<PlayerSetupModel>>()
    val setupPlayers: LiveData<List<PlayerSetupModel>> get() = _setupPlayers

    private val _profiles = MutableLiveData<List<ProfileUiModel>>()
    val profiles: LiveData<List<ProfileUiModel>> get() = _profiles

    private val _tabletopTypes = MutableLiveData<List<TabletopLayoutSelectionUiModel>>()
    val tabletopTypes: LiveData<List<TabletopLayoutSelectionUiModel>> get() = _tabletopTypes

    private val _showCustomizeLayoutButton = MutableLiveData<Boolean>(false)
    val showCustomizeLayoutButton: LiveData<Boolean> get() = _showCustomizeLayoutButton

    private val _resumeGameInfo = MutableLiveData<ResumeGameInfo?>()
    val resumeGameInfo: LiveData<ResumeGameInfo?> get() = _resumeGameInfo

    //Generate 8 unique random colors from list to use for player creation
    private val playerColors = PlayerColor.allColors()

    private var playerProfiles: List<PlayerProfileModel>? = null

    var selectedTabletopType: TabletopType = TabletopType.NONE
        private set

    private val availableTabletopTypes: List<TabletopType>
        get() = TabletopType.getListForNumber(
            _numberOfPlayers.value ?: 0
        )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            profilesRepository.getAllPlayerProfiles()
                .catch {
                    //TODO: error handling?
                }
                .collect {
                    playerProfiles = it
                    _profiles.value = it.map { profile ->
                        ProfileUiModel(profile)
                    }
                    _startingLife.value = gameRepository.startingLife
                    setTabletopType(gameRepository.tabletopType)
                    setNumberOfPlayers(gameRepository.numberOfPlayers)
                    _keepScreenOn.value = gameRepository.keepScreenOn
                    _hideNavigation.value = gameRepository.hideNavigation
                    _resumeGameInfo.value = buildResumeInfo(
                        gameSessionRepository.getLatestInProgress(),
                        it
                    )
                }
        }
    }

    fun setNumberOfPlayers(number: Int) {
        gameRepository.numberOfPlayers = number
        _numberOfPlayers.value = number
        val newTabletopType =
            if (availableTabletopTypes.contains(selectedTabletopType)) selectedTabletopType
            else availableTabletopTypes[0]
        setTabletopType(newTabletopType)
        /**
         * Add to existing player models or create a sublist if the number has decreased
         */
        val newList = (_setupPlayers.value?.take(number))?.map { player ->
            /**
             * Profiles may have been edited or deleted since these models were created.
             * So we should update profiles and reset them to default if they no longer
             * exist
             */
            player.copy(profile = playerProfiles?.find { it.name == player.profile?.name }
                ?: playerProfiles?.find { it.name == PlayerProfileModel.NAME_DEFAULT }
            )
        }?.toMutableList() ?: mutableListOf()
        while (newList.size < number) {
            newList.add(
                PlayerSetupModel(
                    id = newList.size, //next index
                    //Find the first color that is not currently taken by a player
                    color = playerColors.find { color -> newList.find { it.color == color } == null }
                        ?: PlayerColor.NONE,
                    profile = playerProfiles?.find { it.name == PlayerProfileModel.NAME_DEFAULT }
                )
            )
        }
        _setupPlayers.value = newList
    }

    fun setKeepScreenOn(keepScreenOn: Boolean) {
        gameRepository.keepScreenOn = keepScreenOn
        _keepScreenOn.value = keepScreenOn
    }

    fun setHideNavigation(hideNavigation: Boolean) {
        gameRepository.hideNavigation = hideNavigation
        _hideNavigation.value = hideNavigation
    }

    fun setStartingLife(startingLife: Int) {
        gameRepository.startingLife = startingLife
        _startingLife.value = startingLife
    }

    fun setTabletopType(tabletopType: TabletopType) {
        gameRepository.tabletopType = tabletopType
        selectedTabletopType = tabletopType
        _showCustomizeLayoutButton.value = tabletopType != TabletopType.NONE
        _tabletopTypes.value = availableTabletopTypes.map {
            TabletopLayoutSelectionUiModel(
                it,
                it == selectedTabletopType
            )
        }
    }

    fun findSetupPlayerById(id: Int): PlayerSetupModel? {
        return _setupPlayers.value?.find { it.id == id }
    }

    fun updatePlayer(playerSetupModel: PlayerSetupModel) {
        _setupPlayers.value?.let { playerList ->
            playerList.find { it.id == playerSetupModel.id }?.let { existingPlayer ->
                val existingColor = existingPlayer.color
                _setupPlayers.value = playerList.map {
                    if (it.id == playerSetupModel.id) {
                        playerSetupModel
                    } else if (it.color == playerSetupModel.color) {
                        //If there was already a player with this new color, swap colors
                        it.copy(color = existingColor)
                    } else {
                        it
                    }
                }
            }

        }
    }

    /**
     * Generate a new list of player setup models that have additional color counters.
     * Only add color counters that correspond to a player in that game OTHER than the current player.
     *
     * Counters will be given IDs starting at -1 and going backwards to avoid conflicts
     * with database counters
     *
     * This method should be called only one setup is completely finalized since colors can
     * change after the fact
     */
    fun getSetupPlayersWithColorCounters(): List<PlayerSetupModel> {
        return GameSetupUtils.applyColorCounters(_setupPlayers.value ?: emptyList())
    }

    private fun buildResumeInfo(
        session: GameSessionWithParticipants?,
        profiles: List<PlayerProfileModel>
    ): ResumeGameInfo? {
        val resumeSession = session ?: return null
        if (resumeSession.participants.isEmpty()) {
            return null
        }
        val profilesByName = profiles.associateBy { it.name }
        val defaultProfile = profiles.find { it.name == PlayerProfileModel.NAME_DEFAULT }
        val setupPlayers = resumeSession.participants.sortedBy { it.seatIndex }.map { participant ->
            val profile = participant.profileName?.let { profilesByName[it] } ?: defaultProfile
            val color = runCatching {
                PlayerColor.valueOf(participant.colorName)
            }.getOrDefault(PlayerColor.NONE)
            PlayerSetupModel(
                id = participant.seatIndex,
                profile = profile,
                color = color,
                assignedUserId = participant.userId,
                assignedDisplayName = participant.userId?.let { participant.displayName },
                tempName = if (participant.userId.isNullOrBlank()) {
                    participant.guestName ?: participant.displayName
                } else {
                    null
                },
            )
        }
        return ResumeGameInfo(
            localMatchId = resumeSession.session.localMatchId,
            tabletopType = runCatching {
                TabletopType.valueOf(resumeSession.session.tabletopType)
            }.getOrDefault(gameRepository.tabletopType),
            startingLife = resumeSession.participants.firstOrNull()?.startingLife
                ?: gameRepository.startingLife,
            setupPlayers = GameSetupUtils.applyColorCounters(setupPlayers),
        )
    }
}

data class ResumeGameInfo(
    val localMatchId: String,
    val tabletopType: TabletopType,
    val startingLife: Int,
    val setupPlayers: List<PlayerSetupModel>,
)
