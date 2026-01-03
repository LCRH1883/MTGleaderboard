package com.intagri.mtgleader.persistence.gamesession

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface GameSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: GameSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParticipants(participants: List<GameParticipantEntity>)

    @Query("DELETE FROM game_participants WHERE localMatchId = :localMatchId")
    suspend fun deleteParticipants(localMatchId: String)

    @Transaction
    suspend fun saveSnapshot(
        session: GameSessionEntity,
        participants: List<GameParticipantEntity>
    ) {
        insertSession(session)
        deleteParticipants(session.localMatchId)
        insertParticipants(participants)
    }

    @Transaction
    @Query("SELECT * FROM game_sessions WHERE localMatchId = :localMatchId LIMIT 1")
    suspend fun getSessionWithParticipants(localMatchId: String): GameSessionWithParticipants?

    @Transaction
    @Query(
        """
        SELECT * FROM game_sessions
        WHERE status = :status
        ORDER BY updatedAtEpoch DESC
        LIMIT 1
        """
    )
    suspend fun getLatestByStatus(status: String): GameSessionWithParticipants?

    @Transaction
    @Query(
        """
        SELECT * FROM game_sessions
        WHERE status = :status
        ORDER BY endedAtEpoch DESC, updatedAtEpoch DESC
        """
    )
    fun observeByStatus(status: String): Flow<List<GameSessionWithParticipants>>

    @Query(
        """
        UPDATE game_sessions
        SET pendingSync = :pendingSync,
            backendMatchId = :backendMatchId,
            updatedAtEpoch = :updatedAtEpoch
        WHERE localMatchId = :localMatchId
        """
    )
    suspend fun updateSyncState(
        localMatchId: String,
        pendingSync: Boolean,
        backendMatchId: String?,
        updatedAtEpoch: Long,
    )

    @Query("UPDATE game_sessions SET status = :status WHERE localMatchId = :localMatchId")
    suspend fun updateStatus(localMatchId: String, status: String)
}
