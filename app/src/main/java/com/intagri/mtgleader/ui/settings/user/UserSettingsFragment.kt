package com.intagri.mtgleader.ui.settings.user

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.intagri.mtgleader.R
import com.intagri.mtgleader.ui.auth.LoginFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class UserSettingsFragment : Fragment() {

    companion object {
        fun newInstance(): UserSettingsFragment {
            val args = Bundle()
            val fragment = UserSettingsFragment()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "fragment_user_settings"
    }

    private val viewModel: UserSettingsViewModel by viewModels()
    private val pickAvatar = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.updateAvatar(it.toString()) }
    }
    private var defaultMessageColor: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        return inflater.inflate(R.layout.fragment_user_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }

        val statusText = view.findViewById<TextView>(R.id.status_text)
        val userInfoGroup = view.findViewById<View>(R.id.user_info_group)
        val avatarImage = view.findViewById<ImageView>(R.id.avatar_image)
        val changeAvatarButton = view.findViewById<Button>(R.id.change_avatar_button)
        val displayNameInput = view.findViewById<EditText>(R.id.display_name_input)
        val saveProfileButton = view.findViewById<Button>(R.id.save_profile_button)
        val usernameValue = view.findViewById<TextView>(R.id.username_value)
        val emailValue = view.findViewById<TextView>(R.id.email_value)
        val messageText = view.findViewById<TextView>(R.id.message_text)
        val errorText = view.findViewById<TextView>(R.id.error_text)
        val loginButton = view.findViewById<Button>(R.id.login_button)
        val logoutButton = view.findViewById<Button>(R.id.logout_button)
        defaultMessageColor = messageText.currentTextColor

        loginButton.setOnClickListener {
            val f = LoginFragment.newInstance()
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.container, f)
                .addToBackStack(LoginFragment.TAG)
                .commit()
        }

        logoutButton.setOnClickListener {
            viewModel.logout()
        }

        changeAvatarButton.setOnClickListener {
            pickAvatar.launch("image/*")
        }

        avatarImage.setOnClickListener {
            pickAvatar.launch("image/*")
        }

        saveProfileButton.setOnClickListener {
            viewModel.updateDisplayName(displayNameInput.text?.toString())
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            val user = state.user
            val isLoggedIn = user != null
            statusText.text = if (isLoggedIn) {
                getString(R.string.user_settings_logged_in, user.username)
            } else {
                getString(R.string.user_settings_logged_out)
            }
            userInfoGroup.visibility = if (isLoggedIn) View.VISIBLE else View.GONE
            loginButton.visibility = if (isLoggedIn) View.GONE else View.VISIBLE
            logoutButton.visibility = if (isLoggedIn) View.VISIBLE else View.GONE
            changeAvatarButton.visibility = if (isLoggedIn) View.VISIBLE else View.GONE
            saveProfileButton.visibility = if (isLoggedIn) View.VISIBLE else View.GONE
            loginButton.isEnabled = !state.isLoading
            logoutButton.isEnabled = !state.isLoading
            changeAvatarButton.isEnabled = !state.isLoading
            saveProfileButton.isEnabled = !state.isLoading
            displayNameInput.isEnabled = !state.isLoading
            usernameValue.text = user?.username.orEmpty()
            emailValue.text = user?.email.orEmpty()
            if (user != null && !displayNameInput.isFocused) {
                displayNameInput.setText(user.displayName.orEmpty())
            }
            if (user?.avatarUrl.isNullOrBlank()) {
                avatarImage.setImageResource(R.drawable.ic_skull)
            } else {
                Glide.with(this)
                    .load(user?.avatarUrl)
                    .placeholder(R.drawable.ic_skull)
                    .into(avatarImage)
            }
            if (state.error != null) {
                errorText.text = getString(R.string.logout_failed)
                errorText.visibility = View.VISIBLE
            } else {
                errorText.visibility = View.GONE
            }
        }

        viewModel.events.observe(viewLifecycleOwner) { event ->
            when (event) {
                UserSettingsEvent.ProfileUpdated -> showMessage(
                    messageText,
                    getString(R.string.profile_update_success),
                    isError = false
                )
                UserSettingsEvent.ProfileUpdateFailed -> showMessage(
                    messageText,
                    getString(R.string.profile_update_failed),
                    isError = true
                )
                UserSettingsEvent.AvatarUpdated -> showMessage(
                    messageText,
                    getString(R.string.avatar_update_success),
                    isError = false
                )
                UserSettingsEvent.AvatarUpdateFailed -> showMessage(
                    messageText,
                    getString(R.string.avatar_update_failed),
                    isError = true
                )
                UserSettingsEvent.AuthRequired -> showMessage(
                    messageText,
                    getString(R.string.profile_auth_required),
                    isError = true
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshUser()
    }

    private fun showMessage(view: TextView, message: String, isError: Boolean) {
        view.text = message
        if (isError) {
            view.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.light_red
                )
            )
        } else {
            view.setTextColor(defaultMessageColor)
        }
        view.visibility = View.VISIBLE
    }
}
