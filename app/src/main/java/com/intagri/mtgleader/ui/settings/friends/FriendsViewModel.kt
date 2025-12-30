package com.intagri.mtgleader.ui.settings.friends

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intagri.mtgleader.livedata.SingleLiveEvent
import com.intagri.mtgleader.persistence.friends.FriendConnectionDto
import com.intagri.mtgleader.persistence.friends.UserSummaryDto
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
                val connections = friendsRepository.getConnections()
                updateFriends(connections)
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
                val connections = friendsRepository.getConnections()
                updateFriends(connections)
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
                val connections = friendsRepository.getConnections()
                updateFriends(connections)
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

    private fun updateFriends(connections: List<FriendConnectionDto>) {
        val incoming = connections
            .filter { it.status.isIncoming() }
            .mapNotNull { it.toUiModel(FriendStatus.INCOMING) }
        val accepted = connections
            .filter { it.status.isAccepted() }
            .mapNotNull { it.toUiModel(FriendStatus.ACCEPTED) }
        val outgoing = connections
            .filter { it.status.isOutgoing() }
            .mapNotNull { it.toUiModel(FriendStatus.OUTGOING) }
        val unknown = connections
            .filter { !it.status.isIncoming() && !it.status.isAccepted() && !it.status.isOutgoing() }
            .mapNotNull { it.toUiModel(FriendStatus.UNKNOWN) }
        _friends.value = incoming + accepted + outgoing + unknown
    }

    private fun Exception.isAuthError(): Boolean {
        return (this as? HttpException)?.code() == 401
    }

    private fun UserSummaryDto.toUiModel(status: FriendStatus): FriendUiModel? {
        val displayName = displayName ?: username ?: id
        return FriendUiModel(
            id = id,
            displayName = displayName,
            status = status
        )
    }

    private fun FriendConnectionDto.toUiModel(status: FriendStatus): FriendUiModel? {
        val displayName = user.displayName ?: user.username ?: user.id
        val resolvedId = when (status) {
            FriendStatus.INCOMING, FriendStatus.OUTGOING -> requestId ?: user.id
            else -> user.id
        }
        return FriendUiModel(
            id = resolvedId,
            displayName = displayName,
            status = status
        )
    }

    private fun String?.isIncoming(): Boolean = this?.equals("incoming", ignoreCase = true) == true

    private fun String?.isAccepted(): Boolean = this?.equals("accepted", ignoreCase = true) == true

    private fun String?.isOutgoing(): Boolean = this?.equals("outgoing", ignoreCase = true) == true
}

enum class FriendsEvent {
    InviteSent,
    InviteFailed,
    ActionFailed,
    MissingRequestId,
    LoadFailed,
    AuthRequired,
}
