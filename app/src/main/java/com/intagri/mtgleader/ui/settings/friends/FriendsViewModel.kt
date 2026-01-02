package com.intagri.mtgleader.ui.settings.friends

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intagri.mtgleader.BuildConfig
import com.intagri.mtgleader.livedata.SingleLiveEvent
import com.intagri.mtgleader.persistence.friends.FriendAvatarStore
import com.intagri.mtgleader.persistence.friends.FriendDao
import com.intagri.mtgleader.persistence.friends.FriendEntity
import com.intagri.mtgleader.persistence.friends.FriendRequestDao
import com.intagri.mtgleader.persistence.friends.FriendRequestEntity
import com.intagri.mtgleader.persistence.friends.FriendsRepository
import com.intagri.mtgleader.persistence.images.ImageRepository
import com.intagri.mtgleader.persistence.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import javax.inject.Inject

@HiltViewModel
class FriendsViewModel @Inject constructor(
    @ApplicationContext private val appContext: android.content.Context,
    private val friendsRepository: FriendsRepository,
    private val imageRepository: ImageRepository,
    private val friendAvatarStore: FriendAvatarStore,
    private val friendDao: FriendDao,
    private val friendRequestDao: FriendRequestDao,
) : ViewModel() {

    private val _friends = MutableLiveData<List<FriendUiModel>>(emptyList())
    val friends: LiveData<List<FriendUiModel>> = _friends

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    val events = SingleLiveEvent<FriendsEvent>()
    private var isPrefetching = false

    init {
        viewModelScope.launch {
            combine(
                friendDao.getAllAccepted(),
                friendRequestDao.getIncoming(),
                friendRequestDao.getOutgoing(),
            ) { accepted, incoming, outgoing ->
                Triple(accepted, incoming, outgoing)
            }.collect { (accepted, incoming, outgoing) ->
                val incomingUi = incoming.map { it.toUiModel(FriendStatus.INCOMING) }
                val acceptedUi = accepted.map { it.toUiModel(FriendStatus.ACCEPTED) }
                val outgoingUi = outgoing.map { it.toUiModel(FriendStatus.OUTGOING) }
                _friends.value = incomingUi + acceptedUi + outgoingUi
                prefetchAvatars(accepted, incoming, outgoing)
            }
        }
    }

    fun refreshFriends() {
        if (_loading.value == true) {
            return
        }
        _loading.value = true
        viewModelScope.launch {
            SyncScheduler.enqueueNow(appContext)
            try {
                withContext(Dispatchers.IO) {
                    friendsRepository.refreshConnections()
                }
            } catch (e: HttpException) {
                if (e.code() == 401) {
                    events.value = FriendsEvent.AuthRequired
                } else {
                    events.value = FriendsEvent.LoadFailed
                }
            } catch (_: Exception) {
                events.value = FriendsEvent.LoadFailed
            }
            _loading.value = false
        }
    }

    fun sendInvite(username: String) {
        if (_loading.value == true) {
            return
        }
        _loading.value = true
        viewModelScope.launch {
            try {
                friendsRepository.queueSendFriendRequest(username)
                events.value = FriendsEvent.InviteSent
            } catch (e: Exception) {
                events.value = FriendsEvent.InviteFailed
            }
            _loading.value = false
        }
    }

    fun acceptRequest(id: String?) {
        handleRequestAction(id) { friendsRepository.queueAcceptRequest(it) }
    }

    fun declineRequest(id: String?) {
        handleRequestAction(id) { friendsRepository.queueDeclineRequest(it) }
    }

    fun cancelRequest(id: String?) {
        handleRequestAction(id) { friendsRepository.queueCancelRequest(it) }
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
            } catch (e: Exception) {
                events.value = FriendsEvent.ActionFailed
            }
            _loading.value = false
        }
    }

    private fun prefetchAvatars(
        accepted: List<FriendEntity>,
        incoming: List<FriendRequestEntity>,
        outgoing: List<FriendRequestEntity>,
    ) {
        if (isPrefetching) {
            return
        }
        isPrefetching = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val candidates = accepted.map { it.toAvatarCandidate() } +
                        incoming.map { it.toAvatarCandidate() } +
                        outgoing.map { it.toAvatarCandidate() }
                    candidates.forEach { candidate ->
                        val userId = candidate.userId ?: return@forEach
                        val updatedAt = candidate.avatarUpdatedAt ?: return@forEach
                        val avatarPath = candidate.avatarPath ?: return@forEach
                        val cached = friendAvatarStore.getCachedAvatarUri(userId, updatedAt)
                        if (!cached.isNullOrBlank()) {
                            return@forEach
                        }
                        if (avatarPath.startsWith("file:") || avatarPath.startsWith("content:")) {
                            return@forEach
                        }
                        val url = resolveAvatarUrl(avatarPath, updatedAt)
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
                val incomingUi = incoming.map { it.toUiModel(FriendStatus.INCOMING) }
                val acceptedUi = accepted.map { it.toUiModel(FriendStatus.ACCEPTED) }
                val outgoingUi = outgoing.map { it.toUiModel(FriendStatus.OUTGOING) }
                _friends.value = incomingUi + acceptedUi + outgoingUi
            } finally {
                isPrefetching = false
            }
        }
    }

    private fun FriendEntity.toUiModel(status: FriendStatus): FriendUiModel {
        val displayName = displayName ?: username ?: userId
        return FriendUiModel(
            id = userId,
            displayName = displayName,
            username = username,
            status = status,
            avatarUrl = resolveAvatarUrl(avatarPath, avatarUpdatedAt, userId)
        )
    }

    private fun FriendRequestEntity.toUiModel(status: FriendStatus): FriendUiModel {
        val displayName = displayName ?: username ?: userId
        val resolvedId = if (status == FriendStatus.ACCEPTED) {
            userId
        } else {
            requestId
        }
        return FriendUiModel(
            id = resolvedId,
            displayName = displayName,
            username = username,
            status = status,
            avatarUrl = resolveAvatarUrl(avatarPath, avatarUpdatedAt, userId)
        )
    }

    private fun resolveAvatarUrl(
        avatarPath: String?,
        avatarUpdatedAt: String?,
        userId: String?,
    ): String? {
        val cached = friendAvatarStore.getCachedAvatarUri(userId, avatarUpdatedAt)
        if (!cached.isNullOrBlank()) {
            return cached
        }
        return resolveAvatarUrl(avatarPath, avatarUpdatedAt)
    }

    private fun resolveAvatarUrl(avatarPath: String?, avatarUpdatedAt: String?): String? {
        if (avatarPath.isNullOrBlank()) {
            return null
        }
        if (avatarPath.startsWith("http") ||
            avatarPath.startsWith("file:") ||
            avatarPath.startsWith("content:")
        ) {
            return avatarPath
        }
        val cacheBuster = avatarUpdatedAt?.let { toEpochSeconds(it) } ?: System.currentTimeMillis() / 1000
        return BuildConfig.API_BASE_URL.trimEnd('/') + "/app/avatars/" + avatarPath + "?v=" + cacheBuster
    }

    private fun toEpochSeconds(value: String): Long {
        return try {
            Instant.parse(value).epochSecond
        } catch (_: Exception) {
            System.currentTimeMillis() / 1000
        }
    }

    private fun FriendEntity.toAvatarCandidate(): AvatarCandidate {
        return AvatarCandidate(userId = userId, avatarPath = avatarPath, avatarUpdatedAt = avatarUpdatedAt)
    }

    private fun FriendRequestEntity.toAvatarCandidate(): AvatarCandidate {
        return AvatarCandidate(userId = userId, avatarPath = avatarPath, avatarUpdatedAt = avatarUpdatedAt)
    }

    private data class AvatarCandidate(
        val userId: String?,
        val avatarPath: String?,
        val avatarUpdatedAt: String?,
    )
}

enum class FriendsEvent {
    InviteSent,
    InviteFailed,
    ActionFailed,
    MissingRequestId,
    LoadFailed,
    AuthRequired,
}
