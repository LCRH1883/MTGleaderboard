package com.intagri.mtgleader.persistence.notifications

import com.google.firebase.messaging.FirebaseMessaging
import com.intagri.mtgleader.persistence.Datastore
import com.intagri.mtgleader.persistence.userprofile.UserProfileLocalStore
import kotlinx.coroutines.suspendCancellableCoroutine
import retrofit2.HttpException
import kotlin.coroutines.resume

class NotificationsRepository(
    private val notificationsApi: NotificationsApi,
    private val userProfileLocalStore: UserProfileLocalStore,
    private val datastore: Datastore,
) {
    suspend fun syncTokenIfNeeded() {
        val user = userProfileLocalStore.getEntity() ?: return
        val token = fetchFcmToken() ?: return
        val currentToken = datastore.registeredFcmToken
        val currentUserId = datastore.registeredFcmTokenUserId
        if (token == currentToken && currentUserId == user.id) {
            return
        }
        runCatching { registerToken(token, user.id) }
    }

    suspend fun registerToken(token: String) {
        val user = userProfileLocalStore.getEntity() ?: return
        runCatching { registerToken(token, user.id) }
    }

    suspend fun deleteRegisteredToken() {
        val token = datastore.registeredFcmToken ?: return
        try {
            notificationsApi.deleteToken(token)
            datastore.registeredFcmToken = null
            datastore.registeredFcmTokenUserId = null
        } catch (e: HttpException) {
            if (e.code() == 401 || e.code() == 400) {
                datastore.registeredFcmToken = null
                datastore.registeredFcmTokenUserId = null
            }
        } catch (_: Exception) {
            // Keep local token so we can retry later.
        }
    }

    private suspend fun registerToken(token: String, userId: String) {
        try {
            notificationsApi.registerToken(
                NotificationTokenRequest(
                    token = token,
                    platform = PLATFORM_ANDROID,
                )
            )
            datastore.registeredFcmToken = token
            datastore.registeredFcmTokenUserId = userId
        } catch (e: HttpException) {
            if (e.code() != 401) {
                throw e
            }
        }
    }

    private suspend fun fetchFcmToken(): String? {
        return suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    val token = if (task.isSuccessful) task.result else null
                    if (continuation.isActive) {
                        continuation.resume(token)
                    }
                }
        }
    }

    companion object {
        private const val PLATFORM_ANDROID = "android"
    }
}
