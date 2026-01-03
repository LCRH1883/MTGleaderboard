package com.intagri.mtgleader.persistence.stats.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalStatsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSummary(entity: LocalStatsSummaryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHeadToHead(entity: LocalHeadToHeadEntity)

    @Query(
        """
        SELECT * FROM local_stats_summary
        WHERE ownerType = :ownerType AND ownerId = :ownerId
        LIMIT 1
        """
    )
    suspend fun getSummary(ownerType: String, ownerId: String): LocalStatsSummaryEntity?

    @Query(
        """
        SELECT * FROM local_stats_summary
        WHERE ownerType = :ownerType AND ownerId = :ownerId
        LIMIT 1
        """
    )
    fun observeSummary(ownerType: String, ownerId: String): Flow<LocalStatsSummaryEntity?>

    @Query(
        """
        SELECT * FROM local_head_to_head
        WHERE ownerType = :ownerType AND ownerId = :ownerId
        ORDER BY (wins + losses) DESC, opponentDisplayName ASC
        """
    )
    fun observeHeadToHead(ownerType: String, ownerId: String): Flow<List<LocalHeadToHeadEntity>>

    @Query(
        """
        SELECT * FROM local_head_to_head
        WHERE ownerType = :ownerType AND ownerId = :ownerId AND opponentType = :opponentType
        ORDER BY (wins + losses) DESC, opponentDisplayName ASC
        """
    )
    fun observeHeadToHeadByType(
        ownerType: String,
        ownerId: String,
        opponentType: String,
    ): Flow<List<LocalHeadToHeadEntity>>

    @Query(
        """
        SELECT * FROM local_head_to_head
        WHERE ownerType = :ownerType AND ownerId = :ownerId
          AND opponentType = :opponentType
          AND opponentId = :opponentId
        LIMIT 1
        """
    )
    suspend fun getHeadToHead(
        ownerType: String,
        ownerId: String,
        opponentType: String,
        opponentId: String,
    ): LocalHeadToHeadEntity?
}
