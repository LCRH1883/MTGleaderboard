package com.intagri.mtgleader.persistence.friends

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "friends")
data class FriendEntity(
    @PrimaryKey
    val userId: String,
    val username: String? = null,
    val displayName: String? = null,
    val avatarPath: String? = null,
    val avatarUpdatedAt: String? = null,
    val updatedAt: String? = null,
    val lastSeenAt: String? = null,
)

@Entity(tableName = "friend_requests")
data class FriendRequestEntity(
    @PrimaryKey
    val requestId: String,
    val userId: String,
    val username: String? = null,
    val displayName: String? = null,
    val avatarPath: String? = null,
    val avatarUpdatedAt: String? = null,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
    val resolvedAt: String? = null,
    val isPendingSync: Boolean = false,
)
