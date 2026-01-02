package com.intagri.mtgleader.ui.settings.friends

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.intagri.mtgleader.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FriendsFragment : Fragment(), FriendActionListener {

    companion object {
        fun newInstance(): FriendsFragment {
            val args = Bundle()
            val fragment = FriendsFragment()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "fragment_friends"
    }

    private val viewModel: FriendsViewModel by viewModels()
    private val friendsAdapter = FriendsRecyclerAdapter(this)
    private val usernamePattern = Regex("^[A-Za-z0-9_]+$")

    private lateinit var inviteInput: EditText
    private lateinit var inviteButton: Button
    private lateinit var messageText: TextView
    private lateinit var emptyText: TextView
    private var defaultMessageColor: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        return inflater.inflate(R.layout.fragment_friends, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }

        inviteInput = view.findViewById(R.id.invite_username_input)
        inviteButton = view.findViewById(R.id.send_invite_button)
        messageText = view.findViewById(R.id.message_text)
        emptyText = view.findViewById(R.id.empty_text)
        defaultMessageColor = messageText.currentTextColor

        val recyclerView = view.findViewById<RecyclerView>(R.id.friends_recycler)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = friendsAdapter
        val dividers = DividerItemDecoration(requireContext(), RecyclerView.VERTICAL)
        ContextCompat.getDrawable(requireContext(), R.drawable.nav_menu_divider)?.let {
            dividers.setDrawable(it)
        }
        recyclerView.addItemDecoration(dividers)

        inviteButton.setOnClickListener {
            val username = inviteInput.text.toString().trim()
            if (username.isBlank()) {
                showMessage(getString(R.string.friend_invite_missing_username), isError = true)
                return@setOnClickListener
            }
            if (username.length < 3 || username.length > 24 || !usernamePattern.matches(username)) {
                showMessage(getString(R.string.friend_invite_invalid_username), isError = true)
                return@setOnClickListener
            }
            viewModel.sendInvite(username)
        }

        viewModel.friends.observe(viewLifecycleOwner) { friends ->
            friendsAdapter.setFriends(friends)
            emptyText.visibility = if (friends.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.loading.observe(viewLifecycleOwner) { isLoading ->
            inviteInput.isEnabled = !isLoading
            inviteButton.isEnabled = !isLoading
        }

        viewModel.events.observe(viewLifecycleOwner) { event ->
            when (event) {
                FriendsEvent.InviteSent -> {
                    inviteInput.setText("")
                    showMessage(getString(R.string.friend_invite_sent), isError = false)
                }
                FriendsEvent.InviteFailed -> {
                    showMessage(getString(R.string.friend_invite_failed), isError = true)
                }
                FriendsEvent.ActionFailed -> {
                    showMessage(getString(R.string.friend_action_failed), isError = true)
                }
                FriendsEvent.MissingRequestId -> {
                    showMessage(getString(R.string.friend_action_missing_id), isError = true)
                }
                FriendsEvent.LoadFailed -> {
                    showMessage(getString(R.string.friend_load_failed), isError = true)
                }
                FriendsEvent.AuthRequired -> {
                    showMessage(getString(R.string.friend_auth_required), isError = true)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshFriends()
    }

    override fun onPrimaryAction(item: FriendUiModel) {
        when (item.status) {
            FriendStatus.INCOMING -> viewModel.acceptRequest(item.id)
            FriendStatus.OUTGOING -> viewModel.cancelRequest(item.id)
            FriendStatus.ACCEPTED -> {}
            FriendStatus.UNKNOWN -> {}
        }
    }

    override fun onSecondaryAction(item: FriendUiModel) {
        if (item.status == FriendStatus.INCOMING) {
            viewModel.declineRequest(item.id)
        }
    }

    override fun onProfileClicked(item: FriendUiModel) {
        val f = FriendProfileFragment.newInstance(item)
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.container, f)
            .addToBackStack(FriendProfileFragment.TAG)
            .commit()
    }

    private fun showMessage(message: String, isError: Boolean) {
        messageText.text = message
        if (isError) {
            messageText.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.light_red
                )
            )
        } else {
            messageText.setTextColor(defaultMessageColor)
        }
        messageText.visibility = View.VISIBLE
    }
}
