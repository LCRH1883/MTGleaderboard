package com.intagri.mtgleader.persistence.userprofile

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserAvatarStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var localAvatarUri: String?
        get() = prefs.getString(KEY_LOCAL_AVATAR_URI, null)
        set(value) {
            if (value.isNullOrBlank()) {
                prefs.edit().remove(KEY_LOCAL_AVATAR_URI).apply()
            } else {
                prefs.edit().putString(KEY_LOCAL_AVATAR_URI, value).apply()
            }
        }

    var localAvatarUpdatedAt: String?
        get() = prefs.getString(KEY_LOCAL_AVATAR_UPDATED_AT, null)
        set(value) {
            if (value.isNullOrBlank()) {
                prefs.edit().remove(KEY_LOCAL_AVATAR_UPDATED_AT).apply()
            } else {
                prefs.edit().putString(KEY_LOCAL_AVATAR_UPDATED_AT, value).apply()
            }
        }

    fun clear() {
        prefs.edit().remove(KEY_LOCAL_AVATAR_URI).apply()
        prefs.edit().remove(KEY_LOCAL_AVATAR_UPDATED_AT).apply()
    }

    private companion object {
        private const val PREFS_NAME = "user_avatar_store"
        private const val KEY_LOCAL_AVATAR_URI = "local_avatar_uri"
        private const val KEY_LOCAL_AVATAR_UPDATED_AT = "local_avatar_updated_at"
    }
}
