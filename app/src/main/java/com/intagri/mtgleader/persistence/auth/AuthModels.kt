package com.intagri.mtgleader.persistence.auth

import com.intagri.mtgleader.persistence.stats.StatsSummaryDto
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AuthUser(
    val id: String,
    val email: String,
    val username: String,
    @Json(name = "display_name")
    val displayName: String? = null,
    val avatar: String? = null,
    @Json(name = "avatar_url")
    val avatarUrl: String? = null,
    @Json(name = "created_at")
    val createdAt: String,
    @Json(name = "updated_at")
    val updatedAt: String? = null,
    @Json(name = "stats_summary")
    val statsSummary: StatsSummaryDto? = null,
)

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    val email: String,
    val username: String,
    val password: String,
)

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val email: String,
    val password: String,
)

@JsonClass(generateAdapter = true)
data class IdTokenRequest(
    @Json(name = "id_token")
    val idToken: String,
)

@JsonClass(generateAdapter = true)
data class UserProfileUpdateRequest(
    @Json(name = "display_name")
    val displayName: String?,
    @Json(name = "updated_at")
    val updatedAt: String,
)
