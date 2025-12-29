package com.intagri.mtgleader.ui.settings.user

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intagri.mtgleader.persistence.auth.AuthRepository
import com.intagri.mtgleader.persistence.auth.AuthUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserSettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableLiveData(UserSettingsState())
    val state: LiveData<UserSettingsState> = _state

    fun refreshUser() {
        if (_state.value?.isLoading == true) {
            return
        }
        val existingUser = _state.value?.user
        _state.value = UserSettingsState(isLoading = true, user = existingUser)
        viewModelScope.launch {
            try {
                val user = authRepository.getCurrentUser()
                _state.value = UserSettingsState(user = user)
            } catch (e: Exception) {
                _state.value = UserSettingsState(user = existingUser)
            }
        }
    }

    fun logout() {
        if (_state.value?.isLoading == true || _state.value?.user == null) {
            return
        }
        val existingUser = _state.value?.user
        _state.value = UserSettingsState(isLoading = true, user = existingUser)
        viewModelScope.launch {
            try {
                authRepository.logout()
                _state.value = UserSettingsState(user = null)
            } catch (e: Exception) {
                _state.value = UserSettingsState(user = existingUser, error = e)
            }
        }
    }
}

data class UserSettingsState(
    val isLoading: Boolean = false,
    val user: AuthUser? = null,
    val error: Throwable? = null,
)
