package com.intagri.mtgleader.ui.settings.friends

enum class FriendStatus {
    ACCEPTED,
    INCOMING,
    OUTGOING,
    UNKNOWN,
}

data class FriendUiModel(
    val id: String?,
    val displayName: String,
    val status: FriendStatus,
)
