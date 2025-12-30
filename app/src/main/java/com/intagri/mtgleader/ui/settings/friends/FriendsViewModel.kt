package com.intagri.mtgleader.ui.settings.friends

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intagri.mtgleader.livedata.SingleLiveEvent
import com.intagri.mtgleader.persistence.friends.FriendRequestDto
import com.intagri.mtgleader.persistence.friends.FriendsOverviewDto
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
                val overview = friendsRepository.getFriends()
                updateFriends(overview)
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
                val overview = friendsRepository.getFriends()
                updateFriends(overview)
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
                val overview = friendsRepository.getFriends()
                updateFriends(overview)
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

    private fun updateFriends(overview: FriendsOverviewDto) {
        val incoming = overview.incomingRequests.mapNotNull { it.toUiModel(FriendStatus.INCOMING) }
        val accepted = overview.friends.mapNotNull { it.toUiModel(FriendStatus.ACCEPTED) }
        val outgoing = overview.outgoingRequests.mapNotNull { it.toUiModel(FriendStatus.OUTGOING) }
        _friends.value = incoming + accepted + outgoing
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

    private fun FriendRequestDto.toUiModel(status: FriendStatus): FriendUiModel? {
        val displayName = user.displayName ?: user.username ?: user.id
        return FriendUiModel(
            id = id,
            displayName = displayName,
            status = status
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
