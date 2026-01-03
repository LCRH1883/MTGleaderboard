package com.intagri.mtgleader.persistence.stats.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class LocalStatsRepository @Inject constructor(
    private val localStatsDao: LocalStatsDao,
) {
    suspend fun getSummary(ownerType: String, ownerId: String): LocalStatsSummary {
        val entity = localStatsDao.getSummary(ownerType, ownerId)
        return entity?.toModel() ?: LocalStatsSummary(0, 0, 0)
    }

    fun observeSummary(ownerType: String, ownerId: String): Flow<LocalStatsSummary> {
        return localStatsDao.observeSummary(ownerType, ownerId)
            .map { it?.toModel() ?: LocalStatsSummary(0, 0, 0) }
    }

    fun observeHeadToHead(ownerType: String, ownerId: String): Flow<List<LocalHeadToHead>> {
        return localStatsDao.observeHeadToHead(ownerType, ownerId).map { list ->
            list.map { it.toModel() }
        }
    }

    fun observeHeadToHeadByType(
        ownerType: String,
        ownerId: String,
        opponentType: String,
    ): Flow<List<LocalHeadToHead>> {
        return localStatsDao.observeHeadToHeadByType(ownerType, ownerId, opponentType).map { list ->
            list.map { it.toModel() }
        }
    }

    suspend fun updateSummary(
        ownerType: String,
        ownerId: String,
        gamesPlayedDelta: Int,
        winsDelta: Int,
        lossesDelta: Int,
        updatedAtEpoch: Long,
    ) {
        val current = localStatsDao.getSummary(ownerType, ownerId)
        val summary = if (current == null) {
            LocalStatsSummaryEntity(
                ownerType = ownerType,
                ownerId = ownerId,
                gamesPlayed = gamesPlayedDelta.coerceAtLeast(0),
                wins = winsDelta.coerceAtLeast(0),
                losses = lossesDelta.coerceAtLeast(0),
                updatedAtEpoch = updatedAtEpoch,
            )
        } else {
            current.copy(
                gamesPlayed = (current.gamesPlayed + gamesPlayedDelta).coerceAtLeast(0),
                wins = (current.wins + winsDelta).coerceAtLeast(0),
                losses = (current.losses + lossesDelta).coerceAtLeast(0),
                updatedAtEpoch = updatedAtEpoch,
            )
        }
        localStatsDao.upsertSummary(summary)
    }

    suspend fun updateHeadToHead(
        ownerType: String,
        ownerId: String,
        opponentType: String,
        opponentId: String,
        opponentDisplayName: String,
        winsDelta: Int,
        lossesDelta: Int,
        updatedAtEpoch: Long,
    ) {
        val current = localStatsDao.getHeadToHead(ownerType, ownerId, opponentType, opponentId)
        val updated = if (current == null) {
            LocalHeadToHeadEntity(
                ownerType = ownerType,
                ownerId = ownerId,
                opponentType = opponentType,
                opponentId = opponentId,
                opponentDisplayName = opponentDisplayName,
                wins = winsDelta.coerceAtLeast(0),
                losses = lossesDelta.coerceAtLeast(0),
                updatedAtEpoch = updatedAtEpoch,
            )
        } else {
            current.copy(
                opponentDisplayName = opponentDisplayName,
                wins = (current.wins + winsDelta).coerceAtLeast(0),
                losses = (current.losses + lossesDelta).coerceAtLeast(0),
                updatedAtEpoch = updatedAtEpoch,
            )
        }
        localStatsDao.upsertHeadToHead(updated)
    }

    private fun LocalStatsSummaryEntity.toModel(): LocalStatsSummary {
        return LocalStatsSummary(
            gamesPlayed = gamesPlayed,
            wins = wins,
            losses = losses,
        )
    }

    private fun LocalHeadToHeadEntity.toModel(): LocalHeadToHead {
        return LocalHeadToHead(
            opponentType = opponentType,
            opponentId = opponentId,
            opponentDisplayName = opponentDisplayName,
            wins = wins,
            losses = losses,
        )
    }
}
