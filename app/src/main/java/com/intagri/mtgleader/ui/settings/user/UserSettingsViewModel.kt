package com.intagri.mtgleader.ui.settings.user

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import android.net.Uri
import com.intagri.mtgleader.persistence.auth.AuthRepository
import com.intagri.mtgleader.persistence.auth.AuthUser
import com.intagri.mtgleader.persistence.userprofile.UserProfileRepository
import com.intagri.mtgleader.persistence.userprofile.UserAvatarStore
import com.intagri.mtgleader.persistence.userprofile.UserProfileStore
import com.intagri.mtgleader.persistence.userprofile.AvatarSyncScheduler
import com.intagri.mtgleader.persistence.userprofile.DisplayNameSyncScheduler
import com.intagri.mtgleader.livedata.SingleLiveEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class UserSettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: android.content.Context,
    private val authRepository: AuthRepository,
    private val userProfileRepository: UserProfileRepository,
    private val userAvatarStore: UserAvatarStore,
    private val userProfileStore: UserProfileStore,
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
                _state.value = UserSettingsState(user = applyLocalOverrides(user))
                if (user != null) {
                    if (userAvatarStore.localAvatarUri != null) {
                        AvatarSyncScheduler.enqueue(appContext)
                    }
                    if (userProfileStore.pendingDisplayName != null) {
                        DisplayNameSyncScheduler.enqueue(appContext)
                    }
                }
            } catch (e: Exception) {
                _state.value = UserSettingsState(user = applyLocalOverrides(existingUser))
            }
        }
    }

    fun updateDisplayName(displayName: String?) {
        if (_state.value?.isLoading == true) {
            return
        }
        val existingUser = _state.value?.user
        val normalized = displayName?.trim() ?: ""
        val localUser = existingUser?.copy(displayName = normalized)
        userProfileStore.pendingDisplayName = normalized
        _state.value = UserSettingsState(isLoading = true, user = localUser)
        viewModelScope.launch {
            try {
                val updatedUser = userProfileRepository.updateDisplayName(normalized)
                userProfileStore.clearDisplayName()
                _state.value = UserSettingsState(user = applyLocalOverrides(updatedUser))
                events.value = UserSettingsEvent.ProfileUpdated
            } catch (e: Exception) {
                Log.e(TAG, "updateDisplayName failed", e)
                val httpCode = (e as? HttpException)?.code()
                when {
                    httpCode == 400 -> {
                        userProfileStore.clearDisplayName()
                        _state.value = UserSettingsState(user = existingUser)
                        events.value = UserSettingsEvent.ProfileUpdateFailed
                    }
                    e.isAuthError() -> {
                        _state.value = UserSettingsState(user = localUser ?: existingUser)
                        events.value = UserSettingsEvent.AuthRequired
                    }
                    else -> {
                        _state.value = UserSettingsState(user = localUser ?: existingUser)
                        DisplayNameSyncScheduler.enqueue(appContext)
                        events.value = UserSettingsEvent.ProfileSavedLocally
                    }
                }
            }
        }
    }

    fun updateAvatar(avatarUri: String) {
        val existingUser = _state.value?.user
        viewModelScope.launch {
            try {
                val processedFile = userProfileRepository.prepareAvatarFile(avatarUri)
                val localUri = Uri.fromFile(processedFile).toString()
                val localUser = existingUser?.copy(avatarUrl = localUri, avatar = localUri)
                userAvatarStore.localAvatarUri = localUri
                userAvatarStore.localAvatarUpdatedAt = newUpdatedAtTimestamp()
                _state.value = UserSettingsState(user = localUser)
                AvatarSyncScheduler.enqueue(appContext)
                runCatching {
                    userProfileRepository.uploadAvatarFile(processedFile)
                }.onSuccess { uploadedUser ->
                    if (uploadedUser.avatarUpdatedAt != null) {
                        userAvatarStore.localAvatarUpdatedAt = uploadedUser.avatarUpdatedAt
                    }
                }
                events.value = UserSettingsEvent.AvatarUpdated
            } catch (e: Exception) {
                Log.e(TAG, "updateAvatar failed", e)
                val localUser = existingUser?.copy(avatarUrl = avatarUri, avatar = avatarUri)
                userAvatarStore.localAvatarUri = avatarUri
                userAvatarStore.localAvatarUpdatedAt = newUpdatedAtTimestamp()
                _state.value = UserSettingsState(user = localUser ?: existingUser)
                AvatarSyncScheduler.enqueue(appContext)
                events.value = UserSettingsEvent.AvatarUpdated
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

    private fun applyLocalOverrides(user: AuthUser?): AuthUser? {
        val localAvatarUri = userAvatarStore.localAvatarUri
        val localAvatarUpdatedAt = userAvatarStore.localAvatarUpdatedAt
        val serverAvatarUpdatedAt = user?.avatarUpdatedAt
        val shouldUseLocal = localAvatarUri != null && when {
            localAvatarUpdatedAt == null || serverAvatarUpdatedAt == null -> true
            else -> {
                val localInstant = parseInstant(localAvatarUpdatedAt)
                val serverInstant = parseInstant(serverAvatarUpdatedAt)
                localInstant == null || serverInstant == null || !serverInstant.isAfter(localInstant)
            }
        }
        if (!shouldUseLocal && localAvatarUri != null) {
            userAvatarStore.clear()
        }
        val withAvatar = if (shouldUseLocal) {
            user?.copy(avatarUrl = localAvatarUri, avatar = localAvatarUri)
        } else {
            user
        }
        val localName = userProfileStore.pendingDisplayName
        return if (localName != null) {
            withAvatar?.copy(displayName = localName)
        } else {
            withAvatar
        }
    }

    private fun parseInstant(value: String): Instant? {
        return try {
            Instant.parse(value)
        } catch (_: Exception) {
            null
        }
    }

    private fun newUpdatedAtTimestamp(): String {
        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        return TIMESTAMP_FORMATTER.format(now)
    }

    companion object {
        private const val TAG = "UserSettingsViewModel"
        private const val TIMESTAMP_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        private val TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern(TIMESTAMP_FORMAT)
            .withZone(ZoneOffset.UTC)
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
    ProfileSavedLocally,
    AvatarUpdated,
    AvatarUpdateFailed,
    AuthRequired,
}
