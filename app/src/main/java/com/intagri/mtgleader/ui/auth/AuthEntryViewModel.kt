package com.intagri.mtgleader.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intagri.mtgleader.persistence.auth.AuthRepository
import com.intagri.mtgleader.persistence.auth.AuthUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

@HiltViewModel
class AuthEntryViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val errorCodeRegex = Regex("\"code\"\\s*:\\s*\"([^\"]+)\"")

    private val _state = MutableLiveData(AuthEntryState())
    val state: LiveData<AuthEntryState> = _state

    fun login(email: String, password: String) {
        if (_state.value?.isLoading == true) {
            return
        }
        _state.value = AuthEntryState(isLoading = true)
        viewModelScope.launch {
            try {
                val user = authRepository.login(email, password)
                _state.value = AuthEntryState(user = user)
            } catch (e: Exception) {
                _state.value = AuthEntryState(
                    error = e,
                    errorCode = extractErrorCode(e),
                    statusCode = extractStatusCode(e),
                )
            }
        }
    }

    fun register(email: String, username: String, password: String) {
        if (_state.value?.isLoading == true) {
            return
        }
        _state.value = AuthEntryState(isLoading = true)
        viewModelScope.launch {
            try {
                val user = authRepository.register(email, username, password)
                _state.value = AuthEntryState(user = user)
            } catch (e: Exception) {
                _state.value = AuthEntryState(
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

data class AuthEntryState(
    val isLoading: Boolean = false,
    val user: AuthUser? = null,
    val error: Throwable? = null,
    val errorCode: String? = null,
    val statusCode: Int? = null,
)
