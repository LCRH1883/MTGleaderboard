package com.intagri.mtgleader.ui.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.intagri.mtgleader.R

class MatchHistoryAdapter(
    private val onClick: (MatchHistoryUiModel) -> Unit,
) : RecyclerView.Adapter<MatchHistoryAdapter.MatchViewHolder>() {

    private val items = mutableListOf<MatchHistoryUiModel>()

    fun submitList(list: List<MatchHistoryUiModel>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MatchViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_match_history, parent, false)
        return MatchViewHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: MatchViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class MatchViewHolder(
        itemView: View,
        private val onClick: (MatchHistoryUiModel) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val title = itemView.findViewById<TextView>(R.id.match_title)
        private val meta = itemView.findViewById<TextView>(R.id.match_meta)
        private val winner = itemView.findViewById<TextView>(R.id.match_winner)
        private val duration = itemView.findViewById<TextView>(R.id.match_duration)
        private val syncStatus = itemView.findViewById<TextView>(R.id.match_sync_status)

        fun bind(model: MatchHistoryUiModel) {
            val context = itemView.context
            val playerLabel = context.resources.getQuantityString(
                R.plurals.player_count,
                model.playersCount,
                model.playersCount,
            )
            title.text = context.getString(
                R.string.match_history_title_format,
                model.title,
                playerLabel,
            )
            meta.text = model.subtitle
            val winnerLabel = if (model.winnerNames.isEmpty()) {
                context.getString(R.string.winner_unknown)
            } else {
                model.winnerNames.joinToString(", ")
            }
            winner.text = context.getString(R.string.winner_label, winnerLabel)
            duration.text = context.getString(R.string.duration_label, model.durationLabel)
            syncStatus.text = if (model.pendingSync) {
                context.getString(R.string.sync_pending)
            } else {
                context.getString(R.string.sync_synced)
            }
            itemView.setOnClickListener { onClick(model) }
        }
    }
}
