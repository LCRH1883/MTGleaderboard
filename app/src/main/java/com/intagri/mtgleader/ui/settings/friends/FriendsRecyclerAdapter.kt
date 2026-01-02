package com.intagri.mtgleader.ui.settings.friends

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.intagri.mtgleader.R

class FriendsRecyclerAdapter(
    private val actionListener: FriendActionListener
) : RecyclerView.Adapter<FriendsRecyclerAdapter.FriendViewHolder>() {

    private val friends = mutableListOf<FriendUiModel>()

    fun setFriends(updated: List<FriendUiModel>) {
        friends.clear()
        friends.addAll(updated)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_friend, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        holder.bind(friends[position], actionListener)
    }

    override fun getItemCount(): Int = friends.size

    class FriendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.friend_name)
        private val usernameText: TextView = itemView.findViewById(R.id.friend_username)
        private val statusText: TextView = itemView.findViewById(R.id.friend_status)
        private val actionsContainer: View = itemView.findViewById(R.id.friend_actions)
        private val primaryAction: TextView = itemView.findViewById(R.id.primary_action)
        private val secondaryAction: TextView = itemView.findViewById(R.id.secondary_action)
        private val avatarImage: ImageView = itemView.findViewById(R.id.friend_avatar)

        fun bind(item: FriendUiModel, listener: FriendActionListener) {
            nameText.text = item.displayName
            if (!item.username.isNullOrBlank()) {
                usernameText.text = "@${item.username}"
                usernameText.visibility = View.VISIBLE
            } else {
                usernameText.visibility = View.GONE
            }
            itemView.setOnClickListener { listener.onProfileClicked(item) }
            val avatarUrl = item.avatarUrl
            if (avatarUrl.isNullOrBlank()) {
                avatarImage.setImageResource(R.drawable.ic_skull)
            } else {
                Glide.with(itemView)
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_skull)
                    .into(avatarImage)
            }
            val context = itemView.context
            val (statusRes, primaryRes, secondaryRes) = when (item.status) {
                FriendStatus.INCOMING -> Triple(
                    R.string.friend_status_incoming,
                    R.string.friend_action_accept,
                    R.string.friend_action_decline
                )
                FriendStatus.OUTGOING -> Triple(
                    R.string.friend_status_outgoing,
                    R.string.friend_action_cancel,
                    null
                )
                FriendStatus.ACCEPTED -> Triple(
                    R.string.friend_status_accepted,
                    null,
                    null
                )
                FriendStatus.UNKNOWN -> Triple(
                    R.string.friend_status_unknown,
                    null,
                    null
                )
            }

            statusText.text = context.getString(statusRes)

            if (primaryRes == null) {
                actionsContainer.visibility = View.GONE
                return
            }

            actionsContainer.visibility = View.VISIBLE
            primaryAction.text = context.getString(primaryRes)
            primaryAction.setOnClickListener { listener.onPrimaryAction(item) }

            if (secondaryRes != null) {
                secondaryAction.visibility = View.VISIBLE
                secondaryAction.text = context.getString(secondaryRes)
                secondaryAction.setOnClickListener { listener.onSecondaryAction(item) }
            } else {
                secondaryAction.visibility = View.GONE
            }
        }
    }
}

interface FriendActionListener {
    fun onPrimaryAction(item: FriendUiModel)
    fun onSecondaryAction(item: FriendUiModel)
    fun onProfileClicked(item: FriendUiModel)
}
