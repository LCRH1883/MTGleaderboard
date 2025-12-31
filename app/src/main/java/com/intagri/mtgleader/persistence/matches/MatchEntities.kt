package com.intagri.mtgleader.persistence.matches

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "matches")
data class MatchEntity(
    @PrimaryKey
    val localId: String,
    val serverId: String? = null,
    val clientMatchId: String,
    val createdAtEpoch: Long,
    val updatedAt: String,
    val status: String,
    val payloadJson: String,
    val lastError: String? = null,
    val syncedAtEpoch: Long? = null,
)

object MatchStatus {
    const val PENDING_UPLOAD = "PENDING_UPLOAD"
    const val SYNCED = "SYNCED"
    const val FAILED = "FAILED"
}
