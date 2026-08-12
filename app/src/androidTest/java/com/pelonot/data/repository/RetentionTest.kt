package com.pelonot.data.repository

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pelonot.data.local.AppDatabase
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.data.local.entity.WorkoutMetricEntity
import com.pelonot.domain.chart.RideDistributions
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
 * The one thing in this app that deletes a rider's seconds (PLAN 23.4).
 *
 * Every test here is about something *not* happening: an unfinished ride left
 * alone, a ride the cloud has not taken left alone, a peak surviving, a count
 * of seconds surviving. The feature's own happy path is one test; the rest is
 * the fence, because a trimmer that is wrong is wrong permanently.
 */
@RunWith(AndroidJUnit4::class)
class RetentionTest {

    private lateinit var database: AppDatabase
    private lateinit var retention: RetentionRepository
    private lateinit var workouts: WorkoutRepository
    private var riderId = 0

    private val now = 1_700_000_000_000L
    private val lastYear = now - 400L * 24 * 60 * 60 * 1_000
    private val lastWeek = now - 7L * 24 * 60 * 60 * 1_000

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        )
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()
        retention = RetentionRepository(
            database = database,
            workoutDao = database.workoutDao(),
            metricDao = database.workoutMetricDao(),
            userDao = database.userDao(),
            databaseBytes = { 0 }
        )
        workouts = WorkoutRepository(
            database.workoutDao(),
            database.workoutMetricDao(),
            database.activeRideRivalDao(),
            database.workoutPowerBestDao()
        )
        riderId = database.userDao()
            .insertUser(UserEntity(name = "Test Rider", weightKg = 72.0, ftpWatts = 200))
            .toInt()
    }

    @After
    fun tearDown() = database.close()

    /**
     * A ride recorded the way the service records one — row first, then the
     * samples, then the finalise — with a single 600 W second in it, because
     * the peak is what the trim has to be seen not to lose.
     */
    private suspend fun ride(
        id: String,
        seconds: Int = 600,
        atMs: Long = lastYear,
        complete: Boolean = true,
        syncedAtMs: Long? = null
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
                    power = if (it == 217) 600.0 else 200.0,
                    heartRate = null,
                    powerIsMeasured = true
                )
            }
        )
        if (complete) workouts.finaliseWorkout(workout)
        if (syncedAtMs != null) workouts.markSynced(id, syncedAtMs)
        return workout
    }

    private suspend fun samplesOf(id: String) =
        database.workoutMetricDao().getMetricsForWorkout(id)

    @Test
    fun trimmingKeepsAnOutlineOfRealSecondsAndSaysItIsOne() = runBlocking {
        ride("old")

        val result = retention.trim(RetentionAge.SixMonths, nowMs = now)

        assertEquals(1, result.ridesTrimmed)
        val left = samplesOf("old")
        assertTrue("expected about a fifth of 600, got ${left.size}", left.size in 60..180)
        // Every surviving row is a second the rider really rode — nothing here
        // is an average of anything.
        assertTrue(left.all { it.power == 200.0 || it.power == 600.0 })
        // And the sprint is still in there, which is the point of min/max.
        assertEquals(600.0, left.maxOf { it.power }, 0.0)

        val row = database.workoutDao().getWorkoutById("old")
        assertEquals(10, row?.metricsDetailSec)
        assertNotNull(row?.distributionsJson)
    }

    @Test
    fun theSecondsItCountedSurviveTheSecondsThemselves() = runBlocking {
        ride("old")

        retention.trim(RetentionAge.SixMonths, nowMs = now)

        val stored = RideDistributions.decode(
            database.workoutDao().getWorkoutById("old")?.distributionsJson
        )
        // Ten minutes of riding, and the stored count still says ten minutes
        // although a fifth of the rows are left. Recomputing it would say two.
        assertEquals(600, stored?.timeInZone()?.totalSeconds)
        assertEquals(600, stored?.cadence()?.totalSeconds)
        assertEquals(200, stored?.ftpWatts)
    }

    @Test
    fun aRideYoungerThanTheChosenAgeIsUntouched() = runBlocking {
        ride("recent", atMs = lastWeek)

        val result = retention.trim(RetentionAge.SixMonths, nowMs = now)

        assertEquals(0, result.ridesTrimmed)
        assertEquals(600, samplesOf("recent").size)
        assertNull(database.workoutDao().getWorkoutById("recent")?.metricsDetailSec)
    }

    @Test
    fun neverMeansNever() = runBlocking {
        ride("old")

        val result = retention.trim(RetentionAge.Never, nowMs = now)

        assertEquals(0, result.ridesTrimmed)
        assertEquals(600, samplesOf("old").size)
    }

    /**
     * 8.3b's rule in a fourth place. Three resume paths read an unfinished
     * ride's samples, and one of them is the ride somebody is pedalling.
     */
    @Test
    fun anUnfinishedRideIsNeverTrimmedHoweverOldItIs() = runBlocking {
        ride("crashed", complete = false)

        retention.trim(RetentionAge.SixMonths, nowMs = now)

        assertEquals(600, samplesOf("crashed").size)
    }

    /**
     * 23.4.6, and 23.4.9's finding that it has to live here: by the time the
     * sync worker runs, the samples it would have uploaded are gone.
     */
    @Test
    fun aRiderWithAnAccountKeepsWhatTheCloudHasNotTaken() = runBlocking {
        database.userDao().updateUser(
            database.userDao().getUserById(riderId)!!.copy(authUserId = "account-1")
        )
        ride("unsent")
        ride("sent", syncedAtMs = lastYear)

        val result = retention.trim(RetentionAge.SixMonths, nowMs = now)

        assertEquals(1, result.ridesTrimmed)
        assertEquals(600, samplesOf("unsent").size)
        assertTrue(samplesOf("sent").size < 600)
    }

    @Test
    fun anOfflineRiderIsGatedByNothingBecauseThereIsNoCopyToBeAheadOf() = runBlocking {
        ride("unsent")

        val result = retention.trim(RetentionAge.SixMonths, nowMs = now)

        assertEquals(1, result.ridesTrimmed)
    }

    @Test
    fun aRideIsTrimmedOnceAndNeverBucketedAgain() = runBlocking {
        ride("old")
        retention.trim(RetentionAge.SixMonths, nowMs = now)
        val afterFirst = samplesOf("old").size

        val second = retention.trim(RetentionAge.SixMonths, nowMs = now)

        assertEquals(0, second.ridesTrimmed)
        assertEquals(afterFirst, samplesOf("old").size)
    }

    /**
     * The bests are the thing 16.3.3a landed first for, so this is the two items
     * meeting: a trimmed ride's efforts are still on `workout_power_bests` and
     * its provenance is still on the row, because both were written at the
     * finalise rather than re-derived on read.
     */
    @Test
    fun aTrimmedRideKeepsItsEffortsAndItsProvenance() = runBlocking {
        ride("old")
        val before = database.workoutPowerBestDao().bestsFor(riderId).map { it.windowSec to it.watts }

        retention.trim(RetentionAge.SixMonths, nowMs = now)

        val after = database.workoutPowerBestDao().bestsFor(riderId).map { it.windowSec to it.watts }
        assertEquals(before, after)
        assertEquals(
            com.pelonot.domain.model.PowerProvenance.Measured,
            database.workoutDao().getWorkoutById("old")?.powerProvenance
        )
    }

    @Test
    fun theFiguresSettingsShowsCountWhatIsActuallyThere() = runBlocking {
        ride("old")
        ride("recent", atMs = lastWeek)

        val facts = retention.facts(RetentionAge.SixMonths, nowMs = now)

        assertEquals(2, facts.rides)
        assertEquals(1_200, facts.samples)
        assertEquals(1, facts.trimmable)
    }
}
