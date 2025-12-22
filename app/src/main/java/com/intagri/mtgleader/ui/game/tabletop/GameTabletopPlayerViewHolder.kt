package com.intagri.mtgleader.ui.game.tabletop

import android.view.LayoutInflater
import android.view.View
import com.github.rongi.rotate_layout.layout.RotateLayout
import com.intagri.mtgleader.R
import com.intagri.mtgleader.ui.game.OnPlayerUpdatedListener
import com.intagri.mtgleader.ui.game.GamePlayerUiModel
import com.intagri.mtgleader.view.TabletopLayoutViewHolder
import com.intagri.mtgleader.view.counter.edit.PlayerMenuListener
import com.intagri.mtgleader.view.player.PlayerViewHolder

class GameTabletopPlayerViewHolder(
    container: RotateLayout,
    onPlayerUpdatedListener: OnPlayerUpdatedListener,
    playerMenuListener: PlayerMenuListener,
) : TabletopLayoutViewHolder<GamePlayerUiModel>(container) {

    private val nestedPlayerVH = PlayerViewHolder(
        LayoutInflater.from(container.context)
            .inflate(R.layout.item_player_tabletop, container, false),
        onPlayerUpdatedListener,
        playerMenuListener,
    )

    override fun bind(data: GamePlayerUiModel) {
        nestedPlayerVH.bind(data)
    }

    override val view: View
        get() = nestedPlayerVH.itemView
}