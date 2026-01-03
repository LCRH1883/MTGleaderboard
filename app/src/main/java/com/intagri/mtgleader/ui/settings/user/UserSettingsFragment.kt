package com.intagri.mtgleader.ui.settings.user

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.intagri.mtgleader.R
import com.intagri.mtgleader.ui.auth.LoginFragment
import com.yalantis.ucrop.UCrop
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

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
        private const val AVATAR_SIZE = 96
    }

    private val viewModel: UserSettingsViewModel by viewModels()
    private val pickAvatar = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { launchAvatarCrop(it) }
    }
    private val cropAvatar = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val resultUri = result.data?.let { UCrop.getOutput(it) }
            if (resultUri != null) {
                if (this::avatarImage.isInitialized) {
                    Glide.with(this).clear(avatarImage)
                    avatarImage.setImageURI(resultUri)
                }
                viewModel.updateAvatar(resultUri.toString())
            } else if (this::messageText.isInitialized) {
                showMessage(messageText, getString(R.string.avatar_update_failed), isError = true)
            }
        } else if (result.resultCode == UCrop.RESULT_ERROR) {
            val error = result.data?.let { UCrop.getError(it) }
            Log.e(TAG, "Avatar crop failed", error)
            if (this::messageText.isInitialized) {
                showMessage(messageText, getString(R.string.avatar_update_failed), isError = true)
            }
        }
    }
    private var defaultMessageColor: Int = 0
    private lateinit var messageText: TextView
    private lateinit var avatarImage: ImageView

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
        avatarImage = view.findViewById(R.id.avatar_image)
        val changeAvatarButton = view.findViewById<Button>(R.id.change_avatar_button)
        val displayNameInput = view.findViewById<EditText>(R.id.display_name_input)
        val saveProfileButton = view.findViewById<Button>(R.id.save_profile_button)
        val usernameValue = view.findViewById<TextView>(R.id.username_value)
        val emailValue = view.findViewById<TextView>(R.id.email_value)
        val uploadWifiOnlySwitch = view.findViewById<SwitchCompat>(R.id.upload_wifi_only_switch)
        messageText = view.findViewById(R.id.message_text)
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

        uploadWifiOnlySwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setUploadWifiOnly(isChecked)
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
            val avatarUrl = user?.avatarUrl
            if (avatarUrl.isNullOrBlank()) {
                avatarImage.setImageResource(R.drawable.ic_skull)
            } else if (avatarUrl.startsWith("file:") || avatarUrl.startsWith("content:")) {
                Glide.with(this).clear(avatarImage)
                avatarImage.setImageURI(Uri.parse(avatarUrl))
            } else {
                Glide.with(this)
                    .load(avatarUrl)
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

        viewModel.uploadWifiOnly.observe(viewLifecycleOwner) { enabled ->
            if (uploadWifiOnlySwitch.isChecked != enabled) {
                uploadWifiOnlySwitch.isChecked = enabled == true
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
                UserSettingsEvent.ProfileSavedLocally -> showMessage(
                    messageText,
                    getString(R.string.profile_saved_local),
                    isError = false
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

    private fun launchAvatarCrop(sourceUri: Uri) {
        val context = context ?: return
        val sourceForCrop = copySourceToCache(context, sourceUri) ?: run {
            if (this::messageText.isInitialized) {
                showMessage(messageText, getString(R.string.avatar_update_failed), isError = true)
            }
            return
        }
        val cacheDir = File(context.cacheDir, "ucrop")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val destinationFile = File(cacheDir, "avatar_crop_${System.currentTimeMillis()}.jpg")
        val destination = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            destinationFile
        )
        val options = UCrop.Options().apply {
            setCompressionFormat(Bitmap.CompressFormat.JPEG)
            setCompressionQuality(90)
        }
        val intent = UCrop.of(sourceForCrop, destination)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(AVATAR_SIZE, AVATAR_SIZE)
            .withOptions(options)
            .getIntent(context)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val clipData = ClipData.newUri(context.contentResolver, "Avatar", sourceForCrop)
        clipData.addItem(ClipData.Item(destination))
        intent.clipData = clipData
        cropAvatar.launch(intent)
    }

    private fun copySourceToCache(context: android.content.Context, sourceUri: Uri): Uri? {
        return try {
            val cacheDir = File(context.cacheDir, "ucrop")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val sourceFile = File(cacheDir, "avatar_source_${System.currentTimeMillis()}.tmp")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(sourceFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                sourceFile
            )
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy avatar source", e)
            null
        }
    }

}
