package com.intagri.mtgleader.persistence.userprofile

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import retrofit2.HttpException

class DisplayNameSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            UserProfileSyncEntryPoint::class.java
        )
        val userProfileRepository = entryPoint.userProfileRepository()
        val userProfileStore = entryPoint.userProfileStore()
        val pending = userProfileStore.pendingDisplayName ?: return Result.success()
        return try {
            userProfileRepository.updateDisplayName(pending)
            userProfileStore.clearDisplayName()
            Result.success()
        } catch (e: HttpException) {
            Log.e(TAG, "Display name sync HTTP ${e.code()}", e)
            when (e.code()) {
                400 -> {
                    userProfileStore.clearDisplayName()
                    Result.success()
                }
                401 -> Result.success()
                else -> Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Display name sync failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "DisplayNameSyncWorker"
    }
}
