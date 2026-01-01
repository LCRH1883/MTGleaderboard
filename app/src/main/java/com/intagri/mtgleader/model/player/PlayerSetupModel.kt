package com.intagri.mtgleader.model.player

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PlayerSetupModel (
    val id: Int = 0,
    val profile: PlayerProfileModel? = null,
    val color: PlayerColor = PlayerColor.NONE,
    val assignedUserId: String? = null,
    val assignedUsername: String? = null,
    val assignedDisplayName: String? = null,
    val assignedAvatarUrl: String? = null,
    val tempName: String? = null,
) : Parcelable
