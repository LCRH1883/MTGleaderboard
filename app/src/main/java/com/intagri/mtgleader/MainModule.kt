package com.intagri.mtgleader

import android.content.Context
import androidx.room.Room
import com.intagri.mtgleader.legacy.LegacyDatastore
import com.intagri.mtgleader.legacy.MigrationHelper
import com.intagri.mtgleader.persistence.*
import com.intagri.mtgleader.persistence.auth.AuthApi
import com.intagri.mtgleader.persistence.auth.AuthRepository
import com.intagri.mtgleader.persistence.auth.PersistentCookieJar
import com.intagri.mtgleader.persistence.auth.UserProfileCache
import com.intagri.mtgleader.persistence.friends.FriendDao
import com.intagri.mtgleader.persistence.friends.FriendRequestDao
import com.intagri.mtgleader.persistence.friends.FriendsApi
import com.intagri.mtgleader.persistence.friends.FriendsRepository
import com.intagri.mtgleader.persistence.gamesession.GameSessionDao
import com.intagri.mtgleader.persistence.gamesession.GameSessionRepository
import com.intagri.mtgleader.persistence.images.ImageApi
import com.intagri.mtgleader.persistence.images.ImageRepository
import com.intagri.mtgleader.persistence.images.ImageRepositoryImpl
import com.intagri.mtgleader.persistence.matches.MatchApi
import com.intagri.mtgleader.persistence.matches.MatchDao
import com.intagri.mtgleader.persistence.matches.MatchRepository
import com.intagri.mtgleader.persistence.notifications.NotificationsApi
import com.intagri.mtgleader.persistence.notifications.NotificationsRepository
import com.intagri.mtgleader.persistence.sync.SyncMetadataDao
import com.intagri.mtgleader.persistence.sync.SyncQueueDao
import com.intagri.mtgleader.persistence.stats.StatsApi
import com.intagri.mtgleader.persistence.stats.StatsRepository
import com.intagri.mtgleader.persistence.stats.local.LocalStatsDao
import com.intagri.mtgleader.persistence.stats.local.LocalStatsRepository
import com.intagri.mtgleader.persistence.userprofile.UserProfileDao
import com.intagri.mtgleader.persistence.userprofile.UserProfileLocalStore
import com.intagri.mtgleader.persistence.userprofile.UserProfileRepository
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MainModule {

    @Provides
    @Singleton
    fun providesMoshi(): Moshi {
        return Moshi.Builder().build()
    }

    @Provides
    @Singleton
    fun providesDatastore(@ApplicationContext appContext: Context): Datastore {
        return DatastoreImpl(appContext)
    }

    /**
     * For migrations from previous app
     */
    @Provides
    @Singleton
    fun providesLegacyDatastore(
        @ApplicationContext appContext: Context,
        moshi: Moshi
    ): LegacyDatastore {
        return LegacyDatastore(appContext, moshi)
    }

    @Provides
    @Singleton
    fun providesDatabase(@ApplicationContext appContext: Context): AppDatabase {
        return Room.databaseBuilder(
            appContext.applicationContext,
            AppDatabase::class.java,
            "template_database"
        )
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7
            )
            .build()
    }

    @Provides
    @Singleton
    fun providesMigrationHelper(
        appDatabase: AppDatabase,
        datastore: Datastore,
        legacyDatastore: LegacyDatastore
    ): MigrationHelper {
        return MigrationHelper(datastore, legacyDatastore, appDatabase)
    }

    @Provides
    @Singleton
    fun providesUserProfileDao(appDatabase: AppDatabase): UserProfileDao {
        return appDatabase.userProfileDao()
    }

    @Provides
    @Singleton
    fun providesFriendDao(appDatabase: AppDatabase): FriendDao {
        return appDatabase.friendDao()
    }

    @Provides
    @Singleton
    fun providesFriendRequestDao(appDatabase: AppDatabase): FriendRequestDao {
        return appDatabase.friendRequestDao()
    }

    @Provides
    @Singleton
    fun providesMatchDao(appDatabase: AppDatabase): MatchDao {
        return appDatabase.matchDao()
    }

    @Provides
    @Singleton
    fun providesGameSessionDao(appDatabase: AppDatabase): GameSessionDao {
        return appDatabase.gameSessionDao()
    }

    @Provides
    @Singleton
    fun providesSyncQueueDao(appDatabase: AppDatabase): SyncQueueDao {
        return appDatabase.syncQueueDao()
    }

    @Provides
    @Singleton
    fun providesSyncMetadataDao(appDatabase: AppDatabase): SyncMetadataDao {
        return appDatabase.syncMetadataDao()
    }

    @Provides
    @Singleton
    fun providesLocalStatsDao(appDatabase: AppDatabase): LocalStatsDao {
        return appDatabase.localStatsDao()
    }

    @Provides
    @Singleton
    fun provideImageApi(): ImageApi {
        return Retrofit.Builder()
            .baseUrl("https://www.google.com") //using dynamic urls. Need a dummy base url
            .build()
            .create(ImageApi::class.java)
    }

    @Provides
    @Singleton
    fun providesGameRepository(datastore: Datastore): GameRepository {
        return GameRepositoryImpl(datastore)
    }

    @Provides
    @Singleton
    fun providesProfileRepository(database: AppDatabase, datastore: Datastore): ProfileRepository {
        return ProfileRepositoryImpl(database, datastore)
    }

    @Provides
    @Singleton
    fun providesImageRepository(
        @ApplicationContext appContext: Context,
        imageApi: ImageApi,
    ): ImageRepository {
        return ImageRepositoryImpl(appContext, imageApi)
    }

    @Provides
    @Singleton
    fun providesAuthCookieJar(
        @ApplicationContext appContext: Context,
        moshi: Moshi,
    ): PersistentCookieJar {
        return PersistentCookieJar(appContext, moshi)
    }

    @Provides
    @Singleton
    fun providesAuthOkHttpClient(cookieJar: PersistentCookieJar): OkHttpClient {
        return OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .build()
    }

    @Provides
    @Singleton
    fun providesAuthApi(okHttpClient: OkHttpClient, moshi: Moshi): AuthApi {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(AuthApi::class.java)
    }

    @Provides
    @Singleton
    fun providesAuthRepository(
        authApi: AuthApi,
        cookieJar: PersistentCookieJar,
        userProfileCache: UserProfileCache,
        userProfileLocalStore: UserProfileLocalStore,
    ): AuthRepository {
        return AuthRepository(authApi, cookieJar, userProfileCache, userProfileLocalStore)
    }

    @Provides
    @Singleton
    fun providesFriendsApi(okHttpClient: OkHttpClient, moshi: Moshi): FriendsApi {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FriendsApi::class.java)
    }

    @Provides
    @Singleton
    fun providesNotificationsApi(okHttpClient: OkHttpClient, moshi: Moshi): NotificationsApi {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(NotificationsApi::class.java)
    }

    @Provides
    @Singleton
    fun providesFriendsRepository(
        friendsApi: FriendsApi,
        appDatabase: AppDatabase,
        friendDao: FriendDao,
        friendRequestDao: FriendRequestDao,
        syncQueueDao: SyncQueueDao,
        syncMetadataDao: SyncMetadataDao,
        moshi: Moshi,
        @ApplicationContext appContext: Context,
    ): FriendsRepository {
        return FriendsRepository(
            friendsApi,
            appDatabase,
            friendDao,
            friendRequestDao,
            syncQueueDao,
            syncMetadataDao,
            moshi,
            appContext,
        )
    }

    @Provides
    @Singleton
    fun providesNotificationsRepository(
        notificationsApi: NotificationsApi,
        userProfileLocalStore: UserProfileLocalStore,
        datastore: Datastore,
    ): NotificationsRepository {
        return NotificationsRepository(
            notificationsApi,
            userProfileLocalStore,
            datastore,
        )
    }

    @Provides
    @Singleton
    fun providesMatchApi(okHttpClient: OkHttpClient, moshi: Moshi): MatchApi {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(MatchApi::class.java)
    }

    @Provides
    @Singleton
    fun providesMatchRepository(
        matchApi: MatchApi,
        matchDao: MatchDao,
        syncQueueDao: SyncQueueDao,
        userProfileLocalStore: UserProfileLocalStore,
        gameSessionRepository: GameSessionRepository,
        datastore: Datastore,
        moshi: Moshi,
        @ApplicationContext appContext: Context,
    ): MatchRepository {
        return MatchRepository(
            matchApi,
            matchDao,
            syncQueueDao,
            userProfileLocalStore,
            gameSessionRepository,
            datastore,
            moshi,
            appContext,
        )
    }

    @Provides
    @Singleton
    fun providesStatsApi(okHttpClient: OkHttpClient, moshi: Moshi): StatsApi {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(StatsApi::class.java)
    }

    @Provides
    @Singleton
    fun providesStatsRepository(statsApi: StatsApi): StatsRepository {
        return StatsRepository(statsApi)
    }

    @Provides
    @Singleton
    fun providesLocalStatsRepository(localStatsDao: LocalStatsDao): LocalStatsRepository {
        return LocalStatsRepository(localStatsDao)
    }

    @Provides
    @Singleton
    fun providesUserProfileRepository(
        @ApplicationContext appContext: Context,
        authApi: AuthApi,
        userProfileCache: UserProfileCache,
    ): UserProfileRepository {
        return UserProfileRepository(appContext, authApi, userProfileCache)
    }
}
