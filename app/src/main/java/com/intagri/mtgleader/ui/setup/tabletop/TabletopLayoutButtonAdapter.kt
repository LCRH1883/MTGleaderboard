package com.intagri.mtgleader.view.layoutbutton

import android.view.LayoutInflater
import android.view.View
import com.github.rongi.rotate_layout.layout.RotateLayout
import com.intagri.mtgleader.R
import com.intagri.mtgleader.view.TabletopLayout
import com.intagri.mtgleader.view.TabletopLayoutAdapter
import com.intagri.mtgleader.view.TabletopLayoutViewHolder

class TabletopLayoutButtonAdapter(parent: TabletopLayout) :
    TabletopLayoutAdapter<TabletopLayoutButtonPlayerIconViewHolder, Unit>(parent) {
    override fun createViewHolder(container: RotateLayout): TabletopLayoutButtonPlayerIconViewHolder {
        return TabletopLayoutButtonPlayerIconViewHolder(container)
    }
}

class TabletopLayoutButtonPlayerIconViewHolder(container: RotateLayout) :
    TabletopLayoutViewHolder<Unit>(container) {

    override val view: View =
        LayoutInflater.from(container.context)
            .inflate(R.layout.item_layout_button_player, container, false)

    /**
     * No data to bind
     */
    override fun bind(data: Unit) {}
}