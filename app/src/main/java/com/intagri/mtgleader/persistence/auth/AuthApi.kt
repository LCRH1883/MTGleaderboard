package com.intagri.mtgleader.persistence.auth

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Query

interface AuthApi {
    @POST("v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthUser

    @POST("v1/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthUser

    @POST("v1/auth/forgot")
    suspend fun requestPasswordReset(@Body request: ForgotPasswordRequest)

    @GET("v1/users/me")
    suspend fun getCurrentUser(@Query("include_stats") includeStats: Boolean? = null): AuthUser

    @PATCH("v1/users/me")
    suspend fun updateProfile(@Body request: UserProfileUpdateRequest): AuthUser

    @POST("v1/users/me")
    suspend fun updateProfilePost(@Body request: UserProfileUpdateRequest): AuthUser

    @PUT("v1/users/me")
    suspend fun updateProfilePut(@Body request: UserProfileUpdateRequest): AuthUser

    @Multipart
    @POST("v1/users/me/avatar")
    suspend fun uploadAvatar(
        @Part avatar: MultipartBody.Part,
        @Part("updated_at") updatedAt: RequestBody,
    ): AuthUser

    @Multipart
    @PUT("v1/users/me/avatar")
    suspend fun uploadAvatarPut(
        @Part avatar: MultipartBody.Part,
        @Part("updated_at") updatedAt: RequestBody,
    ): AuthUser

    @POST("v1/auth/logout")
    suspend fun logout()

    @POST("v1/auth/google")
    suspend fun loginWithGoogle(@Body request: IdTokenRequest): AuthUser

    @POST("v1/auth/apple")
    suspend fun loginWithApple(@Body request: IdTokenRequest): AuthUser
}
