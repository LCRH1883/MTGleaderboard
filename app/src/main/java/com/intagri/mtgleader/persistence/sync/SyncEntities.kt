package com.intagri.mtgleader.persistence.sync

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val entityType: String,
    val action: String,
    val payloadJson: String,
    val createdAt: Long,
    val attemptCount: Int = 0,
    val lastError: String? = null,
)

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey
    val key: String,
    val value: String,
    val updatedAtEpoch: Long,
)
