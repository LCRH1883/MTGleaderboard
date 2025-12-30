package com.intagri.mtgleader.persistence.userprofile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.annotation.VisibleForTesting
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
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.min

class UserProfileRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val authApi: AuthApi,
    private val userProfileCache: UserProfileCache,
) {
    suspend fun updateDisplayName(displayName: String?): AuthUser {
        val normalized = displayName?.trim()?.takeIf { it.isNotEmpty() }
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

    suspend fun uploadAvatar(uri: String): AuthUser {
        return withContext(Dispatchers.IO) {
            val file = createAvatarFile(Uri.parse(uri))
            try {
                val avatarBody = file.asRequestBody("image/jpeg".toMediaType())
                val avatarPart = MultipartBody.Part.createFormData("avatar", file.name, avatarBody)
                val user = updateWithTimestampRetry("upload avatar") { updatedAt ->
                    val updatedAtPart = updatedAt.toRequestBody("text/plain".toMediaType())
                    uploadAvatarWithFallback(avatarPart, updatedAtPart)
                }
                userProfileCache.setUser(user)
                user
            } finally {
                file.delete()
            }
        }
    }

    @VisibleForTesting
    internal fun createAvatarFile(uri: Uri): File {
        val bitmap = decodeSampledBitmap(uri, AVATAR_SIZE, AVATAR_SIZE)
            ?: throw IllegalArgumentException("Unable to decode avatar image.")
        val square = cropToSquare(bitmap)
        val scaled = if (square.width != AVATAR_SIZE || square.height != AVATAR_SIZE) {
            Bitmap.createScaledBitmap(square, AVATAR_SIZE, AVATAR_SIZE, true)
        } else {
            square
        }
        val outputFile = File.createTempFile("avatar_upload_", ".jpg", appContext.cacheDir)
        FileOutputStream(outputFile).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, AVATAR_QUALITY, out)
        }
        return outputFile
    }

    private fun decodeSampledBitmap(uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        val resolver = appContext.contentResolver
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        } ?: return null
        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
            return null
        }
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(boundsOptions, reqWidth, reqHeight)
        }
        return resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            var halfHeight = height / 2
            var halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun cropToSquare(bitmap: Bitmap): Bitmap {
        val size = min(bitmap.width, bitmap.height)
        val left = ((bitmap.width - size) / 2.0f).toInt()
        val top = ((bitmap.height - size) / 2.0f).toInt()
        return Bitmap.createBitmap(bitmap, left, top, size, size)
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
        private const val AVATAR_SIZE = 512
        private const val AVATAR_QUALITY = 92
        private const val TIMESTAMP_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        private val TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern(TIMESTAMP_FORMAT)
            .withZone(ZoneOffset.UTC)
    }
}
