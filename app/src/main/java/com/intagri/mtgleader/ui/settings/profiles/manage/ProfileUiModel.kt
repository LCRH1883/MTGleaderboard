package com.intagri.mtgleader.ui.settings.profiles.manage

import com.intagri.mtgleader.model.player.PlayerProfileModel

data class ProfileUiModel(
    val name: String,
    val deletable: Boolean = true,
) {
    constructor(playerProfileModel: PlayerProfileModel): this(
        name = playerProfileModel.name,
        deletable = playerProfileModel.deletable,
    )
}