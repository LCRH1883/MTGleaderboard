package com.intagri.mtgleader.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.intagri.mtgleader.persistence.ProfileRepository
import com.intagri.mtgleader.persistence.gamesession.GameParticipantType
import com.intagri.mtgleader.persistence.gamesession.GameSessionRepository
import com.intagri.mtgleader.persistence.gamesession.PlacementUtils
import com.intagri.mtgleader.persistence.stats.local.LocalStatsOpponentType
import com.intagri.mtgleader.persistence.stats.local.LocalStatsOwnerType
import com.intagri.mtgleader.persistence.stats.local.LocalStatsRepository
import com.intagri.mtgleader.persistence.stats.local.LocalStatsSummary
import com.intagri.mtgleader.persistence.userprofile.UserProfileLocalStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlin.math.max

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val userProfileLocalStore: UserProfileLocalStore,
    private val localStatsRepository: LocalStatsRepository,
    private val gameSessionRepository: GameSessionRepository,
) : ViewModel() {

    private val ownerOptionsFlow: StateFlow<List<StatsOwnerOption>> = combine(
        userProfileLocalStore.observe(),
        profileRepository.getAllPlayerProfiles()
    ) { user, profiles ->
        val options = mutableListOf<StatsOwnerOption>()
        if (user != null) {
            val label = user.displayName ?: user.username ?: "Account"
            options.add(
                StatsOwnerOption(
                    label = label,
                    ownerType = LocalStatsOwnerType.ACCOUNT,
                    ownerId = user.id,
                )
            )
        }
        profiles.forEach { profile ->
            options.add(
                StatsOwnerOption(
                    label = profile.name,
                    ownerType = LocalStatsOwnerType.LOCAL_PROFILE,
                    ownerId = profile.name,
                )
            )
        }
        options
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val selectedOwner = MutableStateFlow<StatsOwnerOption?>(null)

    val ownerOptions = ownerOptionsFlow.asLiveData()
    val selectedOwnerOption = selectedOwner.asLiveData()

    val summary = selectedOwner.filterNotNull().flatMapLatest { owner ->
        val localSummaryFlow = localStatsRepository.observeSummary(owner.ownerType, owner.ownerId)
        val pendingDeltaFlow = pendingDeltaFlow(owner)
        if (owner.ownerType == LocalStatsOwnerType.ACCOUNT) {
            combine(localSummaryFlow, userProfileLocalStore.observe(), pendingDeltaFlow) { local, user, pending ->
                val server = user?.statsSummary
                val base = if (server != null) {
                    LocalStatsSummary(
                        gamesPlayed = server.matchesPlayed + pending.gamesPlayed,
                        wins = server.wins + pending.wins,
                        losses = server.losses + pending.losses,
                    )
                } else {
                    local
                }
                val merged = if (server != null) {
                    LocalStatsSummary(
                        gamesPlayed = max(base.gamesPlayed, local.gamesPlayed),
                        wins = max(base.wins, local.wins),
                        losses = max(base.losses, local.losses),
                    )
                } else {
                    base
                }
                StatsSummaryUiModel(
                    gamesPlayed = merged.gamesPlayed,
                    wins = merged.wins,
                    losses = merged.losses,
                    winPercentLabel = winPercentLabel(merged),
                    subtitle = when {
                        server == null -> null
                        merged.gamesPlayed > base.gamesPlayed -> "Includes local totals"
                        else -> "Server totals + pending local"
                    },
                )
            }
        } else {
            localSummaryFlow.map { local ->
                StatsSummaryUiModel(
                    gamesPlayed = local.gamesPlayed,
                    wins = local.wins,
                    losses = local.losses,
                    winPercentLabel = winPercentLabel(local),
                    subtitle = null,
                )
            }
        }
    }.asLiveData()

    val headToHeadAccounts = selectedOwner.filterNotNull().flatMapLatest { owner ->
        localStatsRepository.observeHeadToHeadByType(
            ownerType = owner.ownerType,
            ownerId = owner.ownerId,
            opponentType = LocalStatsOpponentType.ACCOUNT,
        )
    }.asLiveData()

    val headToHeadGuests = selectedOwner.filterNotNull().flatMapLatest { owner ->
        localStatsRepository.observeHeadToHeadByType(
            ownerType = owner.ownerType,
            ownerId = owner.ownerId,
            opponentType = LocalStatsOpponentType.GUEST,
        )
    }.asLiveData()

    fun selectOwner(option: StatsOwnerOption?) {
        selectedOwner.update { option }
    }

    fun ensureDefaultSelection() {
        if (selectedOwner.value != null) {
            return
        }
        val options = ownerOptionsFlow.value
        if (options.isNotEmpty()) {
            selectedOwner.value = options.first()
        }
    }

    private fun pendingDeltaFlow(owner: StatsOwnerOption) = gameSessionRepository
        .observeCompletedSessions()
        .map { sessions ->
            if (owner.ownerType != LocalStatsOwnerType.ACCOUNT &&
                owner.ownerType != LocalStatsOwnerType.LOCAL_PROFILE
            ) {
                return@map LocalStatsSummary(0, 0, 0)
            }
            val pendingSessions = sessions.filter { it.session.pendingSync }
            var gamesPlayed = 0
            var wins = 0
            var losses = 0
            pendingSessions.forEach { session ->
                val participants = session.participants
                val computedPlaces = if (participants.any { it.place == null }) {
                    PlacementUtils.computePlaces(participants)
                } else {
                    emptyMap()
                }
                val resolved = participants.map { participant ->
                    participant.copy(place = participant.place ?: computedPlaces[participant.seatIndex])
                }
                val ownerParticipant = resolved.find { participant ->
                    when (owner.ownerType) {
                        LocalStatsOwnerType.ACCOUNT -> participant.participantType == GameParticipantType.ACCOUNT &&
                                participant.userId == owner.ownerId
                        LocalStatsOwnerType.LOCAL_PROFILE -> participant.participantType == GameParticipantType.LOCAL_PROFILE &&
                                participant.profileName == owner.ownerId
                        else -> false
                    }
                } ?: return@forEach
                gamesPlayed += 1
                val isWinner = ownerParticipant.place == 1
                if (isWinner) {
                    wins += 1
                } else {
                    losses += 1
                }
            }
            LocalStatsSummary(gamesPlayed, wins, losses)
        }

    private fun winPercentLabel(summary: LocalStatsSummary): String {
        if (summary.gamesPlayed == 0) {
            return "0%"
        }
        val percent = (summary.wins.toDouble() / summary.gamesPlayed.toDouble()) * 100.0
        return String.format("%.0f%%", percent)
    }
}
