package com.intagri.mtgleader.persistence.stats.local

import androidx.room.Entity

@Entity(
    tableName = "local_stats_summary",
    primaryKeys = ["ownerType", "ownerId"]
)
data class LocalStatsSummaryEntity(
    val ownerType: String,
    val ownerId: String,
    val gamesPlayed: Int,
    val wins: Int,
    val losses: Int,
    val updatedAtEpoch: Long,
)

@Entity(
    tableName = "local_head_to_head",
    primaryKeys = ["ownerType", "ownerId", "opponentType", "opponentId"]
)
data class LocalHeadToHeadEntity(
    val ownerType: String,
    val ownerId: String,
    val opponentType: String,
    val opponentId: String,
    val opponentDisplayName: String,
    val wins: Int,
    val losses: Int,
    val updatedAtEpoch: Long,
)

object LocalStatsOwnerType {
    const val ACCOUNT = "ACCOUNT"
    const val LOCAL_PROFILE = "LOCAL_PROFILE"
}

object LocalStatsOpponentType {
    const val ACCOUNT = "ACCOUNT"
    const val GUEST = "GUEST"
}
