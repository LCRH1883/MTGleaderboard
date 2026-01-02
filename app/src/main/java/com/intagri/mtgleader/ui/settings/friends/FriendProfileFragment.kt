package com.intagri.mtgleader.ui.settings.friends

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.intagri.mtgleader.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FriendProfileFragment : Fragment() {

    companion object {
        private const val ARG_DISPLAY_NAME = "display_name"
        private const val ARG_USERNAME = "username"
        private const val ARG_AVATAR_URL = "avatar_url"
        private const val ARG_STATUS = "status"

        fun newInstance(item: FriendUiModel): FriendProfileFragment {
            val args = Bundle().apply {
                putString(ARG_DISPLAY_NAME, item.displayName)
                putString(ARG_USERNAME, item.username)
                putString(ARG_AVATAR_URL, item.avatarUrl)
                putString(ARG_STATUS, item.status.name)
            }
            return FriendProfileFragment().apply { arguments = args }
        }

        const val TAG = "fragment_friend_profile"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_friend_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }

        val avatarImage = view.findViewById<ImageView>(R.id.profile_avatar)
        val nameText = view.findViewById<TextView>(R.id.profile_name)
        val usernameText = view.findViewById<TextView>(R.id.profile_username)
        val statusText = view.findViewById<TextView>(R.id.profile_status)

        val displayName = arguments?.getString(ARG_DISPLAY_NAME).orEmpty()
        val username = arguments?.getString(ARG_USERNAME)
        val avatarUrl = arguments?.getString(ARG_AVATAR_URL)
        val status = arguments
            ?.getString(ARG_STATUS)
            ?.let { runCatching { FriendStatus.valueOf(it) }.getOrNull() }
            ?: FriendStatus.UNKNOWN

        nameText.text = displayName
        if (username.isNullOrBlank()) {
            usernameText.visibility = View.GONE
        } else {
            usernameText.text = "@$username"
            usernameText.visibility = View.VISIBLE
        }
        statusText.text = getString(statusToLabel(status))

        if (avatarUrl.isNullOrBlank()) {
            avatarImage.setImageResource(R.drawable.ic_skull)
        } else {
            Glide.with(this)
                .load(avatarUrl)
                .placeholder(R.drawable.ic_skull)
                .into(avatarImage)
        }
    }

    private fun statusToLabel(status: FriendStatus): Int {
        return when (status) {
            FriendStatus.INCOMING -> R.string.friend_status_incoming
            FriendStatus.OUTGOING -> R.string.friend_status_outgoing
            FriendStatus.ACCEPTED -> R.string.friend_status_accepted
            FriendStatus.UNKNOWN -> R.string.friend_status_unknown
        }
    }
}
