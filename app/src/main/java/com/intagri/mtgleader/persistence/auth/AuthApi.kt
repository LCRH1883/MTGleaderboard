package com.intagri.mtgleader.persistence.auth

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {
    @POST("v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthUser

    @POST("v1/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthUser

    @GET("v1/users/me")
    suspend fun getCurrentUser(): AuthUser

    @POST("v1/auth/logout")
    suspend fun logout()

    @POST("v1/auth/google")
    suspend fun loginWithGoogle(@Body request: IdTokenRequest): AuthUser

    @POST("v1/auth/apple")
    suspend fun loginWithApple(@Body request: IdTokenRequest): AuthUser
}
