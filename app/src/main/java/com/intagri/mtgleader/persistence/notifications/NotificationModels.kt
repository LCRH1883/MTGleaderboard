package com.intagri.mtgleader.persistence.notifications

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NotificationTokenRequest(
    val token: String,
    val platform: String,
)

@JsonClass(generateAdapter = true)
data class NotificationTokenResponse(
    val token: String,
    val platform: String,
    @Json(name = "created_at")
    val createdAt: String? = null,
    @Json(name = "updated_at")
    val updatedAt: String? = null,
)
