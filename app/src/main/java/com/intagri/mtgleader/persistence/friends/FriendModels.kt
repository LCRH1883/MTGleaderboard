package com.intagri.mtgleader.persistence.friends

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class FriendConnection(
    val id: String? = null,
    val username: String? = null,
    val email: String? = null,
    val status: String? = null,
    @Json(name = "request_id")
    val requestId: String? = null,
    val user: FriendUser? = null,
)

@JsonClass(generateAdapter = true)
data class FriendUser(
    val id: String? = null,
    val username: String? = null,
    val email: String? = null,
)

@JsonClass(generateAdapter = true)
data class FriendRequestCreate(
    val username: String,
)
