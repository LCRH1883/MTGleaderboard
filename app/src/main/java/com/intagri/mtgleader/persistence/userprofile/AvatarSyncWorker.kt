package com.intagri.mtgleader.persistence.userprofile

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import retrofit2.HttpException
import java.io.File
import java.time.Instant

class AvatarSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            UserProfileSyncEntryPoint::class.java
        )
        val userProfileRepository = entryPoint.userProfileRepository()
        val userAvatarStore = entryPoint.userAvatarStore()
        val userProfileCache = entryPoint.userProfileCache()
        val localUri = userAvatarStore.localAvatarUri ?: return Result.success()
        if (isLocalAvatarStale()) {
            userAvatarStore.clear()
            return Result.success()
        }
        val resolved = resolveFile(localUri) ?: run {
            userAvatarStore.clear()
            return Result.success()
        }
        return try {
            userProfileRepository.uploadAvatarFile(resolved.file)
            if (resolved.isTemp) {
                resolved.file.delete()
            }
            Result.success()
        } catch (e: HttpException) {
            Log.e(TAG, "Avatar sync HTTP ${e.code()}", e)
            if (e.code() == 400 || e.code() == 401) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Avatar sync failed", e)
            Result.retry()
        }
    }

    private fun resolveFile(uriValue: String): ResolvedFile? {
        val uri = Uri.parse(uriValue)
        if (uri.scheme == "file") {
            val path = uri.path ?: return null
            val file = File(path)
            return if (file.exists()) ResolvedFile(file, false) else null
        }
        if (uri.scheme == "content") {
            val file = File.createTempFile("avatar_sync_", ".jpg", applicationContext.cacheDir)
            applicationContext.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            return ResolvedFile(file, true)
        }
        return null
    }

    private fun isLocalAvatarStale(): Boolean {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            UserProfileSyncEntryPoint::class.java
        )
        val userAvatarStore = entryPoint.userAvatarStore()
        val userProfileCache = entryPoint.userProfileCache()
        val localUpdatedAt = userAvatarStore.localAvatarUpdatedAt ?: return false
        val cachedUpdatedAt = userProfileCache.getUser()?.avatarUpdatedAt ?: return false
        val localInstant = parseInstant(localUpdatedAt) ?: return false
        val cachedInstant = parseInstant(cachedUpdatedAt) ?: return false
        return cachedInstant.isAfter(localInstant)
    }

    private fun parseInstant(value: String): Instant? {
        return try {
            Instant.parse(value)
        } catch (_: Exception) {
            null
        }
    }

    private data class ResolvedFile(
        val file: File,
        val isTemp: Boolean,
    )

    companion object {
        private const val TAG = "AvatarSyncWorker"
    }
}
