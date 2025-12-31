package com.intagri.mtgleader.persistence.userprofile

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey
    val id: String,
    val email: String,
    val username: String,
    val displayName: String? = null,
    val avatarPath: String? = null,
    val avatarUpdatedAt: String? = null,
    val updatedAt: String,
    val statsSummaryJson: String? = null,
)
