package com.intagri.mtgleader.ui.settings.notifications

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.intagri.mtgleader.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class NotificationsFragment : Fragment() {

    companion object {
        fun newInstance(): NotificationsFragment {
            val fragment = NotificationsFragment()
            fragment.arguments = Bundle()
            return fragment
        }

        const val TAG = "fragment_notifications"
    }

    private val viewModel: NotificationsViewModel by viewModels()

    private lateinit var friendRequestSwitch: SwitchCompat
    private lateinit var messageText: TextView
    private var pendingPermissionRequest = false
    private var isUpdatingSwitch = false

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        pendingPermissionRequest = false
        if (granted) {
            setSwitchChecked(true)
            viewModel.setFriendRequestEnabled(true)
            messageText.visibility = View.GONE
        } else {
            setSwitchChecked(false)
            viewModel.setFriendRequestEnabled(false)
            showMessage(getString(R.string.notifications_permission_denied))
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_notifications, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }

        friendRequestSwitch = view.findViewById(R.id.friend_request_switch)
        messageText = view.findViewById(R.id.notification_message)

        friendRequestSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingSwitch) {
                return@setOnCheckedChangeListener
            }
            if (isChecked && shouldRequestNotificationPermission()) {
                pendingPermissionRequest = true
                requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.setFriendRequestEnabled(isChecked)
                if (!isChecked) {
                    messageText.visibility = View.GONE
                }
            }
        }

        viewModel.friendRequestEnabled.observe(viewLifecycleOwner) { enabled ->
            setSwitchChecked(enabled)
        }
    }

    override fun onResume() {
        super.onResume()
        if (pendingPermissionRequest) {
            return
        }
        if (friendRequestSwitch.isChecked && !hasNotificationPermission()) {
            showMessage(getString(R.string.notifications_permission_denied))
        }
    }

    private fun setSwitchChecked(checked: Boolean) {
        isUpdatingSwitch = true
        friendRequestSwitch.isChecked = checked
        isUpdatingSwitch = false
    }

    private fun shouldRequestNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun showMessage(message: String) {
        messageText.text = message
        messageText.visibility = View.VISIBLE
    }
}
