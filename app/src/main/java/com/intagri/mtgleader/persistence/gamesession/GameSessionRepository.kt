package com.intagri.mtgleader.persistence.gamesession

import com.intagri.mtgleader.model.counter.CounterModel
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class GameSessionRepository @Inject constructor(
    private val gameSessionDao: GameSessionDao,
    moshi: Moshi,
) {
    private val counterListAdapter: JsonAdapter<List<GameCounterSnapshot>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, GameCounterSnapshot::class.java)
    )

    suspend fun saveSnapshot(
        session: GameSessionEntity,
        participants: List<GameParticipantEntity>
    ) {
        gameSessionDao.saveSnapshot(session, participants)
    }

    suspend fun getSession(localMatchId: String): GameSessionWithParticipants? {
        return gameSessionDao.getSessionWithParticipants(localMatchId)
    }

    suspend fun getLatestInProgress(): GameSessionWithParticipants? {
        return gameSessionDao.getLatestByStatus(GameSessionStatus.IN_PROGRESS)
    }

    fun observeCompletedSessions(): Flow<List<GameSessionWithParticipants>> {
        return gameSessionDao.observeByStatus(GameSessionStatus.COMPLETED)
    }

    suspend fun updateSyncState(
        localMatchId: String,
        pendingSync: Boolean,
        backendMatchId: String?,
    ) {
        gameSessionDao.updateSyncState(
            localMatchId = localMatchId,
            pendingSync = pendingSync,
            backendMatchId = backendMatchId,
            updatedAtEpoch = System.currentTimeMillis(),
        )
    }

    fun encodeCounters(counters: List<CounterModel>): String {
        val snapshots = counters.map { counter ->
            GameCounterSnapshot(counter.template.id, counter.amount)
        }
        return counterListAdapter.toJson(snapshots)
    }

    fun decodeCounters(json: String?): List<GameCounterSnapshot> {
        if (json.isNullOrBlank()) {
            return emptyList()
        }
        return runCatching {
            counterListAdapter.fromJson(json).orEmpty()
        }.getOrDefault(emptyList())
    }
}
