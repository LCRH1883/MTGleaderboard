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
import com.intagri.mtgleader.persistence.friends.FriendsApi
import com.intagri.mtgleader.persistence.friends.FriendsRepository
import com.intagri.mtgleader.persistence.images.ImageApi
import com.intagri.mtgleader.persistence.images.ImageRepository
import com.intagri.mtgleader.persistence.images.ImageRepositoryImpl
import com.intagri.mtgleader.persistence.stats.StatsApi
import com.intagri.mtgleader.persistence.stats.StatsRepository
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
            .addMigrations(AppDatabase.MIGRATION_1_2)
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
    ): AuthRepository {
        return AuthRepository(authApi, cookieJar, userProfileCache)
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
    fun providesFriendsRepository(friendsApi: FriendsApi): FriendsRepository {
        return FriendsRepository(friendsApi)
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
    fun providesUserProfileRepository(
        @ApplicationContext appContext: Context,
        authApi: AuthApi,
        userProfileCache: UserProfileCache,
    ): UserProfileRepository {
        return UserProfileRepository(appContext, authApi, userProfileCache)
    }
}
