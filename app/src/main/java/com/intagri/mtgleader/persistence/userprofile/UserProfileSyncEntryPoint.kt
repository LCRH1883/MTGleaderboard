package com.intagri.mtgleader.persistence.userprofile

import com.intagri.mtgleader.persistence.auth.UserProfileCache
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface UserProfileSyncEntryPoint {
    fun userProfileRepository(): UserProfileRepository
    fun userAvatarStore(): UserAvatarStore
    fun userProfileStore(): UserProfileStore
    fun userProfileCache(): UserProfileCache
}
