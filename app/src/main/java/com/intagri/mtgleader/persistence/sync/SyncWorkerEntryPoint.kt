package com.intagri.mtgleader.persistence.sync

import com.intagri.mtgleader.persistence.auth.AuthApi
import com.intagri.mtgleader.persistence.friends.FriendsRepository
import com.intagri.mtgleader.persistence.matches.MatchRepository
import com.intagri.mtgleader.persistence.userprofile.UserProfileLocalStore
import com.intagri.mtgleader.persistence.userprofile.UserProfileRepository
import com.squareup.moshi.Moshi
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncWorkerEntryPoint {
    fun syncQueueDao(): SyncQueueDao
    fun userProfileRepository(): UserProfileRepository
    fun userProfileLocalStore(): UserProfileLocalStore
    fun friendsRepository(): FriendsRepository
    fun matchRepository(): MatchRepository
    fun authApi(): AuthApi
    fun moshi(): Moshi
}
