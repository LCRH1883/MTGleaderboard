package com.intagri.mtgleader.persistence.friends

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UserSummaryDto(
    val id: String,
    val username: String? = null,
    @Json(name = "display_name")
    val displayName: String? = null,
    @Json(name = "avatar_url")
    val avatarUrl: String? = null,
)

@JsonClass(generateAdapter = true)
data class FriendRequestDto(
    val id: String,
    val user: UserSummaryDto,
    @Json(name = "created_at")
    val createdAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class FriendsOverviewDto(
    val friends: List<UserSummaryDto> = emptyList(),
    @Json(name = "incoming_requests")
    val incomingRequests: List<FriendRequestDto> = emptyList(),
    @Json(name = "outgoing_requests")
    val outgoingRequests: List<FriendRequestDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class FriendRequestCreate(
    val username: String,
)
