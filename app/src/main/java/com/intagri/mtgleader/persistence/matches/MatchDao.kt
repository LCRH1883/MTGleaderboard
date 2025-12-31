package com.intagri.mtgleader.persistence.matches

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MatchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(match: MatchEntity)

    @Query("UPDATE matches SET serverId = :serverId, status = :status, syncedAtEpoch = :syncedAtEpoch, lastError = :lastError WHERE localId = :localId")
    suspend fun markSynced(
        localId: String,
        serverId: String?,
        status: String,
        syncedAtEpoch: Long,
        lastError: String? = null,
    )

    @Query("UPDATE matches SET status = :status, lastError = :lastError WHERE localId = :localId")
    suspend fun markFailed(
        localId: String,
        status: String,
        lastError: String?,
    )

    @Query("SELECT * FROM matches WHERE localId = :localId LIMIT 1")
    suspend fun getByLocalId(localId: String): MatchEntity?

    @Query("SELECT * FROM matches WHERE clientMatchId = :clientMatchId LIMIT 1")
    suspend fun getByClientMatchId(clientMatchId: String): MatchEntity?

    @Query("SELECT * FROM matches ORDER BY createdAtEpoch DESC")
    fun observeAll(): Flow<List<MatchEntity>>
}
