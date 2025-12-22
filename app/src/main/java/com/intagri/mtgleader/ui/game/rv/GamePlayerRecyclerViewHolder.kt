package com.intagri.mtgleader.ui.game.rv

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.intagri.mtgleader.ui.game.OnPlayerUpdatedListener
import com.intagri.mtgleader.ui.game.GamePlayerUiModel
import com.intagri.mtgleader.view.counter.edit.PlayerMenuListener
import com.intagri.mtgleader.view.player.PlayerViewHolder

class GamePlayerRecyclerViewHolder(
    itemView: View,
    onPlayerUpdatedListener: OnPlayerUpdatedListener,
    playerMenuListener: PlayerMenuListener,
) : RecyclerView.ViewHolder(itemView) {

    private val wrappedTableTopVH =
        PlayerViewHolder(itemView, onPlayerUpdatedListener, playerMenuListener)

    fun bind(data: GamePlayerUiModel) {
        wrappedTableTopVH.bind(data)
    }
}