package com.intagri.mtgleader.ui.settings.user

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.intagri.mtgleader.persistence.auth.AuthRepository
import com.intagri.mtgleader.persistence.auth.AuthUser
import com.intagri.mtgleader.persistence.userprofile.UserProfileRepository
import com.intagri.mtgleader.livedata.SingleLiveEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

@HiltViewModel
class UserSettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userProfileRepository: UserProfileRepository,
) : ViewModel() {

    private val _state = MutableLiveData(UserSettingsState())
    val state: LiveData<UserSettingsState> = _state
    val events = SingleLiveEvent<UserSettingsEvent>()

    fun refreshUser() {
        if (_state.value?.isLoading == true) {
            return
        }
        val existingUser = _state.value?.user ?: authRepository.getCachedUser()
        _state.value = UserSettingsState(isLoading = true, user = existingUser)
        viewModelScope.launch {
            try {
                val user = authRepository.getCurrentUser(includeStats = true)
                _state.value = UserSettingsState(user = user)
            } catch (e: Exception) {
                _state.value = UserSettingsState(user = existingUser)
            }
        }
    }

    fun updateDisplayName(displayName: String?) {
        if (_state.value?.isLoading == true) {
            return
        }
        val existingUser = _state.value?.user
        _state.value = UserSettingsState(isLoading = true, user = existingUser)
        viewModelScope.launch {
            try {
                val updatedUser = userProfileRepository.updateDisplayName(displayName)
                _state.value = UserSettingsState(user = updatedUser)
                events.value = UserSettingsEvent.ProfileUpdated
            } catch (e: Exception) {
                Log.e(TAG, "updateDisplayName failed", e)
                _state.value = UserSettingsState(user = existingUser)
                events.value = if (e.isAuthError()) {
                    UserSettingsEvent.AuthRequired
                } else {
                    UserSettingsEvent.ProfileUpdateFailed
                }
            }
        }
    }

    fun updateAvatar(avatarUri: String) {
        if (_state.value?.isLoading == true) {
            return
        }
        val existingUser = _state.value?.user
        _state.value = UserSettingsState(isLoading = true, user = existingUser)
        viewModelScope.launch {
            try {
                val updatedUser = userProfileRepository.uploadAvatar(avatarUri)
                _state.value = UserSettingsState(user = updatedUser)
                events.value = UserSettingsEvent.AvatarUpdated
            } catch (e: Exception) {
                Log.e(TAG, "updateAvatar failed", e)
                _state.value = UserSettingsState(user = existingUser)
                events.value = if (e.isAuthError()) {
                    UserSettingsEvent.AuthRequired
                } else {
                    UserSettingsEvent.AvatarUpdateFailed
                }
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

    private fun Exception.isAuthError(): Boolean {
        return (this as? HttpException)?.code() == 401
    }

    companion object {
        private const val TAG = "UserSettingsViewModel"
    }
}

data class UserSettingsState(
    val isLoading: Boolean = false,
    val user: AuthUser? = null,
    val error: Throwable? = null,
)

enum class UserSettingsEvent {
    ProfileUpdated,
    ProfileUpdateFailed,
    AvatarUpdated,
    AvatarUpdateFailed,
    AuthRequired,
}
