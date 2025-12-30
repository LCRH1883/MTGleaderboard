package com.intagri.mtgleader.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.util.Patterns
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.intagri.mtgleader.R
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class LoginFragment : Fragment() {

    companion object {
        fun newInstance() = LoginFragment()
        const val TAG = "fragment_login"
    }

    private val viewModel: AuthEntryViewModel by viewModels()
    private var handledSuccess = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }

        val emailInput = view.findViewById<EditText>(R.id.email_input)
        val passwordInput = view.findViewById<EditText>(R.id.password_input)
        val loginButton = view.findViewById<Button>(R.id.login_button)
        val signupButton = view.findViewById<Button>(R.id.signup_button)
        val forgotPasswordLink = view.findViewById<TextView>(R.id.forgot_password_link)
        val errorText = view.findViewById<TextView>(R.id.error_text)

        loginButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()
            if (email.isBlank() || password.isBlank()) {
                errorText.text = getString(R.string.login_missing_fields)
                errorText.visibility = View.VISIBLE
                return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                errorText.text = getString(R.string.login_invalid_email)
                errorText.visibility = View.VISIBLE
                return@setOnClickListener
            }
            errorText.visibility = View.GONE
            viewModel.login(email.lowercase(Locale.ROOT), password)
        }

        signupButton.setOnClickListener {
            val f = SignupFragment.newInstance()
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.container, f)
                .addToBackStack(SignupFragment.TAG)
                .commit()
        }

        forgotPasswordLink.setOnClickListener {
            val f = ForgotPasswordFragment.newInstance()
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.container, f)
                .addToBackStack(ForgotPasswordFragment.TAG)
                .commit()
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            val isLoading = state.isLoading
            emailInput.isEnabled = !isLoading
            passwordInput.isEnabled = !isLoading
            loginButton.isEnabled = !isLoading
            signupButton.isEnabled = !isLoading
            forgotPasswordLink.isEnabled = !isLoading
            if (isLoading) {
                errorText.visibility = View.GONE
            }
            state.error?.let {
                val message = when (state.errorCode) {
                    "invalid_credentials" -> getString(R.string.login_invalid_credentials)
                    "rate_limited" -> getString(R.string.auth_rate_limited)
                    else -> getString(R.string.login_failed)
                }
                errorText.text = message
                errorText.visibility = View.VISIBLE
            }
            if (state.user != null && !handledSuccess) {
                handledSuccess = true
                parentFragmentManager.popBackStack()
            }
        }
    }
}
