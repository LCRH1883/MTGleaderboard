package com.intagri.mtgleader.persistence.sync

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

object SyncEntityType {
    const val PROFILE = "PROFILE"
    const val AVATAR = "AVATAR"
    const val FRIEND_REQUEST = "FRIEND_REQUEST"
    const val MATCH = "MATCH"
}

object SyncAction {
    const val UPDATE_DISPLAY_NAME = "UPDATE_DISPLAY_NAME"
    const val UPLOAD_AVATAR = "UPLOAD_AVATAR"
    const val SEND_REQUEST = "SEND_REQUEST"
    const val ACCEPT = "ACCEPT"
    const val DECLINE = "DECLINE"
    const val CANCEL = "CANCEL"
    const val REMOVE = "REMOVE"
    const val CREATE = "CREATE"
}

@JsonClass(generateAdapter = true)
data class DisplayNamePayload(
    @Json(name = "display_name")
    val displayName: String?,
    @Json(name = "updated_at")
    val updatedAt: String,
)

@JsonClass(generateAdapter = true)
data class AvatarUploadPayload(
    @Json(name = "local_uri")
    val localUri: String,
    @Json(name = "updated_at")
    val updatedAt: String,
)

@JsonClass(generateAdapter = true)
data class FriendRequestPayload(
    @Json(name = "request_id")
    val requestId: String? = null,
    @Json(name = "user_id")
    val userId: String? = null,
    val username: String? = null,
    @Json(name = "updated_at")
    val updatedAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class MatchQueuePayload(
    @Json(name = "local_id")
    val localId: String,
    @Json(name = "client_match_id")
    val clientMatchId: String,
    @Json(name = "updated_at")
    val updatedAt: String,
    val match: com.intagri.mtgleader.persistence.matches.MatchPayloadDto,
)
