package com.intagri.mtgleader.persistence.userprofile

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserProfileStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var pendingDisplayName: String?
        get() = prefs.getString(KEY_PENDING_DISPLAY_NAME, null)
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_PENDING_DISPLAY_NAME).apply()
            } else {
                prefs.edit().putString(KEY_PENDING_DISPLAY_NAME, value).apply()
            }
        }

    fun clearDisplayName() {
        prefs.edit().remove(KEY_PENDING_DISPLAY_NAME).apply()
    }

    private companion object {
        private const val PREFS_NAME = "user_profile_store"
        private const val KEY_PENDING_DISPLAY_NAME = "pending_display_name"
    }
}
