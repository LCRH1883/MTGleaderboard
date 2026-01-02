package com.intagri.mtgleader.ui.settings.notifications

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intagri.mtgleader.persistence.Datastore
import com.intagri.mtgleader.persistence.notifications.NotificationsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val datastore: Datastore,
    private val notificationsRepository: NotificationsRepository,
) : ViewModel() {

    private val _friendRequestEnabled =
        MutableLiveData(datastore.friendRequestNotificationsEnabled)
    val friendRequestEnabled: LiveData<Boolean> = _friendRequestEnabled

    fun setFriendRequestEnabled(enabled: Boolean) {
        datastore.friendRequestNotificationsEnabled = enabled
        _friendRequestEnabled.value = enabled
        if (enabled) {
            viewModelScope.launch {
                notificationsRepository.syncTokenIfNeeded()
            }
        } else {
            viewModelScope.launch {
                notificationsRepository.deleteRegisteredToken()
            }
        }
    }
}
