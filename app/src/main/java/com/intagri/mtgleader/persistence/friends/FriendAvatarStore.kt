package com.intagri.mtgleader.persistence.friends

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FriendAvatarStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getCachedAvatarUri(userId: String?, updatedAt: String?): String? {
        if (userId.isNullOrBlank()) {
            return null
        }
        val entry = prefs.getString(key(userId), null) ?: return null
        val parts = entry.split("|", limit = 2)
        val storedUpdatedAt = parts.getOrNull(0).orEmpty()
        val storedUri = parts.getOrNull(1)
        if (storedUri.isNullOrBlank()) {
            clear(userId, null)
            return null
        }
        if (!updatedAt.isNullOrBlank() && storedUpdatedAt != updatedAt) {
            clear(userId, storedUri)
            return null
        }
        if (storedUri.startsWith("file:")) {
            val path = Uri.parse(storedUri).path
            if (path.isNullOrBlank() || !File(path).exists()) {
                clear(userId, storedUri)
                return null
            }
        }
        return storedUri
    }

    fun saveCachedAvatar(userId: String?, updatedAt: String?, uri: String) {
        if (userId.isNullOrBlank()) {
            return
        }
        val value = "${updatedAt.orEmpty()}|$uri"
        prefs.edit().putString(key(userId), value).apply()
    }

    fun clear(userId: String?, uri: String?) {
        if (!userId.isNullOrBlank()) {
            prefs.edit().remove(key(userId)).apply()
        }
        if (!uri.isNullOrBlank() && uri.startsWith("file:")) {
            val path = Uri.parse(uri).path
            if (!path.isNullOrBlank()) {
                runCatching { File(path).delete() }
            }
        }
    }

    private fun key(userId: String) = "avatar_$userId"

    private companion object {
        private const val PREFS_NAME = "friend_avatar_store"
    }
}
