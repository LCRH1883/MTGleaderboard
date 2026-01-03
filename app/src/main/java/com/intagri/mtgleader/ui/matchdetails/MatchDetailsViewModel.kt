package com.intagri.mtgleader.ui.matchdetails

import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import com.intagri.mtgleader.persistence.gamesession.GameSessionRepository
import com.intagri.mtgleader.persistence.gamesession.PlacementUtils
import com.intagri.mtgleader.util.TimeFormatUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MatchDetailsViewModel @Inject constructor(
    private val gameSessionRepository: GameSessionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val localMatchId: String =
        savedStateHandle.get<String>(MatchDetailsActivity.EXTRA_LOCAL_MATCH_ID)
            ?: ""

    val matchDetails: LiveData<MatchDetailsUiModel?> = liveData {
        if (localMatchId.isBlank()) {
            emit(null)
            return@liveData
        }
        val session = gameSessionRepository.getSession(localMatchId)
        if (session == null) {
            emit(null)
            return@liveData
        }
        val participants = session.participants
        val computedPlaces = if (participants.any { it.place == null }) {
            PlacementUtils.computePlaces(participants)
        } else {
            emptyMap()
        }
        val resolvedParticipants = participants.map { participant ->
            val place = participant.place ?: computedPlaces[participant.seatIndex]
            participant.copy(place = place)
        }
        val winnerNames = resolvedParticipants.filter { it.place == 1 }
            .map { it.displayName }
        val winnerLabel = if (winnerNames.isEmpty()) {
            "Winner unknown"
        } else {
            winnerNames.joinToString(", ")
        }
        val placeCounts = resolvedParticipants.groupingBy { it.place }.eachCount()
        val placements = resolvedParticipants.sortedWith(
            compareBy({ it.place ?: Int.MAX_VALUE }, { it.seatIndex })
        ).map { participant ->
            val placeValue = participant.place ?: 0
            val isTie = placeCounts[participant.place] ?: 0 > 1
            val placeLabel = if (placeValue <= 0) {
                "-"
            } else {
                val ordinal = ordinal(placeValue)
                if (isTie) "T$ordinal" else ordinal
            }
            val eliminationLabel = if (participant.place == 1) {
                "Winner"
            } else {
                val turn = participant.eliminatedTurnNumber
                val duringSeat = participant.eliminatedDuringSeatIndex
                val duringName = resolvedParticipants.find { it.seatIndex == duringSeat }?.displayName
                if (turn == null) {
                    "Eliminated"
                } else if (duringName.isNullOrBlank()) {
                    "Eliminated turn $turn"
                } else {
                    "Eliminated turn $turn during $duringName's turn"
                }
            }
            val timeLabel = TimeFormatUtils.formatDurationSeconds(
                (participant.totalTurnTimeMs ?: 0L) / 1000L
            )
            val turnStats = "Turns ${participant.turnsTaken ?: 0} · $timeLabel"
            MatchPlacementUiModel(
                placeLabel = placeLabel,
                displayName = participant.displayName,
                eliminationLabel = eliminationLabel,
                turnStatsLabel = turnStats,
            )
        }
        emit(
            MatchDetailsUiModel(
                tabletopType = session.session.tabletopType,
                playersCount = resolvedParticipants.size,
                durationLabel = TimeFormatUtils.formatDurationSeconds(session.session.gameElapsedSeconds),
                pendingSync = session.session.pendingSync,
                winnerLabel = winnerLabel,
                placements = placements,
            )
        )
    }

    private fun ordinal(value: Int): String {
        val suffix = when {
            value % 100 in 11..13 -> "th"
            value % 10 == 1 -> "st"
            value % 10 == 2 -> "nd"
            value % 10 == 3 -> "rd"
            else -> "th"
        }
        return "$value$suffix"
    }
}
