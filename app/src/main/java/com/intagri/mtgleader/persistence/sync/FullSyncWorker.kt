package com.intagri.mtgleader.persistence.sync

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.intagri.mtgleader.persistence.auth.AuthApi
import com.intagri.mtgleader.persistence.friends.FriendActionRequest
import com.intagri.mtgleader.persistence.friends.FriendsRepository
import com.intagri.mtgleader.persistence.matches.MatchRepository
import com.intagri.mtgleader.persistence.userprofile.UserProfileLocalStore
import com.intagri.mtgleader.persistence.userprofile.UserProfileRepository
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import dagger.hilt.android.EntryPointAccessors
import retrofit2.HttpException
import java.io.File

class FullSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            SyncWorkerEntryPoint::class.java,
        )
        val syncQueueDao = entryPoint.syncQueueDao()
        val userProfileRepository = entryPoint.userProfileRepository()
        val userProfileLocalStore = entryPoint.userProfileLocalStore()
        val friendsRepository = entryPoint.friendsRepository()
        val matchRepository = entryPoint.matchRepository()
        val authApi = entryPoint.authApi()
        val moshi = entryPoint.moshi()

        val displayNameAdapter = moshi.adapter(DisplayNamePayload::class.java)
        val avatarAdapter = moshi.adapter(AvatarUploadPayload::class.java)
        val friendAdapter = moshi.adapter(FriendRequestPayload::class.java)

        var processed = 0
        while (processed < MAX_ITEMS) {
            val item = syncQueueDao.peekOldest() ?: break
            when (val outcome = processQueueItem(
                item = item,
                displayNameAdapter = displayNameAdapter,
                avatarAdapter = avatarAdapter,
                friendAdapter = friendAdapter,
                userProfileRepository = userProfileRepository,
                userProfileLocalStore = userProfileLocalStore,
                friendsRepository = friendsRepository,
                matchRepository = matchRepository,
            )) {
                ProcessOutcome.Delete -> syncQueueDao.deleteById(item.id)
                is ProcessOutcome.Retry -> {
                    syncQueueDao.incrementAttempt(item.id, outcome.errorMessage)
                    return Result.retry()
                }
                ProcessOutcome.StopSuccess -> return Result.success()
            }
            processed++
        }

        if (processed >= MAX_ITEMS && syncQueueDao.count() > 0) {
            SyncScheduler.enqueueNow(applicationContext)
            return Result.success()
        }

        return when (val result = refreshInbound(authApi, userProfileLocalStore, friendsRepository)) {
            PullOutcome.Success -> Result.success()
            PullOutcome.Unauthorized -> Result.success()
            PullOutcome.Retry -> Result.retry()
        }
    }

    private suspend fun processQueueItem(
        item: SyncQueueEntity,
        displayNameAdapter: JsonAdapter<DisplayNamePayload>,
        avatarAdapter: JsonAdapter<AvatarUploadPayload>,
        friendAdapter: JsonAdapter<FriendRequestPayload>,
        userProfileRepository: UserProfileRepository,
        userProfileLocalStore: UserProfileLocalStore,
        friendsRepository: FriendsRepository,
        matchRepository: MatchRepository,
    ): ProcessOutcome {
        return when (item.entityType) {
            SyncEntityType.PROFILE -> when (item.action) {
                SyncAction.UPDATE_DISPLAY_NAME -> handleDisplayNameUpdate(
                    item,
                    displayNameAdapter,
                    userProfileRepository,
                    userProfileLocalStore,
                )
                else -> ProcessOutcome.Delete
            }
            SyncEntityType.AVATAR -> when (item.action) {
                SyncAction.UPLOAD_AVATAR -> handleAvatarUpload(
                    item,
                    avatarAdapter,
                    userProfileRepository,
                    userProfileLocalStore,
                )
                else -> ProcessOutcome.Delete
            }
            SyncEntityType.FRIEND_REQUEST -> handleFriendRequest(
                item,
                friendAdapter,
                friendsRepository,
            )
            SyncEntityType.MATCH -> handleMatchCreate(
                item,
                matchRepository,
            )
            else -> ProcessOutcome.Delete
        }
    }

    private suspend fun handleDisplayNameUpdate(
        item: SyncQueueEntity,
        adapter: JsonAdapter<DisplayNamePayload>,
        userProfileRepository: UserProfileRepository,
        userProfileLocalStore: UserProfileLocalStore,
    ): ProcessOutcome {
        val payload = adapter.fromJson(item.payloadJson) ?: return ProcessOutcome.Delete
        return try {
            val user = userProfileRepository.updateDisplayNameQueued(payload.displayName, payload.updatedAt)
            userProfileLocalStore.upsert(user)
            ProcessOutcome.Delete
        } catch (e: HttpException) {
            when (e.code()) {
                400 -> ProcessOutcome.Delete
                401 -> ProcessOutcome.StopSuccess
                else -> ProcessOutcome.Retry("display_name_http_${e.code()}")
            }
        } catch (e: Exception) {
            ProcessOutcome.Retry(e.message)
        }
    }

    private suspend fun handleAvatarUpload(
        item: SyncQueueEntity,
        adapter: JsonAdapter<AvatarUploadPayload>,
        userProfileRepository: UserProfileRepository,
        userProfileLocalStore: UserProfileLocalStore,
    ): ProcessOutcome {
        val payload = adapter.fromJson(item.payloadJson) ?: return ProcessOutcome.Delete
        val resolved = resolveAvatarFile(payload.localUri) ?: return ProcessOutcome.Delete
        return try {
            val user = userProfileRepository.uploadAvatarQueued(resolved.file, payload.updatedAt)
            userProfileLocalStore.upsert(user)
            val shouldDiscard = user.avatarUpdatedAt != payload.updatedAt
            if (resolved.shouldDelete || shouldDiscard) {
                resolved.file.delete()
            }
            ProcessOutcome.Delete
        } catch (e: HttpException) {
            when (e.code()) {
                400 -> {
                    if (resolved.shouldDelete) {
                        resolved.file.delete()
                    }
                    ProcessOutcome.Delete
                }
                401 -> ProcessOutcome.StopSuccess
                else -> ProcessOutcome.Retry("avatar_http_${e.code()}")
            }
        } catch (e: Exception) {
            ProcessOutcome.Retry(e.message)
        }
    }

    private suspend fun handleFriendRequest(
        item: SyncQueueEntity,
        adapter: JsonAdapter<FriendRequestPayload>,
        friendsRepository: FriendsRepository,
    ): ProcessOutcome {
        val payload = adapter.fromJson(item.payloadJson) ?: return ProcessOutcome.Delete
        return when (item.action) {
            SyncAction.SEND_REQUEST -> {
                val username = payload.username ?: return ProcessOutcome.Delete
                try {
                    friendsRepository.sendFriendRequest(username)
                    friendsRepository.refreshConnections()
                    ProcessOutcome.Delete
                } catch (e: HttpException) {
                    when (e.code()) {
                        400 -> ProcessOutcome.Delete
                        401 -> ProcessOutcome.StopSuccess
                        409 -> {
                            if (!friendsRepository.handleActionConflict(e)) {
                                friendsRepository.refreshConnections()
                            }
                            ProcessOutcome.Delete
                        }
                        else -> ProcessOutcome.Retry("friend_send_http_${e.code()}")
                    }
                } catch (e: Exception) {
                    ProcessOutcome.Retry(e.message)
                }
            }
            SyncAction.ACCEPT,
            SyncAction.DECLINE,
            SyncAction.CANCEL -> {
                val requestId = payload.requestId ?: return ProcessOutcome.Delete
                try {
                    val request = FriendActionRequest(updatedAt = payload.updatedAt)
                    when (item.action) {
                        SyncAction.ACCEPT -> friendsRepository.acceptRequest(requestId, request)
                        SyncAction.DECLINE -> friendsRepository.declineRequest(requestId, request)
                        SyncAction.CANCEL -> friendsRepository.cancelRequest(requestId, request)
                    }
                    friendsRepository.refreshConnections()
                    ProcessOutcome.Delete
                } catch (e: HttpException) {
                    when (e.code()) {
                        400 -> ProcessOutcome.Delete
                        401 -> ProcessOutcome.StopSuccess
                        404 -> {
                            friendsRepository.refreshConnections()
                            ProcessOutcome.Delete
                        }
                        409 -> {
                            if (!friendsRepository.handleActionConflict(e)) {
                                friendsRepository.refreshConnections()
                            }
                            ProcessOutcome.Delete
                        }
                        else -> ProcessOutcome.Retry("friend_action_http_${e.code()}")
                    }
                } catch (e: Exception) {
                    ProcessOutcome.Retry(e.message)
                }
            }
            else -> ProcessOutcome.Delete
        }
    }

    private suspend fun handleMatchCreate(
        item: SyncQueueEntity,
        matchRepository: MatchRepository,
    ): ProcessOutcome {
        if (item.action != SyncAction.CREATE) {
            return ProcessOutcome.Delete
        }
        return when (val result = matchRepository.uploadMatchQueued(item.payloadJson)) {
            MatchRepository.MatchUploadResult.Success -> ProcessOutcome.Delete
            MatchRepository.MatchUploadResult.Unauthorized -> ProcessOutcome.StopSuccess
            is MatchRepository.MatchUploadResult.PermanentFailure -> ProcessOutcome.Delete
            is MatchRepository.MatchUploadResult.Retry -> ProcessOutcome.Retry(result.errorMessage)
        }
    }

    private suspend fun refreshInbound(
        authApi: AuthApi,
        userProfileLocalStore: UserProfileLocalStore,
        friendsRepository: FriendsRepository,
    ): PullOutcome {
        return try {
            val user = authApi.getCurrentUser(includeStats = true)
            userProfileLocalStore.upsert(user)
            friendsRepository.refreshConnections()
            PullOutcome.Success
        } catch (e: HttpException) {
            if (e.code() == 401) {
                PullOutcome.Unauthorized
            } else {
                PullOutcome.Retry
            }
        } catch (e: Exception) {
            PullOutcome.Retry
        }
    }

    private fun resolveAvatarFile(uriValue: String): ResolvedFile? {
        val uri = Uri.parse(uriValue)
        if (uri.scheme == "file") {
            val path = uri.path ?: return null
            val file = File(path)
            return if (file.exists()) ResolvedFile(file, file.isInCache()) else null
        }
        if (uri.scheme == "content") {
            val file = File.createTempFile("avatar_sync_", ".jpg", applicationContext.cacheDir)
            applicationContext.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            return ResolvedFile(file, true)
        }
        val file = File(uriValue)
        return if (file.exists()) ResolvedFile(file, file.isInCache()) else null
    }

    private fun File.isInCache(): Boolean {
        return absolutePath.startsWith(applicationContext.cacheDir.absolutePath)
    }

    private data class ResolvedFile(
        val file: File,
        val shouldDelete: Boolean,
    )

    private sealed class ProcessOutcome {
        object Delete : ProcessOutcome()
        object StopSuccess : ProcessOutcome()
        data class Retry(val errorMessage: String?) : ProcessOutcome()
    }

    private enum class PullOutcome {
        Success,
        Unauthorized,
        Retry,
    }

    companion object {
        private const val MAX_ITEMS = 20
    }
}
