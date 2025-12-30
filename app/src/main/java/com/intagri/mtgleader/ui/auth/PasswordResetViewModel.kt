package com.intagri.mtgleader.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intagri.mtgleader.persistence.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import retrofit2.HttpException

@HiltViewModel
class PasswordResetViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val errorCodeRegex = Regex("\"code\"\\s*:\\s*\"([^\"]+)\"")

    private val _state = MutableLiveData(PasswordResetState())
    val state: LiveData<PasswordResetState> = _state

    fun requestReset(email: String) {
        if (_state.value?.isLoading == true) {
            return
        }
        _state.value = PasswordResetState(isLoading = true)
        viewModelScope.launch {
            try {
                authRepository.requestPasswordReset(email)
                _state.value = PasswordResetState(success = true)
            } catch (e: Exception) {
                _state.value = PasswordResetState(
                    error = e,
                    errorCode = extractErrorCode(e),
                    statusCode = extractStatusCode(e),
                )
            }
        }
    }

    private fun extractStatusCode(error: Throwable): Int? {
        return (error as? HttpException)?.code()
    }

    private fun extractErrorCode(error: Throwable): String? {
        val httpException = error as? HttpException ?: return null
        val body = try {
            httpException.response()?.errorBody()?.string()
        } catch (_: Exception) {
            null
        }
        if (body.isNullOrBlank()) {
            return null
        }
        return errorCodeRegex.find(body)?.groupValues?.getOrNull(1)
    }
}

data class PasswordResetState(
    val isLoading: Boolean = false,
    val success: Boolean = false,
    val error: Throwable? = null,
    val errorCode: String? = null,
    val statusCode: Int? = null,
)
