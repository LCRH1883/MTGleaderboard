package com.intagri.mtgleader.persistence.auth

import retrofit2.HttpException

class AuthRepository(
    private val authApi: AuthApi,
    private val cookieJar: PersistentCookieJar,
) {
    suspend fun register(email: String, username: String, password: String): AuthUser {
        return authApi.register(RegisterRequest(email = email, username = username, password = password))
    }

    suspend fun login(email: String, password: String): AuthUser {
        return authApi.login(LoginRequest(email = email, password = password))
    }

    suspend fun loginWithGoogle(idToken: String): AuthUser {
        return authApi.loginWithGoogle(IdTokenRequest(idToken = idToken))
    }

    suspend fun loginWithApple(idToken: String): AuthUser {
        return authApi.loginWithApple(IdTokenRequest(idToken = idToken))
    }

    suspend fun getCurrentUser(): AuthUser? {
        return try {
            authApi.getCurrentUser()
        } catch (e: HttpException) {
            if (e.code() == 401) {
                null
            } else {
                throw e
            }
        }
    }

    suspend fun logout() {
        try {
            authApi.logout()
        } catch (e: HttpException) {
            if (e.code() != 401) {
                throw e
            }
        }
        cookieJar.clear()
    }
}
