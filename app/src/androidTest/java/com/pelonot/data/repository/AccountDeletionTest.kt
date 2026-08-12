package com.pelonot.data.repository

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pelonot.data.local.AppDatabase
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.data.local.entity.WorkoutMetricEntity
import com.pelonot.data.remote.AuthRepository
import com.pelonot.data.remote.CloudAccess
import com.pelonot.data.remote.SupabaseSyncRepository
import com.pelonot.data.remote.SyncOutcome
import com.pelonot.domain.cloud.CloudDeletion
import com.pelonot.domain.retention.RetentionAge
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Deleting the cloud copy, from the tablet's side (PLAN 15.4.2, 15.4.4).
 *
 * The cloud half is one `DELETE` against policies that were verified from a
 * second account (`supabase/verify_rls.py`, 15.5.4). **The half that can go
 * wrong quietly is this one**, and every test here is about something the
 * rider's own tablet must or must not do afterwards: keep every ride, stop
 * claiming a backup that no longer exists, and stop *licensing* the trimmer
 * against a copy that has just been deleted.
 *
 * The network call is a lambda, which is why all of this runs against a real
 * database with no endpoint anywhere near it.
 */
@RunWith(AndroidJUnit4::class)
class AccountDeletionTest {

    private lateinit var database: AppDatabase
    private lateinit var workouts: WorkoutRepository
    private var riderId = 0

    private val now = 1_700_000_000_000L
    private val lastYear = now - 400L * 24 * 60 * 60 * 1_000

    /** What the cloud will say it removed, and whether it will answer at all. */
    private var cloudSays: SyncOutcome<CloudDeletion> =
        SyncOutcome.Success(CloudDeletion(rides = 1, profileRemoved = true))

    private lateinit var accounts: AccountRepository

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        )
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()
        workouts = WorkoutRepository(
            database.workoutDao(),
            database.workoutMetricDao(),
            database.activeRideRivalDao(),
            database.workoutPowerBestDao()
        )
        riderId = database.userDao().insertUser(
            UserEntity(name = "Test Rider", weightKg = 72.0, ftpWatts = 200, authUserId = ACCOUNT)
        ).toInt()
        accounts = AccountRepository(
            // No credentials in the test build, so `signOut` is a no-op that
            // returns Disabled — which is exactly the case that must still
            // detach the profile, because the tablet's own record of who is
            // signed in is not the SDK's to keep.
            authRepository = AuthRepository(credentialsPresent = { false }),
            userRepository = UserRepository(
                database = database,
                userDao = database.userDao(),
                ftpHistoryDao = database.ftpHistoryDao(),
                syncRepository = SupabaseSyncRepository(CloudAccess(database.userDao()))
            ),
            userDao = database.userDao(),
            workoutDao = database.workoutDao(),
            deleteCloudCopy = { cloudSays }
        )
    }

    @After
    fun tearDown() = database.close()

    private suspend fun ride(
        id: String,
        seconds: Int = 600,
        atMs: Long = lastYear,
        syncedAtMs: Long? = now
    ): WorkoutEntity {
        val workout = WorkoutEntity(
            id = id,
            userId = riderId,
            durationSec = seconds,
            totalOutputKj = 120.0,
            ftpWatts = 200,
            timestamp = atMs
        )
        workouts.beginWorkout(workout)
        workouts.recordMetrics(
            (1..seconds).map {
                WorkoutMetricEntity(
                    workoutId = id,
                    timestampSec = it,
                    cadence = 90.0,
                    resistance = 40.0,
                    power = 200.0,
                    heartRate = null,
                    powerIsMeasured = true
                )
            }
        )
        workouts.finaliseWorkout(workout)
        if (syncedAtMs != null) workouts.markSynced(id, syncedAtMs)
        return workout
    }

    /** 15.4.4, and the whole reason the dialog says it first. */
    @Test
    fun everyRideAndEverySecondStaysOnTheTablet() = runBlocking {
        ride("kept")

        accounts.deleteCloudData(riderId)

        assertNotNull(database.workoutDao().getWorkoutById("kept"))
        assertEquals(600, database.workoutMetricDao().getMetricsForWorkout("kept").size)
    }

    /**
     * The state this action must never leave behind: an attached account and a
     * `synced_at` recording a backup that has just been deleted.
     *
     * Either half alone is a defect with no symptom. Left attached, the next
     * ride drains the backlog and puts the copy straight back. Left marked as
     * synced, `WorkoutDao.trimmableRides` (23.4.6) reads that column as
     * permission to throw away seconds because the cloud has them.
     */
    @Test
    fun theTabletStopsBelievingInACloudCopyItJustDeleted() = runBlocking {
        ride("was-backed-up")

        accounts.deleteCloudData(riderId)

        assertNull(database.workoutDao().getWorkoutById("was-backed-up")?.syncedAt)
        assertNull(database.userDao().getUserById(riderId)?.authUserId)
    }

    /**
     * And the consequence of those two together, stated as the trimmer sees it:
     * the ride is no longer condensable *as a ride the cloud is holding*. It
     * becomes condensable again only under the offline rule — this tablet is
     * the only copy and the rider has said so — which is 23.4.10's distinction
     * arriving intact rather than being papered over.
     */
    @Test
    fun aDeletedCloudCopyDoesNotLicenseTheTrimmer() = runBlocking {
        ride("old", atMs = lastYear)
        val cutoff = RetentionAge.SixMonths.cutoffMs(now)!!

        // Before: attached account, ride synced — trimmable because the cloud
        // has the full one.
        assertEquals(1, database.workoutDao().trimmableRideCount(cutoff, NO_RIDE))

        accounts.deleteCloudData(riderId)

        // After: still trimmable, but now on the offline rule, and the rider
        // has been told in those words that this bike is the only copy.
        assertEquals(1, database.workoutDao().trimmableRideCount(cutoff, NO_RIDE))
        assertNull(database.workoutDao().getWorkoutById("old")?.syncedAt)
    }

    /** A failure changes nothing at all — 15.4.2's "and stop" is conditional. */
    @Test
    fun aRefusedDeleteLeavesTheAccountAttached() = runBlocking {
        ride("still-up-there")
        cloudSays = SyncOutcome.Rejected("no")

        val outcome = accounts.deleteCloudData(riderId)

        assertTrue(outcome is SyncOutcome.Rejected)
        assertNotNull(database.workoutDao().getWorkoutById("still-up-there")?.syncedAt)
        assertEquals(ACCOUNT, database.userDao().getUserById(riderId)?.authUserId)
    }

    /**
     * The count behind the dialog's third sentence (23.4.3).
     *
     * Only a ride that is *both* an outline here and taken up there is a ride
     * whose seconds this action can cost anybody. A condensed ride the cloud
     * never took has nothing fuller anywhere, and an intact ride loses nothing.
     */
    @Test
    fun onlyCondensedRidesTheCloudTookAreCounted() = runBlocking {
        ride("intact-and-synced")
        ride("outline-and-synced")
        ride("outline-never-synced", syncedAtMs = null)
        database.workoutDao().markTrimmed("outline-and-synced", 10, null)
        database.workoutDao().markTrimmed("outline-never-synced", 10, null)

        assertEquals(1, accounts.condensedRideCount(riderId))
    }

    private companion object {
        const val ACCOUNT = "00000000-0000-0000-0000-0000000000aa"

        /** No ride is in progress in any of these. */
        const val NO_RIDE = "none"
    }
}
