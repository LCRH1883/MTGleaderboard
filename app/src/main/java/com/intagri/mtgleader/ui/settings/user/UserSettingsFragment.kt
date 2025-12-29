package com.intagri.mtgleader.ui.settings.user

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
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
        val usernameValue = view.findViewById<TextView>(R.id.username_value)
        val emailValue = view.findViewById<TextView>(R.id.email_value)
        val errorText = view.findViewById<TextView>(R.id.error_text)
        val loginButton = view.findViewById<Button>(R.id.login_button)
        val logoutButton = view.findViewById<Button>(R.id.logout_button)

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
            loginButton.isEnabled = !state.isLoading
            logoutButton.isEnabled = !state.isLoading
            usernameValue.text = user?.username.orEmpty()
            emailValue.text = user?.email.orEmpty()
            if (state.error != null) {
                errorText.text = getString(R.string.logout_failed)
                errorText.visibility = View.VISIBLE
            } else {
                errorText.visibility = View.GONE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshUser()
    }
}
