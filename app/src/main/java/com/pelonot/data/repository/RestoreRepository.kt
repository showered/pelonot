package com.pelonot.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.pelonot.data.local.AppDatabase
import com.pelonot.data.local.dao.ClassTemplateDao
import com.pelonot.data.local.dao.WorkoutDao
import com.pelonot.data.local.dao.WorkoutMetricDao
import com.pelonot.data.local.entity.FtpChangeSource
import com.pelonot.data.remote.SyncOutcome
import com.pelonot.data.remote.dto.ProfileDto
import com.pelonot.data.remote.dto.WorkoutDto
import com.pelonot.data.remote.dto.fromIso8601
import com.pelonot.domain.cloud.RestoreOutcome
import com.pelonot.domain.cloud.RestoreSurvey

/**
 * Bringing a rider's history back down (PLAN 15.3.2).
 *
 * **Until this, the cloud was write-only.** Every ride went up and nothing ever
 * came back, which made "backup" a word the app was not entitled to: a rider
 * whose tablet died had rows in Postgres and no way to reach them, and 15.4.2
 * could delete a copy the app had never been able to restore. This is the other
 * direction, and it is a *download* rather than a sync — see the four rules
 * below, all of which are about refusing to be clever.
 *
 * **1. It only ever adds rides.** A ride whose id this tablet already has is
 * skipped, whoever it belongs to (`WorkoutDao.existingIds`). Nothing is merged
 * and nothing is overwritten, because the local copy is the tablet's own record
 * and the two cannot disagree about anything that matters: a finished ride is
 * immutable except for its RPE, and taking the cloud's RPE over one the rider
 * typed here after the upload would silently undo an edit. Skipping is also what
 * makes a restore safe to run twice, which a rider on a slow connection will.
 *
 * **2. A restored ride is marked as backed up, not queued.** It came *from* the
 * cloud, so `synced_at` is set as it lands. Without that the next drain would
 * upload everything it had just downloaded — and worse, 23.4.6 reads a null
 * `synced_at` as *this tablet is the only copy*, so a restored history would be
 * treated as unprotected by the trimmer.
 *
 * **3. A ride is skipped whole or restored whole.** A payload whose columns
 * disagree, or a date that will not parse, is a corrupt record; 2.7's rule is
 * reject rather than repair, and a ride restored without a date would appear in
 * January 1970 at the bottom of every list.
 *
 * **4. The profile is adopted only by a rider who has never ridden here.** That
 * is precisely the new-device case this item is named for, and it is the only
 * case with nothing to lose — a profile with rides on this bike has an FTP and a
 * weight the rider may have changed here since, and overwriting them from a
 * copy with no timestamp on it is the read-modify-write defect of 7.9 wearing a
 * feature's clothes. Where it does apply, the change is recorded in
 * `ftp_history` as [FtpChangeSource.PulledFromCloud], so it is visible on *Your
 * FTP* rather than silent.
 *
 * The three cloud calls are lambdas for [AccountRepository]'s reason: everything
 * interesting here is what happens to the local database, and all of it is
 * testable against a real one with no endpoint anywhere near it.
 */
class RestoreRepository(
    private val database: AppDatabase,
    private val workoutDao: WorkoutDao,
    private val metricDao: WorkoutMetricDao,
    private val classTemplateDao: ClassTemplateDao,
    private val userRepository: UserRepository,
    private val fetchIds: suspend (localUserId: Int) -> SyncOutcome<List<String>>,
    private val fetchRides: suspend (localUserId: Int, ids: List<String>) ->
    SyncOutcome<List<WorkoutDto>>,
    private val fetchProfile: suspend (localUserId: Int) -> SyncOutcome<ProfileDto?>,
    private val now: () -> Long = System::currentTimeMillis
) {

    /**
     * What the account holds and how much of it is missing here (15.3.2).
     *
     * One request for one column, so the account screen can answer *is there
     * anything to bring down* without committing the rider to a download.
     */
    suspend fun survey(localUserId: Int): SyncOutcome<RestoreSurvey> {
        val ids = when (val outcome = fetchIds(localUserId)) {
            is SyncOutcome.Success -> outcome.value
            else -> return outcome.carry()
        }
        return SyncOutcome.Success(
            RestoreSurvey(inCloud = ids.size, missingHere = missingFrom(ids).size)
        )
    }

    /**
     * Brings down every ride in the account that is not on this tablet.
     *
     * Batched, and the batches are not an optimisation: each row carries its own
     * 54 KB series (14.4), and a partly-completed restore that keeps what it
     * fetched is better than an all-or-nothing that keeps nothing — the same
     * argument as draining a backlog oldest-first (14.2.5). A failure part way
     * through returns the failure and leaves the rides already written, which is
     * why the counts in [RestoreOutcome] are accumulated as it goes.
     */
    suspend fun restore(localUserId: Int): SyncOutcome<RestoreOutcome> {
        val ids = when (val outcome = fetchIds(localUserId)) {
            is SyncOutcome.Success -> outcome.value
            else -> return outcome.carry()
        }

        // Asked **before** anything is written, because restoring the rides is
        // what would make it false. Rule 4.
        val neverRiddenHere = workoutDao.completedCountFor(localUserId) == 0
        val knownClasses = classTemplateDao.allIds().toSet()

        var rides = 0
        var samples = 0
        var unreadable = 0
        var classesNotHere = 0

        for (batch in missingFrom(ids).chunked(BATCH_SIZE)) {
            val fetched = when (val outcome = fetchRides(localUserId, batch)) {
                is SyncOutcome.Success -> outcome.value
                else -> return outcome.carry()
            }

            for (dto in fetched) {
                val recordedAt = dto.recordedAt.fromIso8601()
                val metrics = dto.metrics.toMetrics(dto.id).getOrNull()
                if (recordedAt == null || metrics == null) {
                    // Rule 3, and it is deliberately quiet in the log and loud
                    // in the count: the rider is told a number, not shown a
                    // stack trace about a row they cannot do anything about.
                    Log.w(TAG, "Skipped ride ${dto.id}: unreadable record")
                    unreadable++
                    continue
                }

                val classId = dto.classId?.takeIf { it in knownClasses }
                if (dto.classId != null && classId == null) classesNotHere++

                database.withTransaction {
                    // The row first: `workout_metrics` has a foreign key onto
                    // `workouts`, which is the ordering that was silently broken
                    // for the whole project before 1.12.
                    workoutDao.insertWorkout(
                        dto.toEntity(
                            localUserId = localUserId,
                            classId = classId,
                            syncedAt = now(),
                            recordedAtMs = recordedAt
                        )
                    )
                    metricDao.insertMetrics(metrics)
                }
                rides++
                samples += metrics.size
            }
        }

        val profileAdopted = neverRiddenHere && adoptProfile(localUserId)

        Log.i(
            TAG,
            "Restored $rides rides ($samples samples) for profile $localUserId; " +
                "$unreadable unreadable, $classesNotHere without their class"
        )
        return SyncOutcome.Success(
            RestoreOutcome(
                rides = rides,
                samples = samples,
                unreadable = unreadable,
                classesNotHere = classesNotHere,
                profileAdopted = profileAdopted
            )
        )
    }

    /**
     * Takes the account's name, weight and FTP for a profile that has never
     * ridden on this bike.
     *
     * A failure here is not a failure of the restore: the rides are what the
     * rider asked for, and *"we could not read your name"* is not a reason to
     * report that their history did not come down. So it returns false rather
     * than propagating, and the screen simply does not mention the profile.
     */
    private suspend fun adoptProfile(localUserId: Int): Boolean {
        val cloud = (fetchProfile(localUserId) as? SyncOutcome.Success)?.value ?: return false
        val local = userRepository.getUser(localUserId) ?: return false
        if (local.name == cloud.name &&
            local.ftpWatts == cloud.ftpWatts &&
            local.weightKg == cloud.weightKg
        ) {
            return false
        }

        userRepository.save(
            local.copy(
                name = cloud.name,
                ftpWatts = cloud.ftpWatts,
                weightKg = cloud.weightKg
            ),
            // 7.9.4's funnel writes the history row; naming the source is what
            // keeps *Your FTP* able to say where the number came from.
            ftpSource = FtpChangeSource.PulledFromCloud
        )
        return true
    }

    private suspend fun missingFrom(ids: List<String>): List<String> {
        if (ids.isEmpty()) return emptyList()
        val here = ids.chunked(ID_QUERY_SIZE)
            .flatMap { workoutDao.existingIds(it) }
            .toSet()
        return ids.filterNot { it in here }
    }

    /**
     * Re-labels a non-success outcome as the type this call returns.
     *
     * `SyncOutcome` is generic in its success value only, so `Disabled`,
     * `Failed` and `Rejected` carry nothing that needs converting — but the
     * compiler cannot know that from a `when` in the caller, and a cast at each
     * of the four call sites reads worse than one function saying why.
     */
    private fun <T, R> SyncOutcome<T>.carry(): SyncOutcome<R> = when (this) {
        is SyncOutcome.Success -> error("carry() is for the three failures")
        is SyncOutcome.Rejected -> this
        is SyncOutcome.Failed -> this
        SyncOutcome.Disabled -> SyncOutcome.Disabled
    }

    private companion object {
        const val TAG = "PelonotRestore"

        /**
         * Rides per request. Five 45-minute rides is about 270 KB of JSON, which
         * is a request a tablet on household wifi finishes; forty of them is two
         * megabytes in one response with nothing on screen but a spinner.
         */
        const val BATCH_SIZE = 5

        /**
         * Ids per `IN (…)`. SQLite's default limit on host parameters is 999 and
         * Room binds one per id, so a rider with four figures of riding would
         * otherwise fail the survey rather than the ride.
         */
        const val ID_QUERY_SIZE = 500
    }
}
