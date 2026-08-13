package com.pelonot.data.repository

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pelonot.data.local.AppDatabase
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.data.local.entity.WorkoutMetricEntity
import com.pelonot.domain.model.PowerProvenance
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
 * Efforts worked out once and kept (PLAN 16.3.3a).
 *
 * Written against the repository rather than the DAO because the claim is about
 * the **funnel**: every way a ride is finalised has to leave its efforts on
 * disk, or the ride is uncounted for ever once 23.4 has trimmed the samples it
 * would have been re-derived from. A test against `WorkoutPowerBestDao.upsert`
 * would stay green while the feature quietly stopped happening.
 *
 * The interesting assertion in most of these is not the watts — that is
 * `MeanMaximalPowerTest`'s, on the JVM — it is *what survives the samples going
 * away*, which is why several of them delete `workout_metrics` and then ask
 * again.
 */
@RunWith(AndroidJUnit4::class)
class PersonalBestsTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: WorkoutRepository
    private var riderId = 0

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        )
            // The cascade off `workouts` is one of the things under test.
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()
        repository = WorkoutRepository(
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
     * A ride of [seconds] at [watts], recorded the way the service records one:
     * the row first with `is_complete = 0`, then the samples, then the finalise.
     * That ordering is not decoration — `workout_metrics` has a foreign key onto
     * `workouts` (1.12).
     */
    private suspend fun ride(
        id: String,
        seconds: Int,
        watts: Double,
        measured: Boolean? = true,
        atMs: Long = 1_000L
    ): WorkoutEntity {
        val workout = WorkoutEntity(
            id = id,
            userId = riderId,
            durationSec = seconds,
            totalOutputKj = watts * seconds / 1000.0,
            timestamp = atMs
        )
        repository.beginWorkout(workout)
        repository.recordMetrics(
            (1..seconds).map {
                WorkoutMetricEntity(
                    workoutId = id,
                    timestampSec = it,
                    cadence = 90.0,
                    resistance = 40.0,
                    power = watts,
                    heartRate = null,
                    powerIsMeasured = measured
                )
            }
        )
        repository.finaliseWorkout(workout)
        return workout
    }

    private suspend fun dropSamples(workoutId: String) =
        database.openHelper.writableDatabase
            .execSQL("DELETE FROM workout_metrics WHERE workout_id = '$workoutId'")

    @Test
    fun endingARideWorksOutItsEffortsThereAndThen() = runBlocking {
        ride("w1", seconds = 400, watts = 180.0)

        val stored = database.workoutPowerBestDao().bestsFor(riderId)

        // A seven-minute ride holds five seconds, a minute and five minutes,
        // and no twenty minutes — absent rather than zero.
        assertEquals(listOf(5, 60, 300), stored.map { it.windowSec }.sorted())
        assertEquals(180.0, stored.first().watts, 0.001)
        val row = database.workoutDao().getWorkoutById("w1")
        assertNotNull("the scan must be recorded on the ride", row?.powerBestsAt)
        // And where the watts came from, which is the other half of what a
        // trimmed ride can no longer answer for itself (23.4.12).
        assertEquals(PowerProvenance.Measured, row?.powerProvenance)
    }

    @Test
    fun aTrimmedRideKeepsTheBestsItSet() = runBlocking {
        ride("w1", seconds = 400, watts = 180.0)

        // 23.4 in one line: the aggregates stay, the per-second record goes.
        dropSamples("w1")

        val bests = repository.personalBests(riderId)
        assertEquals(180.0, bests.efforts.first { it.windowSec == 300 }.watts, 0.001)
        // And the ride is still counted, because the count is asked of the
        // marker rather than of samples that no longer exist.
        assertEquals(1, bests.ridesCounted)
        assertEquals(0, bests.ridesSkipped)
    }

    @Test
    fun aModelledRideIsNeverCounted() = runBlocking {
        ride("modelled", seconds = 400, watts = 400.0, measured = false)

        val bests = repository.personalBests(riderId)

        // `PowerModel` scores RMSE 137 W; 400 W of it is a fiction, and filing
        // it as a record is the one thing this project does not do.
        assertTrue(bests.efforts.isEmpty())
        assertEquals(0, bests.ridesCounted)
        assertEquals(1, bests.ridesSkipped)
        assertNull(database.workoutDao().getWorkoutById("modelled")?.powerBestsAt)
        // **Written, not left null** — that asymmetry is 23.4.12. No efforts are
        // stored for a modelled ride, but "the app made these watts up" is a
        // real answer and the six leaderboards that used to re-count the samples
        // to find it out now read it here.
        assertEquals(
            PowerProvenance.Modelled,
            database.workoutDao().getWorkoutById("modelled")?.powerProvenance
        )
    }

    @Test
    fun aRideRecordedBeforeTheColumnExistedIsScannedOnceAndThenLeftAlone() = runBlocking {
        ride("old", seconds = 400, watts = 200.0)
        // Exactly what migrations 17 → 18 and 18 → 19 leave behind: samples, no
        // scan and no provenance. Both columns, because the list the backfill
        // walks is now *"rides whose row says measured"* — so a ride with
        // neither is only ever scanned if the provenance pass runs first, and
        // that ordering is what this fixture exists to break if it is wrong.
        database.openHelper.writableDatabase.execSQL(
            "UPDATE workouts SET power_bests_at = NULL, power_provenance = NULL " +
                "WHERE id = 'old'"
        )
        database.workoutPowerBestDao().clearFor("old")

        val first = repository.personalBests(riderId)
        assertEquals(200.0, first.efforts.first { it.windowSec == 5 }.watts, 0.001)

        // The backfill has emptied itself: the second visit has nothing left to
        // walk, which is the whole difference between this and the old shape.
        assertTrue(database.workoutDao().measuredRidesAwaitingBests(riderId).isEmpty())
        dropSamples("old")
        assertEquals(200.0, repository.personalBests(riderId).efforts.first().watts, 0.001)
    }

    /**
     * A ride condensed *before* it was ever scanned stays uncounted (23.4.3).
     *
     * The trap is that a trim does not empty `workout_metrics` — it leaves the
     * lowest and highest watt of every ten seconds, which are real rows — so the
     * backfill's list would happily walk them and file a mean-maximal effort
     * computed over a fifth of the ride's seconds. Here the outline's peak is a
     * 320 W second the rider held once; a five-minute window through it comes
     * back far above what they actually averaged for five minutes, and it would
     * sit on *Your FTP* as a personal best indistinguishable from a real one.
     *
     * The honest answer is the one the rider is already shown for a modelled
     * ride: not counted, and said so.
     */
    @Test
    fun aRideCondensedBeforeItWasScannedNeverAcquiresABest() = runBlocking {
        ride("outline", seconds = 900, watts = 180.0)
        // Trimmed without ever having been scanned — a ride recorded before
        // `power_bests_at` existed, condensed by 23.4 before anybody opened
        // *Your FTP*. The outline keeps a peak the ride only touched.
        database.openHelper.writableDatabase.execSQL(
            "UPDATE workout_metrics SET power = 320.0 WHERE workout_id = 'outline' " +
                "AND timestamp_sec % 10 = 0"
        )
        database.openHelper.writableDatabase.execSQL(
            "DELETE FROM workout_metrics WHERE workout_id = 'outline' " +
                "AND timestamp_sec % 10 != 0"
        )
        database.openHelper.writableDatabase.execSQL(
            "UPDATE workouts SET power_bests_at = NULL, metrics_detail_sec = 10 " +
                "WHERE id = 'outline'"
        )
        database.workoutPowerBestDao().clearFor("outline")

        val bests = repository.personalBests(riderId)

        assertTrue("an outline sets no records", bests.efforts.isEmpty())
        assertEquals(0, bests.ridesCounted)
        assertEquals(1, bests.ridesSkipped)
        assertTrue(database.workoutDao().measuredRidesAwaitingBests(riderId).isEmpty())
    }

    @Test
    fun theStrongestRideAtEachWindowWins() = runBlocking {
        ride("steady", seconds = 1_300, watts = 200.0, atMs = 1_000)
        ride("sprint", seconds = 400, watts = 320.0, atMs = 2_000)

        val efforts = repository.personalBests(riderId).efforts.associateBy { it.windowSec }

        assertEquals("sprint", efforts.getValue(5).workoutId)
        assertEquals("sprint", efforts.getValue(300).workoutId)
        // Only the long ride ever held twenty minutes, whoever was faster.
        assertEquals("steady", efforts.getValue(1_200).workoutId)
        assertEquals(2, repository.personalBests(riderId).ridesCounted)
    }

    @Test
    fun discardingARideTakesItsEffortsWithIt() = runBlocking {
        ride("w1", seconds = 400, watts = 180.0)

        repository.discardWorkout("w1")

        assertTrue(database.workoutPowerBestDao().bestsFor(riderId).isEmpty())
        assertTrue(repository.personalBests(riderId).efforts.isEmpty())
    }

    @Test
    fun resumingAFinishedRideThrowsAwayTheShortRidesEfforts() = runBlocking {
        ride("w1", seconds = 400, watts = 180.0)

        val interruption = repository.interruptionFor("w1", nowEpochMs = 500_000L)
        assertNotNull(interruption)
        repository.resumeInterruptedWorkout("w1", interruption!!)

        // 12.6.2: the ride is being ridden again, so the efforts on record are
        // the short version's and the marker must not claim otherwise.
        assertTrue(database.workoutPowerBestDao().bestsFor(riderId).isEmpty())
        val row = database.workoutDao().getWorkoutById("w1")
        assertNull(row?.powerBestsAt)
        // And the provenance goes with them: the extra minutes can turn a
        // measured ride into a mixed one, and a stale `Measured` would keep it
        // on six boards it no longer belongs on (23.4.12).
        assertNull(row?.powerProvenance)
    }
}
