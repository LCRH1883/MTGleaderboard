package com.intagri.mtgleader.persistence.sync

import android.content.Context
import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.intagri.mtgleader.TestApplication
import com.intagri.mtgleader.persistence.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O], application = TestApplication::class)
class SyncQueueDaoTest {
    @Test
    fun peekOldest_returnsOldestByCreatedAt() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.syncQueueDao()
            dao.enqueue(
                SyncQueueEntity(
                    entityType = SyncEntityType.PROFILE,
                    action = SyncAction.UPDATE_DISPLAY_NAME,
                    payloadJson = "{}",
                    createdAt = 2000L,
                )
            )
            dao.enqueue(
                SyncQueueEntity(
                    entityType = SyncEntityType.PROFILE,
                    action = SyncAction.UPDATE_DISPLAY_NAME,
                    payloadJson = "{}",
                    createdAt = 1000L,
                )
            )
            val oldest = dao.peekOldest()
            assertEquals(1000L, oldest?.createdAt)
        } finally {
            db.close()
        }
    }
}
