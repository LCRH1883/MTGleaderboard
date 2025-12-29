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
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import com.intagri.mtgleader.R
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class SignupFragment : Fragment() {

    companion object {
        fun newInstance() = SignupFragment()
        const val TAG = "fragment_signup"
    }

    private val viewModel: AuthEntryViewModel by viewModels()
    private var handledSuccess = false
    private val usernamePattern = Regex("^[A-Za-z0-9_]+$")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        return inflater.inflate(R.layout.fragment_signup, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }

        val emailInput = view.findViewById<EditText>(R.id.email_input)
        val usernameInput = view.findViewById<EditText>(R.id.username_input)
        val passwordInput = view.findViewById<EditText>(R.id.password_input)
        val signupButton = view.findViewById<Button>(R.id.signup_button)
        val errorText = view.findViewById<TextView>(R.id.error_text)

        signupButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val username = usernameInput.text.toString().trim()
            val password = passwordInput.text.toString()
            if (email.isBlank() || username.isBlank() || password.isBlank()) {
                errorText.text = getString(R.string.signup_missing_fields)
                errorText.visibility = View.VISIBLE
                return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                errorText.text = getString(R.string.signup_invalid_email)
                errorText.visibility = View.VISIBLE
                return@setOnClickListener
            }
            if (username.length < 3 || username.length > 24 || !usernamePattern.matches(username)) {
                errorText.text = getString(R.string.signup_invalid_username)
                errorText.visibility = View.VISIBLE
                return@setOnClickListener
            }
            if (password.length < 12) {
                errorText.text = getString(R.string.signup_invalid_password)
                errorText.visibility = View.VISIBLE
                return@setOnClickListener
            }
            errorText.visibility = View.GONE
            viewModel.register(email.lowercase(Locale.ROOT), username, password)
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            val isLoading = state.isLoading
            emailInput.isEnabled = !isLoading
            usernameInput.isEnabled = !isLoading
            passwordInput.isEnabled = !isLoading
            signupButton.isEnabled = !isLoading
            if (isLoading) {
                errorText.visibility = View.GONE
            }
            state.error?.let {
                val message = when (state.errorCode) {
                    "email_taken" -> getString(R.string.signup_email_taken)
                    "username_taken" -> getString(R.string.signup_username_taken)
                    "validation_error" -> getString(R.string.signup_validation_error)
                    "rate_limited" -> getString(R.string.auth_rate_limited)
                    else -> getString(R.string.signup_failed)
                }
                errorText.text = message
                errorText.visibility = View.VISIBLE
            }
            if (state.user != null && !handledSuccess) {
                handledSuccess = true
                parentFragmentManager.popBackStack(
                    LoginFragment.TAG,
                    FragmentManager.POP_BACK_STACK_INCLUSIVE
                )
            }
        }
    }
}
