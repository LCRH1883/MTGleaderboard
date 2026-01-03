package com.intagri.mtgleader.persistence.matches

import android.content.Context
import com.intagri.mtgleader.persistence.sync.MatchQueuePayload
import com.intagri.mtgleader.persistence.sync.SyncAction
import com.intagri.mtgleader.persistence.sync.SyncEntityType
import com.intagri.mtgleader.persistence.Datastore
import com.intagri.mtgleader.persistence.sync.SyncQueueDao
import com.intagri.mtgleader.persistence.sync.SyncQueueEntity
import com.intagri.mtgleader.persistence.sync.SyncScheduler
import com.intagri.mtgleader.persistence.userprofile.UserProfileLocalStore
import com.intagri.mtgleader.util.NetworkUtils
import com.intagri.mtgleader.util.TimestampUtils
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import retrofit2.HttpException
import java.util.UUID
import javax.inject.Inject
import com.intagri.mtgleader.persistence.gamesession.GameSessionRepository

class MatchRepository @Inject constructor(
    private val matchApi: MatchApi,
    private val matchDao: MatchDao,
    private val syncQueueDao: SyncQueueDao,
    private val userProfileLocalStore: UserProfileLocalStore,
    private val gameSessionRepository: GameSessionRepository,
    private val datastore: Datastore,
    moshi: Moshi,
    @ApplicationContext private val appContext: Context,
) {
    private val payloadAdapter: JsonAdapter<MatchPayloadDto> = moshi.adapter(MatchPayloadDto::class.java)
    private val queueAdapter: JsonAdapter<MatchQueuePayload> = moshi.adapter(MatchQueuePayload::class.java)
    private val conflictAdapter: JsonAdapter<MatchConflictResponse> =
        moshi.adapter(MatchConflictResponse::class.java)

    fun observeMatches(): Flow<List<MatchEntity>> = matchDao.observeAll()

    suspend fun recordMatchOffline(payload: MatchPayloadDto): String {
        val localId = UUID.randomUUID().toString()
        val clientMatchId = UUID.randomUUID().toString()
        val updatedAt = TimestampUtils.nowRfc3339Millis()
        recordMatchWithIds(localId, clientMatchId, updatedAt, payload)
        return localId
    }

    suspend fun recordMatchFromSession(
        localId: String,
        clientMatchId: String,
        updatedAt: String,
        payload: MatchPayloadDto,
    ) {
        if (matchDao.getByLocalId(localId) != null) {
            return
        }
        recordMatchWithIds(localId, clientMatchId, updatedAt, payload)
    }

    suspend fun retryUpload(localId: String) {
        val match = matchDao.getByLocalId(localId) ?: return
        val payload = payloadAdapter.fromJson(match.payloadJson) ?: return
        matchDao.insert(
            match.copy(
                status = MatchStatus.PENDING_UPLOAD,
                lastError = null,
            )
        )
        enqueueMatchUpload(match.localId, match.clientMatchId, match.updatedAt, payload)
    }

    suspend fun uploadMatchQueued(queueItemPayloadJson: String): MatchUploadResult {
        val payload = queueAdapter.fromJson(queueItemPayloadJson)
            ?: return MatchUploadResult.PermanentFailure("invalid_payload")
        val request = MatchCreateRequest(
            clientMatchId = payload.clientMatchId,
            updatedAt = payload.updatedAt,
            match = payload.match,
        )
        return try {
            val response = matchApi.createMatch(request)
            val serverId = response.match.id
            matchDao.markSynced(
                localId = payload.localId,
                serverId = serverId,
                status = MatchStatus.SYNCED,
                syncedAtEpoch = System.currentTimeMillis(),
            )
            gameSessionRepository.updateSyncState(
                localMatchId = payload.localId,
                pendingSync = false,
                backendMatchId = serverId,
            )
            response.statsSummary?.let { summary ->
                userProfileLocalStore.updateStatsSummary(summary)
            }
            MatchUploadResult.Success
        } catch (e: HttpException) {
            when (e.code()) {
                400 -> {
                    matchDao.markFailed(
                        localId = payload.localId,
                        status = MatchStatus.FAILED,
                        lastError = "invalid_match",
                    )
                    MatchUploadResult.PermanentFailure("invalid_match")
                }
                401 -> MatchUploadResult.Unauthorized
                409 -> {
                    val conflict = runCatching {
                        e.response()?.errorBody()?.string()
                    }.getOrNull()?.let { body ->
                        runCatching { conflictAdapter.fromJson(body) }.getOrNull()
                    }
                    matchDao.markSynced(
                        localId = payload.localId,
                        serverId = conflict?.match?.id,
                        status = MatchStatus.SYNCED,
                        syncedAtEpoch = System.currentTimeMillis(),
                        lastError = "deduped",
                    )
                    gameSessionRepository.updateSyncState(
                        localMatchId = payload.localId,
                        pendingSync = false,
                        backendMatchId = conflict?.match?.id,
                    )
                    MatchUploadResult.Success
                }
                else -> MatchUploadResult.Retry("match_http_${e.code()}")
            }
        } catch (e: Exception) {
            MatchUploadResult.Retry(e.message)
        }
    }

    private suspend fun recordMatchWithIds(
        localId: String,
        clientMatchId: String,
        updatedAt: String,
        payload: MatchPayloadDto,
    ) {
        val createdAtEpoch = System.currentTimeMillis()
        val payloadJson = payloadAdapter.toJson(payload)
        matchDao.insert(
            MatchEntity(
                localId = localId,
                serverId = null,
                clientMatchId = clientMatchId,
                createdAtEpoch = createdAtEpoch,
                updatedAt = updatedAt,
                status = MatchStatus.PENDING_UPLOAD,
                payloadJson = payloadJson,
                lastError = null,
                syncedAtEpoch = null,
            )
        )
        val immediateUploaded = if (shouldUploadImmediately()) {
            tryUploadImmediate(localId, clientMatchId, updatedAt, payload)
        } else {
            false
        }
        if (!immediateUploaded) {
            enqueueMatchUpload(localId, clientMatchId, updatedAt, payload)
        }
    }

    private fun shouldUploadImmediately(): Boolean {
        return if (datastore.uploadMatchesWifiOnly) {
            NetworkUtils.isWifiConnected(appContext)
        } else {
            NetworkUtils.isOnline(appContext)
        }
    }

    private suspend fun tryUploadImmediate(
        localId: String,
        clientMatchId: String,
        updatedAt: String,
        payload: MatchPayloadDto,
    ): Boolean {
        val request = MatchCreateRequest(
            clientMatchId = clientMatchId,
            updatedAt = updatedAt,
            match = payload,
        )
        return try {
            val response = matchApi.createMatch(request)
            val serverId = response.match.id
            matchDao.markSynced(
                localId = localId,
                serverId = serverId,
                status = MatchStatus.SYNCED,
                syncedAtEpoch = System.currentTimeMillis(),
            )
            gameSessionRepository.updateSyncState(
                localMatchId = localId,
                pendingSync = false,
                backendMatchId = serverId,
            )
            response.statsSummary?.let { summary ->
                userProfileLocalStore.updateStatsSummary(summary)
            }
            true
        } catch (e: HttpException) {
            if (e.code() == 409) {
                val conflict = runCatching {
                    e.response()?.errorBody()?.string()
                }.getOrNull()?.let { body ->
                    runCatching { conflictAdapter.fromJson(body) }.getOrNull()
                }
                matchDao.markSynced(
                    localId = localId,
                    serverId = conflict?.match?.id,
                    status = MatchStatus.SYNCED,
                    syncedAtEpoch = System.currentTimeMillis(),
                    lastError = "deduped",
                )
                gameSessionRepository.updateSyncState(
                    localMatchId = localId,
                    pendingSync = false,
                    backendMatchId = conflict?.match?.id,
                )
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun enqueueMatchUpload(
        localId: String,
        clientMatchId: String,
        updatedAt: String,
        payload: MatchPayloadDto,
    ) {
        val queuePayload = MatchQueuePayload(
            localId = localId,
            clientMatchId = clientMatchId,
            updatedAt = updatedAt,
            match = payload,
        )
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = SyncEntityType.MATCH,
                action = SyncAction.CREATE,
                payloadJson = queueAdapter.toJson(queuePayload),
                createdAt = System.currentTimeMillis(),
            )
        )
        SyncScheduler.enqueueNow(appContext)
    }

    sealed class MatchUploadResult {
        object Success : MatchUploadResult()
        object Unauthorized : MatchUploadResult()
        data class PermanentFailure(val reason: String) : MatchUploadResult()
        data class Retry(val errorMessage: String?) : MatchUploadResult()
    }
}
