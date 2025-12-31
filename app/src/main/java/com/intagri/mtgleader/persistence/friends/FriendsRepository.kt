package com.intagri.mtgleader.persistence.friends

import android.content.Context
import androidx.room.withTransaction
import com.intagri.mtgleader.persistence.AppDatabase
import com.intagri.mtgleader.persistence.sync.FriendRequestPayload
import com.intagri.mtgleader.persistence.sync.SyncAction
import com.intagri.mtgleader.persistence.sync.SyncEntityType
import com.intagri.mtgleader.persistence.sync.SyncMetadataEntity
import com.intagri.mtgleader.persistence.sync.SyncQueueDao
import com.intagri.mtgleader.persistence.sync.SyncQueueEntity
import com.intagri.mtgleader.persistence.sync.SyncScheduler
import com.intagri.mtgleader.persistence.sync.SyncMetadataDao
import com.intagri.mtgleader.util.TimestampUtils
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import retrofit2.HttpException
import java.util.UUID

class FriendsRepository(
    private val friendsApi: FriendsApi,
    private val appDatabase: AppDatabase,
    private val friendDao: FriendDao,
    private val friendRequestDao: FriendRequestDao,
    private val syncQueueDao: SyncQueueDao,
    private val syncMetadataDao: SyncMetadataDao,
    moshi: Moshi,
    @ApplicationContext private val appContext: Context,
) {
    private val connectionsAdapter: JsonAdapter<List<FriendConnectionDto>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, FriendConnectionDto::class.java)
    )
    private val friendRequestPayloadAdapter: JsonAdapter<FriendRequestPayload> =
        moshi.adapter(FriendRequestPayload::class.java)

    suspend fun getConnections(): List<FriendConnectionDto> {
        return try {
            friendsApi.getConnections()
        } catch (e: HttpException) {
            if (e.code() != 404) {
                throw e
            }
            val overview = friendsApi.getFriends()
            overviewToConnections(overview)
        }
    }

    suspend fun refreshConnections() {
        val etag = syncMetadataDao.get(FRIENDS_ETAG_KEY)
        val response = friendsApi.getConnectionsWithEtag(etag)
        if (response.code() == 304) {
            return
        }
        if (response.isSuccessful) {
            val connections = response.body().orEmpty()
            persistConnections(connections)
            val newEtag = response.headers()["ETag"]
            if (!newEtag.isNullOrBlank()) {
                syncMetadataDao.put(
                    SyncMetadataEntity(
                        key = FRIENDS_ETAG_KEY,
                        value = newEtag,
                        updatedAtEpoch = System.currentTimeMillis(),
                    )
                )
            }
            return
        }
        if (response.code() == 404) {
            val overview = friendsApi.getFriends()
            persistConnections(overviewToConnections(overview))
            return
        }
        throw HttpException(response)
    }

    suspend fun sendFriendRequest(username: String, updatedAt: String? = null) {
        friendsApi.sendFriendRequest(FriendRequestCreate(username = username, updatedAt = updatedAt))
    }

    suspend fun acceptRequest(id: String, request: FriendActionRequest? = null) {
        friendsApi.acceptRequest(id, request)
    }

    suspend fun declineRequest(id: String, request: FriendActionRequest? = null) {
        friendsApi.declineRequest(id, request)
    }

    suspend fun cancelRequest(id: String, request: FriendActionRequest? = null) {
        friendsApi.cancelRequest(id, request)
    }

    suspend fun queueSendFriendRequest(username: String) {
        val updatedAt = TimestampUtils.nowRfc3339Millis()
        val localRequestId = "local_" + UUID.randomUUID().toString()
        friendRequestDao.upsert(
            FriendRequestEntity(
                requestId = localRequestId,
                userId = username,
                username = username,
                displayName = username,
                avatarPath = null,
                avatarUpdatedAt = null,
                status = STATUS_OUTGOING,
                createdAt = updatedAt,
                updatedAt = updatedAt,
                resolvedAt = null,
                isPendingSync = true,
            )
        )
        enqueueFriendRequest(
            action = SyncAction.SEND_REQUEST,
            payload = FriendRequestPayload(username = username, updatedAt = updatedAt),
        )
    }

    suspend fun queueAcceptRequest(requestId: String) {
        val updatedAt = TimestampUtils.nowRfc3339Millis()
        val existing = friendRequestDao.getById(requestId)
        if (existing != null) {
            friendRequestDao.deleteById(requestId)
            friendDao.upsert(
                FriendEntity(
                    userId = existing.userId,
                    username = existing.username,
                    displayName = existing.displayName,
                    avatarPath = existing.avatarPath,
                    avatarUpdatedAt = existing.avatarUpdatedAt,
                    updatedAt = updatedAt,
                    lastSeenAt = null,
                )
            )
        }
        enqueueFriendRequest(
            action = SyncAction.ACCEPT,
            payload = FriendRequestPayload(requestId = requestId, updatedAt = updatedAt),
        )
    }

    suspend fun queueDeclineRequest(requestId: String) {
        val updatedAt = TimestampUtils.nowRfc3339Millis()
        friendRequestDao.deleteById(requestId)
        enqueueFriendRequest(
            action = SyncAction.DECLINE,
            payload = FriendRequestPayload(requestId = requestId, updatedAt = updatedAt),
        )
    }

    suspend fun queueCancelRequest(requestId: String) {
        val updatedAt = TimestampUtils.nowRfc3339Millis()
        friendRequestDao.deleteById(requestId)
        enqueueFriendRequest(
            action = SyncAction.CANCEL,
            payload = FriendRequestPayload(requestId = requestId, updatedAt = updatedAt),
        )
    }

    suspend fun handleActionConflict(error: HttpException): Boolean {
        if (error.code() != 409) {
            return false
        }
        val body = runCatching { error.response()?.errorBody()?.string() }.getOrNull()
        val parsed = body?.let { runCatching { connectionsAdapter.fromJson(it) }.getOrNull() }
        if (parsed != null) {
            persistConnections(parsed)
            return true
        }
        return false
    }

    private suspend fun persistConnections(connections: List<FriendConnectionDto>) {
        val fetchedAt = TimestampUtils.nowRfc3339Millis()
        val accepted = connections
            .filter { it.status.isAccepted() }
            .map { it.toFriendEntity(fetchedAt) }
        val incoming = connections
            .filter { it.status.isIncoming() }
            .map { it.toFriendRequestEntity(STATUS_INCOMING, fetchedAt) }
        val outgoing = connections
            .filter { it.status.isOutgoing() }
            .map { it.toFriendRequestEntity(STATUS_OUTGOING, fetchedAt) }
        appDatabase.withTransaction {
            friendDao.replaceAllAccepted(accepted)
            friendRequestDao.replaceAllRequests(incoming, outgoing)
        }
    }

    private suspend fun enqueueFriendRequest(action: String, payload: FriendRequestPayload) {
        val payloadJson = friendRequestPayloadAdapter.toJson(payload)
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = SyncEntityType.FRIEND_REQUEST,
                action = action,
                payloadJson = payloadJson,
                createdAt = System.currentTimeMillis(),
                attemptCount = 0,
                lastError = null,
            )
        )
        SyncScheduler.enqueueNow(appContext)
    }

    private fun overviewToConnections(overview: FriendsOverviewDto): List<FriendConnectionDto> {
        val accepted = overview.friends.map {
            FriendConnectionDto(user = it, status = STATUS_ACCEPTED)
        }
        val incoming = overview.incomingRequests.map {
            FriendConnectionDto(
                user = it.user,
                status = STATUS_INCOMING,
                requestId = it.id,
                createdAt = it.createdAt,
            )
        }
        val outgoing = overview.outgoingRequests.map {
            FriendConnectionDto(
                user = it.user,
                status = STATUS_OUTGOING,
                requestId = it.id,
                createdAt = it.createdAt,
            )
        }
        return incoming + accepted + outgoing
    }

    private fun FriendConnectionDto.toFriendEntity(fetchedAt: String): FriendEntity {
        val resolvedUpdatedAt = updatedAt ?: avatarUpdatedAtOrFallback(fetchedAt)
        return FriendEntity(
            userId = user.id,
            username = user.username,
            displayName = user.displayName,
            avatarPath = user.avatarPath ?: user.avatarUrl,
            avatarUpdatedAt = user.avatarUpdatedAt,
            updatedAt = resolvedUpdatedAt,
            lastSeenAt = null,
        )
    }

    private fun FriendConnectionDto.toFriendRequestEntity(
        status: String,
        fetchedAt: String,
    ): FriendRequestEntity {
        val resolvedUpdatedAt = updatedAt ?: createdAt ?: fetchedAt
        return FriendRequestEntity(
            requestId = requestId ?: user.id,
            userId = user.id,
            username = user.username,
            displayName = user.displayName,
            avatarPath = user.avatarPath ?: user.avatarUrl,
            avatarUpdatedAt = user.avatarUpdatedAt,
            status = status,
            createdAt = createdAt ?: resolvedUpdatedAt,
            updatedAt = resolvedUpdatedAt,
            resolvedAt = null,
            isPendingSync = false,
        )
    }

    private fun FriendConnectionDto.avatarUpdatedAtOrFallback(fetchedAt: String): String {
        return user.avatarUpdatedAt ?: fetchedAt
    }

    private fun String?.isIncoming(): Boolean = this?.equals(STATUS_INCOMING, ignoreCase = true) == true

    private fun String?.isAccepted(): Boolean = this?.equals(STATUS_ACCEPTED, ignoreCase = true) == true

    private fun String?.isOutgoing(): Boolean = this?.equals(STATUS_OUTGOING, ignoreCase = true) == true

    companion object {
        private const val FRIENDS_ETAG_KEY = "friends_connections_etag"
        private const val STATUS_INCOMING = "incoming"
        private const val STATUS_OUTGOING = "outgoing"
        private const val STATUS_ACCEPTED = "accepted"
    }
}
