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
 * Uploads whatever the cloud has not got yet, oldest first.
 *
 * **It drains a backlog rather than posting one ride** (PLAN 14.2.5, 14.2.6),
 * and that is a change of shape rather than a feature on top of the old one.
 * Before, the worker took a workout id, got three attempts at it, and then the
 * question was closed forever: a ride that failed while the router was rebooting
 * was never retried and nothing anywhere recorded that it had not gone up. The
 * only trace was a `Log.i` line, on a tablet whose `log.tag` is `W`.
 *
 * Now the unit of work is *the profile*, the backlog is a query over
 * `synced_at IS NULL` (14.2.4), and a ride that exhausts its retries is not
 * lost — it is simply still in the backlog, and the next ride the rider
 * finishes sweeps it up. Nothing is permanently forgotten, which is the
 * property that lets this be called a backup.
 *
 * It is also what 15.3.1's first-sign-in backfill is made of. A rider who has
 * just attached an account has a whole history where every row is unsynced;
 * "backfill everything" and "drain the backlog" are the same query in the same
 * order, so there is one implementation rather than two that drift (the 18.9
 * argument, applied a phase early).
 *
 * The previous version discarded the sync result — `syncWorkout(...)` was called
 * for its side effect and `Result.success()` returned unconditionally — so a
 * failed upload reported success. It also had no network constraint and no
 * retry ceiling.
 */
class WorkoutSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val userId = inputData.getInt(KEY_USER_ID, NO_USER)
        if (userId == NO_USER) return Result.failure()

        val workoutRepository = ServiceLocator.workoutRepository
        val syncRepository = ServiceLocator.syncRepository
        val settings = ServiceLocator.settingsRepository

        val pending = workoutRepository.unsyncedWorkouts(userId)
        if (pending.isEmpty()) {
            // The only moment the app can honestly say the rider is up to date,
            // so it is the only moment the last error is cleared (14.2.3).
            settings.clearCloudSyncError()
            Log.i(TAG, "Nothing waiting for profile $userId")
            return Result.success()
        }

        Log.i(TAG, "Draining ${pending.size} ride(s) for profile $userId")

        var uploaded = 0
        for (workout in pending) {
            val metrics = workoutRepository.getMetrics(workout.id)

            when (val outcome = syncRepository.syncWorkout(workout, metrics)) {
                is SyncOutcome.Success -> {
                    // Written before moving on, so a failure on the *next* ride
                    // cannot cost us the fact that this one landed. A batch that
                    // dies halfway has still made progress.
                    workoutRepository.markSynced(workout.id)
                    settings.recordCloudSync()
                    uploaded++
                    Log.i(TAG, "Synced ${workout.id} (${metrics.size} samples)")
                }

                SyncOutcome.Disabled -> {
                    // No account on this profile, no credentials in the build, or
                    // backup turned off. Retrying will never help, so stop rather
                    // than burning battery on the rest of the batch.
                    //
                    // Reached even though `enqueueIfAllowed` asked the same
                    // question: the job can be queued for a signed-in rider and
                    // run after they sign out, and the answer that matters is the
                    // one at the moment the rides would actually leave the tablet.
                    Log.i(TAG, "Cloud sync disabled; stopping after $uploaded")
                    return Result.success()
                }

                is SyncOutcome.Failed -> {
                    // Written before deciding whether to retry, so a failure is
                    // visible in Settings on the first attempt rather than only
                    // once the retries are exhausted — the rider watching the
                    // screen is the person best placed to fix a wifi problem.
                    settings.recordCloudSyncFailure(
                        outcome.cause.message ?: outcome.cause::class.java.simpleName
                    )

                    // Stop at the first failure rather than pressing on through
                    // the batch. The overwhelming cause is that the network is
                    // gone, and nineteen more attempts at it is nineteen more
                    // radio wakeups for the same answer. The rides behind this
                    // one keep their place in the backlog by doing nothing at all.
                    return if (runAttemptCount >= MAX_ATTEMPTS) {
                        Log.w(
                            TAG,
                            "Giving up after $runAttemptCount attempts, $uploaded synced; " +
                                "the rest stay in the backlog",
                            outcome.cause
                        )
                        // Deliberately not `Result.failure()` for the ride's sake
                        // — nothing is lost by stopping, because `synced_at` is
                        // still null and the next ride's enqueue will find it.
                        Result.success()
                    } else {
                        Log.w(TAG, "Attempt $runAttemptCount failed after $uploaded; will retry")
                        Result.retry()
                    }
                }
            }
        }

        // A full batch probably means there is more behind it; a short one
        // means the backlog is now empty, which is the claim that clears the
        // error.
        return if (pending.size >= BATCH_LIMIT) {
            enqueue(applicationContext, userId)
            Result.success()
        } else {
            settings.clearCloudSyncError()
            Result.success()
        }
    }

    companion object {
        private const val TAG = "WorkoutSyncWorker"
        private const val KEY_USER_ID = "user_id"
        private const val NO_USER = -1
        private const val MAX_ATTEMPTS = 3
        private val BATCH_LIMIT get() = com.pelonot.data.repository.WorkoutRepository.SYNC_BATCH

        /**
         * Drains this profile's backlog **only if it has an account** (23.1.2).
         *
         * The old rule was `finalSession.userId != null` — any profile ride at
         * all — so the app uploaded rides on behalf of riders who never signed
         * in, into a shared pool, with no `user_id` on the row (14.2.1). The
         * worker checks the same gate again when it runs, but enqueuing work
         * that can only decline is still the wrong thing to do: it schedules a
         * network-constrained job on a tablet whose rider asked for nothing of
         * the sort.
         *
         * @param workoutId the ride that prompted this, used only for the log.
         *   The upload itself is driven off the backlog query, so a ride that
         *   is somehow not in it was already synced and needs nothing.
         */
        suspend fun enqueueIfAllowed(context: Context, workoutId: String, userId: Int?) {
            if (!ServiceLocator.cloudAccess.isAllowedFor(userId)) {
                Log.i(TAG, "No account on profile $userId; not scheduling a sync for $workoutId")
                return
            }
            Log.i(TAG, "Profile $userId has an account; draining the backlog after $workoutId")
            enqueue(context, userId!!)
        }

        private fun enqueue(context: Context, userId: Int) {
            val request = OneTimeWorkRequestBuilder<WorkoutSyncWorker>()
                .setInputData(Data.Builder().putInt(KEY_USER_ID, userId).build())
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            // Keyed by profile, and APPEND_OR_REPLACE rather than KEEP.
            //
            // KEEP was right when the unit of work was one ride and wrong now:
            // a drain already running has *already taken its snapshot* of the
            // backlog, so dropping the new request loses the ride that just
            // finished until something else happens to trigger a sync. Appending
            // runs it after, against a fresh query.
            WorkManager.getInstance(context).enqueueUniqueWork(
                "sync-profile-$userId",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
        }
    }
}
