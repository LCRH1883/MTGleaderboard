package com.intagri.mtgleader.persistence

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.intagri.mtgleader.persistence.entities.CounterTemplateEntity
import com.intagri.mtgleader.persistence.entities.PlayerProfileCounterTemplateCrossRefEntity
import com.intagri.mtgleader.persistence.entities.PlayerProfileEntity
import com.intagri.mtgleader.persistence.friends.FriendDao
import com.intagri.mtgleader.persistence.friends.FriendEntity
import com.intagri.mtgleader.persistence.friends.FriendRequestDao
import com.intagri.mtgleader.persistence.friends.FriendRequestEntity
import com.intagri.mtgleader.persistence.matches.MatchDao
import com.intagri.mtgleader.persistence.matches.MatchEntity
import com.intagri.mtgleader.persistence.sync.SyncMetadataDao
import com.intagri.mtgleader.persistence.sync.SyncMetadataEntity
import com.intagri.mtgleader.persistence.sync.SyncQueueDao
import com.intagri.mtgleader.persistence.sync.SyncQueueEntity
import com.intagri.mtgleader.persistence.userprofile.UserProfileDao
import com.intagri.mtgleader.persistence.userprofile.UserProfileEntity

@Database(
    entities = [
        PlayerProfileEntity::class,
        CounterTemplateEntity::class,
        PlayerProfileCounterTemplateCrossRefEntity::class,
        UserProfileEntity::class,
        FriendEntity::class,
        FriendRequestEntity::class,
        MatchEntity::class,
        SyncQueueEntity::class,
        SyncMetadataEntity::class,
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun templateDao(): TemplateDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun friendDao(): FriendDao
    abstract fun friendRequestDao(): FriendRequestDao
    abstract fun matchDao(): MatchDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun syncMetadataDao(): SyncMetadataDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE player_profiles ADD COLUMN life_counter_id INTEGER")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS user_profile (
                        id TEXT NOT NULL PRIMARY KEY,
                        email TEXT NOT NULL,
                        username TEXT NOT NULL,
                        displayName TEXT,
                        avatarPath TEXT,
                        avatarUpdatedAt TEXT,
                        updatedAt TEXT NOT NULL,
                        statsSummaryJson TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS friends (
                        userId TEXT NOT NULL PRIMARY KEY,
                        username TEXT,
                        displayName TEXT,
                        avatarPath TEXT,
                        avatarUpdatedAt TEXT,
                        updatedAt TEXT,
                        lastSeenAt TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS friend_requests (
                        requestId TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL,
                        username TEXT,
                        displayName TEXT,
                        avatarPath TEXT,
                        avatarUpdatedAt TEXT,
                        status TEXT NOT NULL,
                        createdAt TEXT NOT NULL,
                        updatedAt TEXT NOT NULL,
                        resolvedAt TEXT,
                        isPendingSync INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_queue (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        entityType TEXT NOT NULL,
                        action TEXT NOT NULL,
                        payloadJson TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        attemptCount INTEGER NOT NULL DEFAULT 0,
                        lastError TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_metadata (
                        key TEXT NOT NULL PRIMARY KEY,
                        value TEXT NOT NULL,
                        updatedAtEpoch INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS matches (
                        localId TEXT NOT NULL PRIMARY KEY,
                        serverId TEXT,
                        clientMatchId TEXT NOT NULL,
                        createdAtEpoch INTEGER NOT NULL,
                        updatedAt TEXT NOT NULL,
                        status TEXT NOT NULL,
                        payloadJson TEXT NOT NULL,
                        lastError TEXT,
                        syncedAtEpoch INTEGER
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
