package com.intagri.mtgleader.persistence.auth

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AuthUser(
    val id: String,
    val email: String,
    val username: String,
    @Json(name = "created_at")
    val createdAt: String,
)

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    val email: String,
    val username: String,
    val password: String,
)

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val email: String,
    val password: String,
)

@JsonClass(generateAdapter = true)
data class IdTokenRequest(
    @Json(name = "id_token")
    val idToken: String,
)
