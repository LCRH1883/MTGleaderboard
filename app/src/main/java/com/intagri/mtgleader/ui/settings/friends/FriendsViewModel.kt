package com.intagri.mtgleader.ui.settings.friends

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intagri.mtgleader.BuildConfig
import com.intagri.mtgleader.livedata.SingleLiveEvent
import android.util.Log
import com.intagri.mtgleader.persistence.friends.FriendAvatarStore
import com.intagri.mtgleader.persistence.friends.FriendDao
import com.intagri.mtgleader.persistence.friends.FriendEntity
import com.intagri.mtgleader.persistence.friends.FriendRequestDao
import com.intagri.mtgleader.persistence.friends.FriendRequestEntity
import com.intagri.mtgleader.persistence.friends.FriendsRepository
import com.intagri.mtgleader.persistence.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import retrofit2.HttpException

@HiltViewModel
class FriendsViewModel @Inject constructor(
    @ApplicationContext private val appContext: android.content.Context,
    private val friendsRepository: FriendsRepository,
    private val friendAvatarStore: FriendAvatarStore,
    private val friendDao: FriendDao,
    private val friendRequestDao: FriendRequestDao,
) : ViewModel() {

    private val _friends = MutableLiveData<List<FriendUiModel>>(emptyList())
    val friends: LiveData<List<FriendUiModel>> = _friends

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    val events = SingleLiveEvent<FriendsEvent>()
    private val _actionError = MutableLiveData<String?>(null)
    val actionError: LiveData<String?> = _actionError

    init {
        viewModelScope.launch {
            combine(
                friendRequestDao.getIncoming(),
                friendDao.getAllAccepted(),
                friendRequestDao.getOutgoing(),
            ) { incoming, accepted, outgoing ->
                val incomingUi = incoming.map { it.toUiModel(FriendStatus.INCOMING) }
                val acceptedUi = accepted.map { it.toUiModel(FriendStatus.ACCEPTED) }
                val outgoingUi = outgoing.map { it.toUiModel(FriendStatus.OUTGOING) }
                incomingUi + acceptedUi + outgoingUi
            }.collect { list ->
                _friends.value = list
            }
        }
    }

    fun refreshFriends(force: Boolean = true) {
        viewModelScope.launch {
            _loading.value = true
            _actionError.value = null
            try {
                friendsRepository.refreshConnections(force = force)
            } catch (e: HttpException) {
                handleHttpError(e)
            } catch (e: Exception) {
                events.value = FriendsEvent.LoadFailed
                _actionError.value = buildActionError(e, "refresh")
            } finally {
                _loading.value = false
            }
        }
    }

    fun sendInvite(username: String) {
        _actionError.value = null
        viewModelScope.launch {
            _loading.value = true
            try {
                friendsRepository.sendFriendRequestAndSync(username)
                events.value = FriendsEvent.InviteSent
            } catch (e: HttpException) {
                if (e.code() == 401) {
                    events.value = FriendsEvent.AuthRequired
                } else {
                    events.value = FriendsEvent.InviteFailed
                    _actionError.value = buildActionError(e, username)
                }
            } catch (e: IOException) {
                runCatching { friendsRepository.queueSendFriendRequest(username) }
                SyncScheduler.enqueueNow(appContext)
                events.value = FriendsEvent.InviteSent
            } catch (e: Exception) {
                events.value = FriendsEvent.InviteFailed
                _actionError.value = buildActionError(e, username)
            } finally {
                _loading.value = false
            }
        }
    }

    fun acceptRequest(id: String?) {
        handleRequestAction(
            id = id,
            onRemote = { friendsRepository.acceptRequestAndSync(it) },
            onQueue = { friendsRepository.queueAcceptRequest(it) },
        )
    }

    fun declineRequest(id: String?) {
        handleRequestAction(
            id = id,
            onRemote = { friendsRepository.declineRequestAndSync(it) },
            onQueue = { friendsRepository.queueDeclineRequest(it) },
        )
    }

    fun cancelRequest(id: String?) {
        handleRequestAction(
            id = id,
            onRemote = { friendsRepository.cancelRequestAndSync(it) },
            onQueue = { friendsRepository.queueCancelRequest(it) },
        )
    }

    fun removeFriend(id: String?) {
        handleRequestAction(
            id = id,
            onRemote = {
                friendsRepository.removeFriend(it)
                friendsRepository.refreshConnections(force = true)
            },
            onQueue = { friendsRepository.queueRemoveFriend(it) },
        )
    }

    private fun handleRequestAction(
        id: String?,
        onRemote: suspend (String) -> Unit,
        onQueue: suspend (String) -> Unit,
    ) {
        val trimmedId = id?.trim()
        if (trimmedId.isNullOrBlank()) {
            events.value = FriendsEvent.MissingRequestId
            refreshFriends(force = true)
            return
        }
        Log.d("Friends", "Friend action requested: id=$trimmedId")
        _actionError.value = null
        viewModelScope.launch {
            _loading.value = true
            try {
                onRemote(trimmedId)
            } catch (e: HttpException) {
                if (e.code() == 401) {
                    events.value = FriendsEvent.AuthRequired
                } else {
                    events.value = FriendsEvent.ActionFailed
                    _actionError.value = buildActionError(e, trimmedId)
                }
            } catch (e: IOException) {
                runCatching { onQueue(trimmedId) }
                SyncScheduler.enqueueNow(appContext)
                events.value = FriendsEvent.ActionFailed
                _actionError.value = "Friend action queued; will retry when online. id=$trimmedId"
            } catch (e: Exception) {
                events.value = FriendsEvent.ActionFailed
                _actionError.value = buildActionError(e, trimmedId)
            } finally {
                _loading.value = false
            }
        }
    }

    private fun handleHttpError(error: HttpException) {
        if (error.code() == 401) {
            events.value = FriendsEvent.AuthRequired
        } else {
            events.value = FriendsEvent.LoadFailed
            _actionError.value = buildActionError(error, "refresh")
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

    private fun buildActionError(error: Exception, context: String?): String {
        if (error is HttpException) {
            val code = error.code()
            val body = runCatching { error.response()?.errorBody()?.string() }.getOrNull()
            val method = error.response()?.raw()?.request?.method.orEmpty()
            val url = error.response()?.raw()?.request?.url?.encodedPath.orEmpty()
            val sanitized = body?.trim()?.take(240).orEmpty()
            val base = if (context.isNullOrBlank()) {
                "Friend action failed"
            } else {
                "Friend action failed (context=$context)"
            }
            return if (sanitized.isBlank()) {
                "$base (HTTP $code $method $url)."
            } else {
                "$base (HTTP $code $method $url): $sanitized"
            }
        }
        val message = error.message?.trim().orEmpty()
        val base = if (context.isNullOrBlank()) {
            "Friend action failed"
        } else {
            "Friend action failed (context=$context)"
        }
        return if (message.isBlank()) {
            "$base (unknown error)."
        } else {
            "$base: $message"
        }
    }
}

enum class FriendsEvent {
    InviteSent,
    InviteFailed,
    ActionFailed,
    MissingRequestId,
    LoadFailed,
    AuthRequired,
    FeatureDisabled,
}
