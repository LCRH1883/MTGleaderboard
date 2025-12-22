package com.intagri.mtgleader.ui.setup.tabletop

import android.view.LayoutInflater
import android.view.View
import com.github.rongi.rotate_layout.layout.RotateLayout
import com.intagri.mtgleader.R
import com.intagri.mtgleader.model.player.PlayerSetupModel
import com.intagri.mtgleader.view.TabletopLayout
import com.intagri.mtgleader.view.TabletopLayoutAdapter
import com.intagri.mtgleader.view.TabletopLayoutViewHolder

class SetupTabletopLayoutAdapter(
    parent: TabletopLayout,
    private val onSetupPlayerSelectedListener: OnSetupPlayerSelectedListener
) :
    TabletopLayoutAdapter<SetupTabletopLayoutViewHolder, PlayerSetupModel>(parent) {
    override fun createViewHolder(container: RotateLayout): SetupTabletopLayoutViewHolder {
        return SetupTabletopLayoutViewHolder(container, onSetupPlayerSelectedListener)
    }
}

class SetupTabletopLayoutViewHolder(
    container: RotateLayout,
    onSetupPlayerSelectedListener: OnSetupPlayerSelectedListener
) : TabletopLayoutViewHolder<PlayerSetupModel>(container) {

    override val view: View =
        LayoutInflater.from(container.context).inflate(R.layout.item_setup_player, container, false)

    private val nestedVh = SetupPlayerViewHolder(view, onSetupPlayerSelectedListener)

    override fun bind(data: PlayerSetupModel) {
        nestedVh.bind(data)
    }
}