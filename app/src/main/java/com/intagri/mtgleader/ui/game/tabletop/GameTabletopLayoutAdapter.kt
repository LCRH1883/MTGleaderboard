package com.intagri.mtgleader.ui.game.tabletop

import com.github.rongi.rotate_layout.layout.RotateLayout
import com.intagri.mtgleader.ui.game.OnPlayerUpdatedListener
import com.intagri.mtgleader.ui.game.GamePlayerUiModel
import com.intagri.mtgleader.view.TabletopLayout
import com.intagri.mtgleader.view.TabletopLayoutAdapter
import com.intagri.mtgleader.view.counter.edit.PlayerMenuListener

class GameTabletopLayoutAdapter(
    parent: TabletopLayout,
    private val onPlayerUpdatedListener: OnPlayerUpdatedListener,
    private val playerMenuListener: PlayerMenuListener,
) :
    TabletopLayoutAdapter<GameTabletopPlayerViewHolder, GamePlayerUiModel>(parent) {

    override fun createViewHolder(container: RotateLayout): GameTabletopPlayerViewHolder {
        return GameTabletopPlayerViewHolder(
            container,
            onPlayerUpdatedListener,
            playerMenuListener,
        )
    }
}