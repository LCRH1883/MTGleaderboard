package com.intagri.mtgleader.ui.auth

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.intagri.mtgleader.R
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class ForgotPasswordFragment : Fragment() {

    companion object {
        fun newInstance() = ForgotPasswordFragment()
        const val TAG = "fragment_forgot_password"
    }

    private val viewModel: PasswordResetViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        return inflater.inflate(R.layout.fragment_forgot_password, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }

        val emailInput = view.findViewById<EditText>(R.id.email_input)
        val submitButton = view.findViewById<Button>(R.id.reset_button)
        val errorText = view.findViewById<TextView>(R.id.error_text)
        val successText = view.findViewById<TextView>(R.id.success_text)

        submitButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            if (email.isBlank()) {
                errorText.text = getString(R.string.reset_missing_email)
                errorText.visibility = View.VISIBLE
                successText.visibility = View.GONE
                return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                errorText.text = getString(R.string.reset_invalid_email)
                errorText.visibility = View.VISIBLE
                successText.visibility = View.GONE
                return@setOnClickListener
            }
            errorText.visibility = View.GONE
            successText.visibility = View.GONE
            viewModel.requestReset(email.lowercase(Locale.ROOT))
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            val isLoading = state.isLoading
            emailInput.isEnabled = !isLoading
            submitButton.isEnabled = !isLoading
            if (isLoading) {
                errorText.visibility = View.GONE
                successText.visibility = View.GONE
            }
            if (state.success) {
                successText.text = getString(R.string.reset_email_sent)
                successText.visibility = View.VISIBLE
                errorText.visibility = View.GONE
                return@observe
            }
            state.error?.let {
                val message = when (state.errorCode) {
                    "validation_error" -> getString(R.string.reset_invalid_email)
                    "rate_limited" -> getString(R.string.auth_rate_limited)
                    "smtp_unavailable" -> getString(R.string.reset_smtp_unavailable)
                    "reset_failed" -> getString(R.string.reset_failed)
                    else -> getString(R.string.reset_failed)
                }
                errorText.text = message
                errorText.visibility = View.VISIBLE
                successText.visibility = View.GONE
            }
        }
    }
}
