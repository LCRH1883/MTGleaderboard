package com.intagri.mtgleader.persistence.auth

import com.intagri.mtgleader.persistence.userprofile.UserProfileLocalStore
import retrofit2.HttpException

class AuthRepository(
    private val authApi: AuthApi,
    private val cookieJar: PersistentCookieJar,
    private val userProfileCache: UserProfileCache,
    private val userProfileLocalStore: UserProfileLocalStore,
) {
    suspend fun register(email: String, username: String, password: String): AuthUser {
        val user = authApi.register(RegisterRequest(email = email, username = username, password = password))
        userProfileCache.setUser(user)
        userProfileLocalStore.upsert(user)
        return user
    }

    suspend fun login(email: String, password: String): AuthUser {
        val user = authApi.login(LoginRequest(email = email, password = password))
        userProfileCache.setUser(user)
        userProfileLocalStore.upsert(user)
        return user
    }

    suspend fun requestPasswordReset(email: String) {
        authApi.requestPasswordReset(ForgotPasswordRequest(email = email))
    }

    suspend fun loginWithGoogle(idToken: String): AuthUser {
        val user = authApi.loginWithGoogle(IdTokenRequest(idToken = idToken))
        userProfileCache.setUser(user)
        userProfileLocalStore.upsert(user)
        return user
    }

    suspend fun loginWithApple(idToken: String): AuthUser {
        val user = authApi.loginWithApple(IdTokenRequest(idToken = idToken))
        userProfileCache.setUser(user)
        userProfileLocalStore.upsert(user)
        return user
    }

    suspend fun getCurrentUser(includeStats: Boolean = false): AuthUser? {
        return try {
            val user = authApi.getCurrentUser(includeStats = if (includeStats) true else null)
            userProfileCache.setUser(user)
            userProfileLocalStore.upsert(user)
            user
        } catch (e: HttpException) {
            if (e.code() == 401) {
                userProfileCache.setUser(null)
                userProfileLocalStore.clear()
                null
            } else {
                throw e
            }
        }
    }

    fun getCachedUser(): AuthUser? = userProfileCache.getUser()

    suspend fun logout() {
        try {
            authApi.logout()
        } catch (e: HttpException) {
            if (e.code() != 401) {
                throw e
            }
        }
        cookieJar.clear()
        userProfileCache.setUser(null)
        userProfileLocalStore.clear()
    }
}
