package com.pelonot.data.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pelonot.data.remote.SyncOutcome
import com.pelonot.di.ServiceLocator
import java.util.concurrent.TimeUnit

/** Retries a profile edit with the latest local row after a failed upload. */
class ProfileSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val userId = inputData.getInt(KEY_USER_ID, -1)
        if (userId < 0) return Result.failure()
        val user = ServiceLocator.userRepository.getUser(userId) ?: return Result.success()
        return when (ServiceLocator.syncRepository.syncProfile(user)) {
            is SyncOutcome.Success -> Result.success()
            is SyncOutcome.Failed -> Result.retry()
            is SyncOutcome.Rejected -> Result.failure()
            SyncOutcome.Disabled -> Result.success()
        }
    }

    companion object {
        private const val KEY_USER_ID = "user_id"

        fun enqueue(context: Context, userId: Int) {
            val request = OneTimeWorkRequestBuilder<ProfileSyncWorker>()
                .setInputData(Data.Builder().putInt(KEY_USER_ID, userId).build())
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            // A newer edit replaces the retry; doWork always reads the latest row.
            WorkManager.getInstance(context).enqueueUniqueWork(
                "sync-profile-settings-$userId", ExistingWorkPolicy.REPLACE, request
            )
        }
    }
}
