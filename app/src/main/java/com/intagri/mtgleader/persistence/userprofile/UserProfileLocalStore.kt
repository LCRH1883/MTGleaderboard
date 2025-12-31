package com.intagri.mtgleader.persistence.userprofile

import com.intagri.mtgleader.BuildConfig
import com.intagri.mtgleader.persistence.auth.AuthUser
import com.intagri.mtgleader.persistence.stats.StatsSummaryDto
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserProfileLocalStore @Inject constructor(
    private val userProfileDao: UserProfileDao,
    moshi: Moshi,
) {
    private val statsAdapter: JsonAdapter<StatsSummaryDto> = moshi.adapter(StatsSummaryDto::class.java)

    fun observe(): Flow<AuthUser?> {
        return userProfileDao.get().map { entity ->
            entity?.toAuthUser(statsAdapter)
        }
    }

    suspend fun getEntity(): UserProfileEntity? = userProfileDao.getOnce()

    suspend fun upsert(user: AuthUser) {
        userProfileDao.upsert(user.toEntity(statsAdapter))
    }

    suspend fun updateDisplayName(displayName: String?, updatedAt: String) {
        val current = userProfileDao.getOnce()
        if (current == null) {
            return
        }
        userProfileDao.upsert(
            current.copy(
                displayName = displayName,
                updatedAt = updatedAt,
            )
        )
    }

    suspend fun updateAvatar(avatarPath: String?, updatedAt: String) {
        val current = userProfileDao.getOnce() ?: return
        userProfileDao.upsert(
            current.copy(
                avatarPath = avatarPath,
                avatarUpdatedAt = updatedAt,
                updatedAt = updatedAt,
            )
        )
    }

    suspend fun clear() {
        userProfileDao.clear()
    }

    suspend fun updateStatsSummary(summary: StatsSummaryDto) {
        val current = userProfileDao.getOnce() ?: return
        val statsJson = runCatching { statsAdapter.toJson(summary) }.getOrNull()
        userProfileDao.upsert(
            current.copy(statsSummaryJson = statsJson)
        )
    }

    private fun UserProfileEntity.toAuthUser(
        adapter: JsonAdapter<StatsSummaryDto>,
    ): AuthUser {
        val stats = statsSummaryJson?.let { json ->
            runCatching { adapter.fromJson(json) }.getOrNull()
        }
        val resolvedAvatar = resolveAvatarUrl(avatarPath, avatarUpdatedAt)
        return AuthUser(
            id = id,
            email = email,
            username = username,
            displayName = displayName,
            avatar = avatarPath,
            avatarUrl = resolvedAvatar,
            avatarUpdatedAt = avatarUpdatedAt,
            createdAt = updatedAt,
            updatedAt = updatedAt,
            statsSummary = stats,
        )
    }

    private fun AuthUser.toEntity(
        adapter: JsonAdapter<StatsSummaryDto>,
    ): UserProfileEntity {
        val statsJson = statsSummary?.let { summary ->
            runCatching { adapter.toJson(summary) }.getOrNull()
        }
        val resolvedUpdatedAt = updatedAt ?: createdAt
        return UserProfileEntity(
            id = id,
            email = email,
            username = username,
            displayName = displayName,
            avatarPath = avatar ?: avatarUrl,
            avatarUpdatedAt = avatarUpdatedAt,
            updatedAt = resolvedUpdatedAt,
            statsSummaryJson = statsJson,
        )
    }

    private fun resolveAvatarUrl(path: String?, updatedAt: String?): String? {
        if (path.isNullOrBlank()) {
            return null
        }
        if (path.startsWith("http") || path.startsWith("file:") || path.startsWith("content:")) {
            return path
        }
        val cacheBuster = updatedAt?.let { toEpochSeconds(it) } ?: (System.currentTimeMillis() / 1000)
        return BuildConfig.API_BASE_URL.trimEnd('/') + "/app/avatars/" + path + "?v=" + cacheBuster
    }

    private fun toEpochSeconds(value: String): Long {
        return try {
            Instant.parse(value).epochSecond
        } catch (_: Exception) {
            System.currentTimeMillis() / 1000
        }
    }
}
