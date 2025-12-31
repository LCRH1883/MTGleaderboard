package com.intagri.mtgleader.persistence.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncQueueDao {
    @Insert
    suspend fun enqueue(item: SyncQueueEntity)

    @Query("SELECT * FROM sync_queue ORDER BY createdAt ASC LIMIT 1")
    suspend fun peekOldest(): SyncQueueEntity?

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE sync_queue SET attemptCount = attemptCount + 1, lastError = :errorMessage WHERE id = :id")
    suspend fun incrementAttempt(id: Long, errorMessage: String?)

    @Query("SELECT COUNT(*) FROM sync_queue")
    suspend fun count(): Int
}

@Dao
interface SyncMetadataDao {
    @Query("SELECT value FROM sync_metadata WHERE key = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: SyncMetadataEntity)
}
