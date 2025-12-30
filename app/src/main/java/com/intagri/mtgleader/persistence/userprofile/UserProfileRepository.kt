package com.intagri.mtgleader.persistence.userprofile

import android.content.Context
import android.net.Uri
import android.util.Log
import com.intagri.mtgleader.persistence.auth.AuthApi
import com.intagri.mtgleader.persistence.auth.AuthUser
import com.intagri.mtgleader.persistence.auth.UserProfileUpdateRequest
import com.intagri.mtgleader.persistence.auth.UserProfileCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class UserProfileRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val authApi: AuthApi,
    private val userProfileCache: UserProfileCache,
) {
    suspend fun updateDisplayName(displayName: String?): AuthUser {
        val normalized = displayName?.trim() ?: ""
        val user = updateWithTimestampRetry("update display name") { updatedAt ->
            updateProfileWithFallback(
                UserProfileUpdateRequest(
                    displayName = normalized,
                    updatedAt = updatedAt
                )
            )
        }
        userProfileCache.setUser(user)
        return user
    }

    suspend fun prepareAvatarFile(uri: String): File {
        return withContext(Dispatchers.IO) {
            val outputDir = File(appContext.filesDir, "avatars")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }
            val outputFile = File(outputDir, "local_avatar.jpg")
            val sourceUri = Uri.parse(uri)
            copyUriToFile(sourceUri, outputFile)
        }
    }

    suspend fun uploadAvatarFile(file: File): AuthUser {
        return withContext(Dispatchers.IO) {
            val avatarBody = file.asRequestBody("image/jpeg".toMediaType())
            val avatarPart = MultipartBody.Part.createFormData("avatar", file.name, avatarBody)
            val user = updateWithTimestampRetry("upload avatar") { updatedAt ->
                val updatedAtPart = updatedAt.toRequestBody("text/plain".toMediaType())
                uploadAvatarWithFallback(avatarPart, updatedAtPart)
            }
            userProfileCache.setUser(user)
            user
        }
    }

    private fun openInputStream(
        resolver: android.content.ContentResolver,
        uri: Uri
    ): java.io.InputStream? {
        return if (uri.scheme == "file") {
            uri.path?.let { FileInputStream(File(it)) }
        } else {
            resolver.openInputStream(uri)
        }
    }

    private fun copyUriToFile(uri: Uri, outputFile: File): File {
        val resolver = appContext.contentResolver
        openInputStream(resolver, uri)?.use { input ->
            FileOutputStream(outputFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalArgumentException("Unable to read avatar content.")
        return outputFile
    }


    private fun newUpdatedAtTimestamp(): String {
        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val cached = userProfileCache.getUser()?.updatedAt
        val cachedInstant = cached?.let { parseInstant(it) }
        val resolved = if (cachedInstant != null && cachedInstant.isAfter(now)) {
            cachedInstant.plusMillis(1)
        } else {
            now
        }
        return TIMESTAMP_FORMATTER.format(resolved)
    }

    private fun parseInstant(value: String): Instant? {
        return try {
            Instant.parse(value)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun updateWithTimestampRetry(
        actionLabel: String,
        block: suspend (updatedAt: String) -> AuthUser
    ): AuthUser {
        val updatedAt = newUpdatedAtTimestamp()
        return try {
            block(updatedAt)
        } catch (e: HttpException) {
            if (e.code() != 409) {
                logHttpError(actionLabel, updatedAt, e)
                throw e
            }
            val refreshed = authApi.getCurrentUser()
            userProfileCache.setUser(refreshed)
            val retryUpdatedAt = newUpdatedAtTimestamp()
            try {
                block(retryUpdatedAt)
            } catch (retry: HttpException) {
                logHttpError(actionLabel, retryUpdatedAt, retry)
                throw retry
            }
        }
    }

    private fun logHttpError(actionLabel: String, updatedAt: String, error: HttpException) {
        val body = runCatching { error.response()?.errorBody()?.string() }.getOrNull()
        Log.e(
            TAG,
            "$actionLabel failed. status=${error.code()} updated_at=$updatedAt body=${body.orEmpty()}"
        )
    }

    private suspend fun updateProfileWithFallback(request: UserProfileUpdateRequest): AuthUser {
        return try {
            authApi.updateProfile(request)
        } catch (e: HttpException) {
            if (!e.isMethodFallbackCandidate()) {
                throw e
            }
            try {
                authApi.updateProfilePost(request)
            } catch (postError: HttpException) {
                if (!postError.isMethodFallbackCandidate()) {
                    throw postError
                }
                authApi.updateProfilePut(request)
            }
        }
    }

    private suspend fun uploadAvatarWithFallback(
        avatarPart: MultipartBody.Part,
        updatedAtPart: okhttp3.RequestBody,
    ): AuthUser {
        return try {
            authApi.uploadAvatar(avatarPart, updatedAtPart)
        } catch (e: HttpException) {
            if (!e.isMethodFallbackCandidate()) {
                throw e
            }
            authApi.uploadAvatarPut(avatarPart, updatedAtPart)
        }
    }

    private fun HttpException.isMethodFallbackCandidate(): Boolean {
        return code() == 404 || code() == 405
    }

    companion object {
        private const val TAG = "UserProfileRepository"
        private const val TIMESTAMP_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        private val TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern(TIMESTAMP_FORMAT)
            .withZone(ZoneOffset.UTC)
    }
}
