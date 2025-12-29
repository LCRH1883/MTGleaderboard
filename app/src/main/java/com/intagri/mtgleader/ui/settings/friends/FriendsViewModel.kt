package com.intagri.mtgleader.ui.settings.friends

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intagri.mtgleader.livedata.SingleLiveEvent
import com.intagri.mtgleader.persistence.friends.FriendConnection
import com.intagri.mtgleader.persistence.friends.FriendsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val friendsRepository: FriendsRepository
) : ViewModel() {

    private val _friends = MutableLiveData<List<FriendUiModel>>(emptyList())
    val friends: LiveData<List<FriendUiModel>> = _friends

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    val events = SingleLiveEvent<FriendsEvent>()

    fun refreshFriends() {
        if (_loading.value == true) {
            return
        }
        loadFriends()
    }

    fun sendInvite(username: String) {
        if (_loading.value == true) {
            return
        }
        _loading.value = true
        viewModelScope.launch {
            try {
                friendsRepository.sendFriendRequest(username)
                events.value = FriendsEvent.InviteSent
                val friends = friendsRepository.getFriends()
                updateFriends(friends)
            } catch (e: Exception) {
                events.value = if (e.isAuthError()) {
                    FriendsEvent.AuthRequired
                } else {
                    FriendsEvent.InviteFailed
                }
            }
            _loading.value = false
        }
    }

    fun acceptRequest(id: String?) {
        handleRequestAction(id) { friendsRepository.acceptRequest(it) }
    }

    fun declineRequest(id: String?) {
        handleRequestAction(id) { friendsRepository.declineRequest(it) }
    }

    fun cancelRequest(id: String?) {
        handleRequestAction(id) { friendsRepository.cancelRequest(it) }
    }

    private fun handleRequestAction(id: String?, action: suspend (String) -> Unit) {
        if (_loading.value == true) {
            return
        }
        if (id.isNullOrBlank()) {
            events.value = FriendsEvent.MissingRequestId
            return
        }
        _loading.value = true
        viewModelScope.launch {
            try {
                action(id)
                val friends = friendsRepository.getFriends()
                updateFriends(friends)
            } catch (e: Exception) {
                events.value = if (e.isAuthError()) {
                    FriendsEvent.AuthRequired
                } else {
                    FriendsEvent.ActionFailed
                }
            }
            _loading.value = false
        }
    }

    private fun loadFriends() {
        _loading.value = true
        viewModelScope.launch {
            try {
                val friends = friendsRepository.getFriends()
                updateFriends(friends)
            } catch (e: Exception) {
                events.value = if (e.isAuthError()) {
                    FriendsEvent.AuthRequired
                } else {
                    FriendsEvent.LoadFailed
                }
            } finally {
                _loading.value = false
            }
        }
    }

    private fun updateFriends(friends: List<FriendConnection>) {
        _friends.value = friends.mapNotNull { it.toUiModel() }
    }

    private fun Exception.isAuthError(): Boolean {
        return (this as? HttpException)?.code() == 401
    }

    private fun FriendConnection.toUiModel(): FriendUiModel? {
        val displayName = username
            ?: user?.username
            ?: email
            ?: user?.email
            ?: id
            ?: requestId
            ?: return null
        val statusValue = status?.lowercase()
        val resolvedStatus = when (statusValue) {
            "incoming", "pending_incoming", "requested", "incoming_request" -> FriendStatus.INCOMING
            "outgoing", "pending_outgoing", "sent", "requested_outgoing" -> FriendStatus.OUTGOING
            "accepted", "friend", "friends" -> FriendStatus.ACCEPTED
            null -> FriendStatus.ACCEPTED
            else -> FriendStatus.UNKNOWN
        }
        val resolvedId = requestId ?: id ?: user?.id
        return FriendUiModel(
            id = resolvedId,
            displayName = displayName,
            status = resolvedStatus
        )
    }
}

enum class FriendsEvent {
    InviteSent,
    InviteFailed,
    ActionFailed,
    MissingRequestId,
    LoadFailed,
    AuthRequired,
}
