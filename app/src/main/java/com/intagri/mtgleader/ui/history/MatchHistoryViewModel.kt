package com.intagri.mtgleader.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.intagri.mtgleader.persistence.gamesession.GameSessionRepository
import com.intagri.mtgleader.util.TimeFormatUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.map

@HiltViewModel
class MatchHistoryViewModel @Inject constructor(
    gameSessionRepository: GameSessionRepository,
) : ViewModel() {

    val matches = gameSessionRepository.observeCompletedSessions()
        .map { sessions ->
            sessions.map { session ->
                val participants = session.participants
                val winnerNames = participants.filter { it.place == 1 }
                    .map { it.displayName }
                val endedAtEpoch = session.session.endedAtEpoch ?: session.session.updatedAtEpoch
                val title = session.session.tabletopType
                val subtitle = TimeFormatUtils.formatEpochDate(endedAtEpoch)
                val duration = TimeFormatUtils.formatDurationSeconds(session.session.gameElapsedSeconds)
                MatchHistoryUiModel(
                    localMatchId = session.session.localMatchId,
                    title = title,
                    subtitle = subtitle,
                    playersCount = participants.size,
                    winnerNames = winnerNames,
                    durationLabel = duration,
                    pendingSync = session.session.pendingSync,
                )
            }
        }
        .asLiveData()
}
