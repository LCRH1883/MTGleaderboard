package com.intagri.mtgleader.persistence.matches

import android.content.Context
import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.intagri.mtgleader.TestApplication
import com.intagri.mtgleader.persistence.AppDatabase
import com.intagri.mtgleader.persistence.sync.MatchQueuePayload
import com.intagri.mtgleader.persistence.userprofile.UserProfileLocalStore
import com.squareup.moshi.Moshi
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O], application = TestApplication::class)
class MatchRepositoryTest {

    @Test
    fun recordMatchOffline_enqueuesQueueWithSameUpdatedAtAndClientMatchId() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val db = createDb(context)
        val moshi = Moshi.Builder().build()
        val repo = MatchRepository(
            matchApi = FakeMatchApi(),
            matchDao = db.matchDao(),
            syncQueueDao = db.syncQueueDao(),
            userProfileLocalStore = UserProfileLocalStore(db.userProfileDao(), moshi),
            moshi = moshi,
            appContext = context,
        )
        val payload = MatchPayloadDto(
            players = listOf(
                MatchPlayerDto(seat = 1, profileName = "Deck A", life = 10),
                MatchPlayerDto(seat = 2, profileName = "Deck B", life = 5),
            ),
            winnerSeat = 1,
            durationSeconds = 120,
            tabletopType = "LIST",
        )

        val localId = repo.recordMatchOffline(payload)
        val match = db.matchDao().getByLocalId(localId)
        val queueItem = db.syncQueueDao().peekOldest()
        val queuePayload = moshi.adapter(MatchQueuePayload::class.java)
            .fromJson(queueItem?.payloadJson.orEmpty())

        assertNotNull(match)
        assertNotNull(queuePayload)
        assertEquals(match?.clientMatchId, queuePayload?.clientMatchId)
        assertEquals(match?.updatedAt, queuePayload?.updatedAt)
        assertEquals(localId, queuePayload?.localId)
        db.close()
    }

    @Test
    fun uploadMatchQueued_handles409ByMarkingSynced() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = createDb(context)
        val moshi = Moshi.Builder().build()
        val matchDao = db.matchDao()
        val payload = MatchPayloadDto(
            players = listOf(MatchPlayerDto(seat = 1, profileName = "Deck A", life = 10)),
        )
        val queuePayload = MatchQueuePayload(
            localId = "local-1",
            clientMatchId = "client-1",
            updatedAt = "2024-06-01T12:34:56.789Z",
            match = payload,
        )
        matchDao.insert(
            MatchEntity(
                localId = queuePayload.localId,
                serverId = null,
                clientMatchId = queuePayload.clientMatchId,
                createdAtEpoch = 1000L,
                updatedAt = queuePayload.updatedAt,
                status = MatchStatus.PENDING_UPLOAD,
                payloadJson = moshi.adapter(MatchPayloadDto::class.java).toJson(payload),
            )
        )
        val repo = MatchRepository(
            matchApi = FakeMatchApi(
                error = HttpException(
                    Response.error<MatchCreateResponse>(
                        409,
                        """
                        {"match":{"id":"server-123","client_match_id":"client-1","updated_at":"2024-06-01T12:34:56.789Z"}}
                        """.trimIndent()
                            .toResponseBody("application/json".toMediaType())
                    )
                )
            ),
            matchDao = matchDao,
            syncQueueDao = db.syncQueueDao(),
            userProfileLocalStore = UserProfileLocalStore(db.userProfileDao(), moshi),
            moshi = moshi,
            appContext = context,
        )

        val result = repo.uploadMatchQueued(moshi.adapter(MatchQueuePayload::class.java).toJson(queuePayload))
        val updated = matchDao.getByLocalId(queuePayload.localId)

        assertEquals(MatchRepository.MatchUploadResult.Success, result)
        assertEquals(MatchStatus.SYNCED, updated?.status)
        assertEquals("server-123", updated?.serverId)
        db.close()
    }

    private fun createDb(context: Context): AppDatabase {
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    private class FakeMatchApi(
        private val error: Throwable? = null,
    ) : MatchApi {
        override suspend fun createMatch(request: MatchCreateRequest): MatchCreateResponse {
            error?.let { throw it }
            throw UnsupportedOperationException("Not implemented for this test.")
        }
    }
}
