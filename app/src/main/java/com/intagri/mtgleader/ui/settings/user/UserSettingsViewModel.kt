package com.intagri.mtgleader.ui.settings.user

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intagri.mtgleader.persistence.auth.AuthRepository
import com.intagri.mtgleader.persistence.auth.AuthUser
import com.intagri.mtgleader.persistence.notifications.NotificationsRepository
import com.intagri.mtgleader.persistence.userprofile.UserProfileRepository
import com.intagri.mtgleader.persistence.userprofile.UserProfileLocalStore
import com.intagri.mtgleader.persistence.sync.AvatarUploadPayload
import com.intagri.mtgleader.persistence.sync.DisplayNamePayload
import com.intagri.mtgleader.persistence.sync.SyncAction
import com.intagri.mtgleader.persistence.sync.SyncEntityType
import com.intagri.mtgleader.persistence.sync.SyncQueueDao
import com.intagri.mtgleader.persistence.sync.SyncQueueEntity
import com.intagri.mtgleader.persistence.sync.SyncScheduler
import com.intagri.mtgleader.util.TimestampUtils
import com.intagri.mtgleader.livedata.SingleLiveEvent
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import javax.inject.Inject

@HiltViewModel
class UserSettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: android.content.Context,
    private val authRepository: AuthRepository,
    private val userProfileRepository: UserProfileRepository,
    private val userProfileLocalStore: UserProfileLocalStore,
    private val notificationsRepository: NotificationsRepository,
    private val syncQueueDao: SyncQueueDao,
    moshi: Moshi,
) : ViewModel() {

    private val _state = MutableLiveData(UserSettingsState())
    val state: LiveData<UserSettingsState> = _state
    val events = SingleLiveEvent<UserSettingsEvent>()
    private val displayNameAdapter = moshi.adapter(DisplayNamePayload::class.java)
    private val avatarAdapter = moshi.adapter(AvatarUploadPayload::class.java)

    init {
        viewModelScope.launch {
            userProfileLocalStore.observe().collect { user ->
                val current = _state.value ?: UserSettingsState()
                _state.value = current.copy(user = user, isLoading = false)
            }
        }
    }

    fun refreshUser() {
        if (_state.value?.isLoading == true) {
            return
        }
        val existingUser = _state.value?.user ?: authRepository.getCachedUser()
        _state.value = UserSettingsState(isLoading = true, user = existingUser)
        viewModelScope.launch {
            try {
                authRepository.getCurrentUser(includeStats = true)
                SyncScheduler.enqueueNow(appContext)
            } catch (e: Exception) {
                _state.value = UserSettingsState(user = existingUser)
            } finally {
                val updated = _state.value ?: UserSettingsState()
                _state.value = updated.copy(isLoading = false)
            }
        }
    }

    fun updateDisplayName(displayName: String?) {
        if (_state.value?.isLoading == true) {
            return
        }
        val normalized = displayName?.trim() ?: ""
        val updatedAt = TimestampUtils.nowRfc3339Millis()
        viewModelScope.launch {
            userProfileLocalStore.updateDisplayName(normalized, updatedAt)
            enqueueDisplayNameSync(normalized, updatedAt)
            events.value = UserSettingsEvent.ProfileSavedLocally
        }
    }

    fun updateAvatar(avatarUri: String) {
        viewModelScope.launch {
            try {
                val processedFile = userProfileRepository.prepareAvatarFile(avatarUri)
                val localUri = android.net.Uri.fromFile(processedFile).toString()
                val updatedAt = TimestampUtils.nowRfc3339Millis()
                userProfileLocalStore.updateAvatar(localUri, updatedAt)
                enqueueAvatarSync(localUri, updatedAt)
                events.value = UserSettingsEvent.AvatarUpdated
            } catch (e: Exception) {
                val updatedAt = TimestampUtils.nowRfc3339Millis()
                userProfileLocalStore.updateAvatar(avatarUri, updatedAt)
                enqueueAvatarSync(avatarUri, updatedAt)
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
                notificationsRepository.deleteRegisteredToken()
                authRepository.logout()
                _state.value = UserSettingsState(user = null)
            } catch (e: Exception) {
                _state.value = UserSettingsState(user = existingUser, error = e)
            }
        }
    }

    private suspend fun enqueueDisplayNameSync(displayName: String?, updatedAt: String) {
        val payloadJson = displayNameAdapter.toJson(
            DisplayNamePayload(displayName = displayName, updatedAt = updatedAt)
        )
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = SyncEntityType.PROFILE,
                action = SyncAction.UPDATE_DISPLAY_NAME,
                payloadJson = payloadJson,
                createdAt = System.currentTimeMillis(),
            )
        )
        SyncScheduler.enqueueNow(appContext)
    }

    private suspend fun enqueueAvatarSync(localUri: String, updatedAt: String) {
        val payloadJson = avatarAdapter.toJson(
            AvatarUploadPayload(localUri = localUri, updatedAt = updatedAt)
        )
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = SyncEntityType.AVATAR,
                action = SyncAction.UPLOAD_AVATAR,
                payloadJson = payloadJson,
                createdAt = System.currentTimeMillis(),
            )
        )
        SyncScheduler.enqueueNow(appContext)
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
