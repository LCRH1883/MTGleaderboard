package com.intagri.mtgleader.ui.stats

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.intagri.mtgleader.R
import com.intagri.mtgleader.persistence.stats.local.LocalHeadToHead

class HeadToHeadAdapter : RecyclerView.Adapter<HeadToHeadAdapter.HeadToHeadViewHolder>() {

    private val items = mutableListOf<LocalHeadToHead>()

    fun submitList(list: List<LocalHeadToHead>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeadToHeadViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_head_to_head, parent, false)
        return HeadToHeadViewHolder(view)
    }

    override fun onBindViewHolder(holder: HeadToHeadViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class HeadToHeadViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name = itemView.findViewById<TextView>(R.id.head_to_head_name)
        private val record = itemView.findViewById<TextView>(R.id.head_to_head_record)

        fun bind(item: LocalHeadToHead) {
            name.text = item.opponentDisplayName
            record.text = "${item.wins}-${item.losses}"
        }
    }
}
