package com.intagri.mtgleader.persistence.friends

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FriendDao {
    @Query("SELECT * FROM friends")
    fun getAllAccepted(): Flow<List<FriendEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<FriendEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: FriendEntity)

    @Query("DELETE FROM friends")
    suspend fun clear()

    @Transaction
    suspend fun replaceAllAccepted(items: List<FriendEntity>) {
        clear()
        if (items.isNotEmpty()) {
            insertAll(items)
        }
    }
}

@Dao
interface FriendRequestDao {
    @Query("SELECT * FROM friend_requests WHERE status = 'incoming'")
    fun getIncoming(): Flow<List<FriendRequestEntity>>

    @Query("SELECT * FROM friend_requests WHERE status = 'outgoing'")
    fun getOutgoing(): Flow<List<FriendRequestEntity>>

    @Query("SELECT * FROM friend_requests WHERE isPendingSync = 1")
    suspend fun getPendingSync(): List<FriendRequestEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<FriendRequestEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: FriendRequestEntity)

    @Query("DELETE FROM friend_requests")
    suspend fun clear()

    @Query("DELETE FROM friend_requests WHERE requestId = :requestId")
    suspend fun deleteById(requestId: String)

    @Query("SELECT * FROM friend_requests WHERE requestId = :requestId LIMIT 1")
    suspend fun getById(requestId: String): FriendRequestEntity?

    @Transaction
    suspend fun replaceAllRequests(
        incoming: List<FriendRequestEntity>,
        outgoing: List<FriendRequestEntity>,
    ) {
        clear()
        if (incoming.isNotEmpty() || outgoing.isNotEmpty()) {
            insertAll(incoming + outgoing)
        }
    }
}
