package com.pelonot.data.worker

import android.content.Context
import android.util.Log
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

/**
 * Uploads a completed ride to Supabase in the background.
 *
 * The previous version discarded the sync result — `syncRepository.syncWorkout(...)`
 * was called for its side effect and `Result.success()` returned unconditionally
 * — so a failed upload reported success and was never retried. It also had no
 * network constraint and no retry ceiling.
 */
class WorkoutSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val workoutId = inputData.getString(KEY_WORKOUT_ID)
            ?: return Result.failure()

        val workoutRepository = ServiceLocator.workoutRepository
        val syncRepository = ServiceLocator.syncRepository

        val workout = workoutRepository.getWorkout(workoutId) ?: run {
            // The ride was discarded before the upload ran. Nothing to retry.
            Log.i(TAG, "Workout $workoutId no longer exists; dropping sync")
            return Result.success()
        }

        val metrics = workoutRepository.getMetrics(workoutId)

        return when (val outcome = syncRepository.syncWorkout(workout, metrics)) {
            is SyncOutcome.Success -> {
                Log.i(TAG, "Synced workout $workoutId (${metrics.size} samples)")
                Result.success()
            }

            SyncOutcome.Disabled -> {
                // No credentials, or the rider turned sync off. Retrying will
                // never help, so fail terminally rather than burning battery.
                Log.i(TAG, "Cloud sync disabled; not syncing $workoutId")
                Result.success()
            }

            is SyncOutcome.Failed -> {
                if (runAttemptCount >= MAX_ATTEMPTS) {
                    Log.w(TAG, "Giving up on $workoutId after $runAttemptCount attempts", outcome.cause)
                    Result.failure()
                } else {
                    Log.w(TAG, "Sync attempt $runAttemptCount for $workoutId failed; will retry")
                    Result.retry()
                }
            }
        }
    }

    companion object {
        private const val TAG = "WorkoutSyncWorker"
        private const val KEY_WORKOUT_ID = "workout_id"
        private const val MAX_ATTEMPTS = 3

        fun enqueue(context: Context, workoutId: String) {
            val request = OneTimeWorkRequestBuilder<WorkoutSyncWorker>()
                .setInputData(Data.Builder().putString(KEY_WORKOUT_ID, workoutId).build())
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            // Keyed by workout id so a re-enqueue cannot upload the same ride
            // twice concurrently.
            WorkManager.getInstance(context).enqueueUniqueWork(
                "sync-workout-$workoutId",
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
