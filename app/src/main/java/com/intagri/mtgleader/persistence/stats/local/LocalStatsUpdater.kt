package com.intagri.mtgleader.persistence.stats.local

import com.intagri.mtgleader.persistence.gamesession.GameParticipantEntity
import com.intagri.mtgleader.persistence.gamesession.GameParticipantType
import javax.inject.Inject

class LocalStatsUpdater @Inject constructor(
    private val localStatsRepository: LocalStatsRepository,
) {
    suspend fun recordCompletedMatch(participants: List<GameParticipantEntity>) {
        if (participants.isEmpty()) {
            return
        }
        val winners = participants.filter { it.place == 1 }
        val updatedAtEpoch = System.currentTimeMillis()
        participants.forEach { participant ->
            val ownerKey = ownerKeyFor(participant) ?: return@forEach
            val isWinner = winners.any { it.seatIndex == participant.seatIndex }
            val winsDelta = if (isWinner) 1 else 0
            val lossesDelta = if (!isWinner && winners.isNotEmpty()) 1 else 0
            localStatsRepository.updateSummary(
                ownerType = ownerKey.ownerType,
                ownerId = ownerKey.ownerId,
                gamesPlayedDelta = 1,
                winsDelta = winsDelta,
                lossesDelta = lossesDelta,
                updatedAtEpoch = updatedAtEpoch,
            )
            winners.forEach { winner ->
                if (winner.seatIndex == participant.seatIndex) {
                    return@forEach
                }
                val opponentKey = opponentKeyFor(winner) ?: return@forEach
                localStatsRepository.updateHeadToHead(
                    ownerType = ownerKey.ownerType,
                    ownerId = ownerKey.ownerId,
                    opponentType = opponentKey.opponentType,
                    opponentId = opponentKey.opponentId,
                    opponentDisplayName = opponentKey.opponentDisplayName,
                    winsDelta = 0,
                    lossesDelta = 1,
                    updatedAtEpoch = updatedAtEpoch,
                )
            }
            if (isWinner) {
                participants.forEach { opponent ->
                    if (opponent.seatIndex == participant.seatIndex) {
                        return@forEach
                    }
                    if (winners.any { it.seatIndex == opponent.seatIndex }) {
                        return@forEach
                    }
                    val opponentKey = opponentKeyFor(opponent) ?: return@forEach
                    localStatsRepository.updateHeadToHead(
                        ownerType = ownerKey.ownerType,
                        ownerId = ownerKey.ownerId,
                        opponentType = opponentKey.opponentType,
                        opponentId = opponentKey.opponentId,
                        opponentDisplayName = opponentKey.opponentDisplayName,
                        winsDelta = 1,
                        lossesDelta = 0,
                        updatedAtEpoch = updatedAtEpoch,
                    )
                }
            }
        }
    }

    private fun ownerKeyFor(participant: GameParticipantEntity): OwnerKey? {
        return when (participant.participantType) {
            GameParticipantType.ACCOUNT -> {
                val userId = participant.userId ?: return null
                OwnerKey(LocalStatsOwnerType.ACCOUNT, userId)
            }
            GameParticipantType.LOCAL_PROFILE -> {
                val profileName = participant.profileName ?: return null
                OwnerKey(LocalStatsOwnerType.LOCAL_PROFILE, profileName)
            }
            else -> null
        }
    }

    private fun opponentKeyFor(participant: GameParticipantEntity): OpponentKey? {
        return when (participant.participantType) {
            GameParticipantType.ACCOUNT -> {
                val userId = participant.userId ?: return null
                OpponentKey(
                    opponentType = LocalStatsOpponentType.ACCOUNT,
                    opponentId = userId,
                    opponentDisplayName = participant.displayName.ifBlank { userId },
                )
            }
            GameParticipantType.LOCAL_PROFILE,
            GameParticipantType.GUEST -> {
                val guestId = participant.guestName
                    ?: participant.profileName
                    ?: participant.displayName
                OpponentKey(
                    opponentType = LocalStatsOpponentType.GUEST,
                    opponentId = guestId,
                    opponentDisplayName = participant.displayName.ifBlank { guestId },
                )
            }
            else -> null
        }
    }

    private data class OwnerKey(
        val ownerType: String,
        val ownerId: String,
    )

    private data class OpponentKey(
        val opponentType: String,
        val opponentId: String,
        val opponentDisplayName: String,
    )
}
