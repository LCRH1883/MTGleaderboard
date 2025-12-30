package com.intagri.mtgleader.ui.settings.friends

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intagri.mtgleader.BuildConfig
import com.intagri.mtgleader.livedata.SingleLiveEvent
import com.intagri.mtgleader.persistence.friends.FriendAvatarStore
import com.intagri.mtgleader.persistence.friends.FriendConnectionDto
import com.intagri.mtgleader.persistence.friends.FriendsRepository
import com.intagri.mtgleader.persistence.friends.UserSummaryDto
import com.intagri.mtgleader.persistence.images.ImageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import javax.inject.Inject

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val friendsRepository: FriendsRepository,
    private val imageRepository: ImageRepository,
    private val friendAvatarStore: FriendAvatarStore,
) : ViewModel() {

    private val _friends = MutableLiveData<List<FriendUiModel>>(emptyList())
    val friends: LiveData<List<FriendUiModel>> = _friends

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    val events = SingleLiveEvent<FriendsEvent>()
    private var isPrefetching = false

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
                prefetchAvatars(connections)
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
                prefetchAvatars(connections)
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
                prefetchAvatars(connections)
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

    private fun prefetchAvatars(connections: List<FriendConnectionDto>) {
        if (isPrefetching) {
            return
        }
        isPrefetching = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    connections.forEach { connection ->
                        val user = connection.user
                        val userId = user.id
                        val updatedAt = user.avatarUpdatedAt
                        if (user.avatarPath.isNullOrBlank() || updatedAt.isNullOrBlank()) {
                            return@forEach
                        }
                        val cached = friendAvatarStore.getCachedAvatarUri(userId, updatedAt)
                        if (!cached.isNullOrBlank()) {
                            return@forEach
                        }
                        val url = buildAvatarUrl(user)
                        if (url.isNullOrBlank()) {
                            return@forEach
                        }
                        try {
                            val result = imageRepository.saveUrlImageToDisk(url).first()
                            val file = result.file
                            if (file != null) {
                                friendAvatarStore.saveCachedAvatar(
                                    userId,
                                    updatedAt,
                                    Uri.fromFile(file).toString()
                                )
                            }
                        } catch (_: Exception) {
                            // Keep remote URL fallback.
                        }
                    }
                }
                updateFriends(connections)
            } finally {
                isPrefetching = false
            }
        }
    }

    private fun Exception.isAuthError(): Boolean {
        return (this as? HttpException)?.code() == 401
    }

    private fun UserSummaryDto.toUiModel(status: FriendStatus): FriendUiModel? {
        val displayName = displayName ?: username ?: id
        return FriendUiModel(
            id = id,
            displayName = displayName,
            username = username,
            status = status,
            avatarUrl = resolveAvatarUrl(this)
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
            username = user.username,
            status = status,
            avatarUrl = resolveAvatarUrl(user)
        )
    }

    private fun resolveAvatarUrl(user: UserSummaryDto): String? {
        val cached = friendAvatarStore.getCachedAvatarUri(user.id, user.avatarUpdatedAt)
        if (!cached.isNullOrBlank()) {
            return cached
        }
        return buildAvatarUrl(user)
    }

    private fun buildAvatarUrl(user: UserSummaryDto): String? {
        if (!user.avatarUrl.isNullOrBlank()) {
            return user.avatarUrl
        }
        val path = user.avatarPath ?: return null
        val updatedAt = user.avatarUpdatedAt
        val cacheBuster = updatedAt?.let { toEpochSeconds(it) } ?: System.currentTimeMillis() / 1000
        return BuildConfig.API_BASE_URL.trimEnd('/') + "/app/avatars/" + path + "?v=" + cacheBuster
    }

    private fun toEpochSeconds(value: String): Long {
        return try {
            Instant.parse(value).epochSecond
        } catch (_: Exception) {
            System.currentTimeMillis() / 1000
        }
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
