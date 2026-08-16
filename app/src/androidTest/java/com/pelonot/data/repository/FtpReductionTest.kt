package com.pelonot.data.repository

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pelonot.data.local.AppDatabase
import com.pelonot.data.local.entity.FtpChangeSource
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.data.local.entity.WorkoutMetricEntity
import com.pelonot.data.remote.CloudAccess
import com.pelonot.data.remote.SupabaseSyncRepository
import com.pelonot.domain.progress.FtpReductionRule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the database is allowed to hand `FtpReductionRule` (PLAN 7.11).
 *
 * The rule itself is decided on the JVM in `FtpReductionRuleTest`; everything
 * here is about the **funnel**, which is the half that can fail silently. Every
 * refusal below would show up as *the feature simply never fires*, and there is
 * no screen anywhere that could tell a rider whether that was because their
 * fitness is fine or because a join stopped matching.
 *
 * The one to read first is [aSimulatedRideCannotLowerAnFtp]. It is 7.11.2's
 * gate, it is Phase 27's bar for a *record* applied to a claim about a rider's
 * body, and it is also why none of this can be checked on an emulator without
 * setting `power_is_measured` by hand.
 */
@RunWith(AndroidJUnit4::class)
class FtpReductionTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: WorkoutRepository
    private lateinit var users: UserRepository
    private var riderId = 0

    /** Twenty minutes plus a little, so a twenty-minute window exists at all. */
    private val rideSeconds = FtpReductionRule.EVIDENCE_WINDOW_SEC + 60

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        )
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()
        repository = WorkoutRepository(
            database.workoutDao(),
            database.workoutMetricDao(),
            database.activeRideRivalDao(),
            database.workoutPowerBestDao()
        )
        users = UserRepository(
            database = database,
            userDao = database.userDao(),
            ftpHistoryDao = database.ftpHistoryDao(),
            syncRepository = SupabaseSyncRepository(CloudAccess(database.userDao()))
        )
        // Inserted through the DAO rather than `UserRepository.save`, so the
        // profile starts with **no** `ftp_history` row and the evidence window
        // opens at zero. In production every profile has a `ProfileCreated`
        // row, and every ride is after it, so the effect is the same.
        riderId = database.userDao()
            .insertUser(UserEntity(name = "Test Rider", weightKg = 72.0, ftpWatts = 200))
            .toInt()
    }

    @After
    fun tearDown() = database.close()

    /**
     * A ride recorded the way the service records one — the row first, then the
     * samples, then the finalise, which is what computes the stored efforts.
     *
     * The defaults describe the case 7.11 is about: measured watts, twenty-odd
     * minutes at 180 W (an implied FTP of 171 against a current 200), and a
     * heart rate at 84% of the maximum the ride was judged against.
     */
    private suspend fun ride(
        id: String,
        atMs: Long,
        watts: Double = 180.0,
        heartRate: Int? = 160,
        maxHr: Int? = 190,
        measured: Boolean? = true
    ): WorkoutEntity {
        val workout = WorkoutEntity(
            id = id,
            userId = riderId,
            durationSec = rideSeconds,
            totalOutputKj = watts * rideSeconds / 1000.0,
            avgHr = heartRate?.toDouble(),
            maxHrBpm = maxHr,
            timestamp = atMs
        )
        repository.beginWorkout(workout)
        repository.recordMetrics(
            (1..rideSeconds).map {
                WorkoutMetricEntity(
                    workoutId = id,
                    timestampSec = it,
                    cadence = 90.0,
                    resistance = 40.0,
                    power = watts,
                    heartRate = heartRate,
                    powerIsMeasured = measured
                )
            }
        )
        repository.finaliseWorkout(workout)
        return workout
    }

    private suspend fun threeHardRides(watts: Double = 180.0) {
        ride("w1", atMs = 1_000, watts = watts)
        ride("w2", atMs = 2_000, watts = watts)
        ride("w3", atMs = 3_000, watts = watts)
    }

    private suspend fun ask(currentFtp: Int = 200) = repository.ftpReduction(
        userId = riderId,
        currentFtp = currentFtp,
        ftpSettledAt = users.lastFtpChangeAt(riderId)
    )

    @Test
    fun threeHardRidesUnderTheBarOfferTheBestOfThem() = runBlocking {
        ride("w1", atMs = 1_000, watts = 176.0)
        ride("w2", atMs = 2_000, watts = 184.0)
        ride("w3", atMs = 3_000, watts = 180.0)

        val reduction = ask()

        assertNotNull("three hard short rides are the whole trigger", reduction)
        // 184 × 0.95 = 174.8. The best of the three, not the newest.
        assertEquals(175, reduction!!.proposedFtp)
        assertEquals("w2", reduction.strongestRide.workoutId)
        assertEquals(3, reduction.evidence.size)
    }

    @Test
    fun aSimulatedRideCannotLowerAnFtp() = runBlocking {
        // 7.11.2. `power_is_measured = 0` on every sample, which is every ride
        // an emulator can produce. `PowerModel` scores RMSE 137 W against the
        // real board, and a number the app invented must not be the evidence
        // that the app then edits the rider's record with.
        ride("w1", atMs = 1_000, measured = false)
        ride("w2", atMs = 2_000, measured = false)
        ride("w3", atMs = 3_000, measured = false)

        assertNull(ask())
    }

    @Test
    fun aRideWithNoStoredEffortIsNotEvidence() = runBlocking {
        threeHardRides()
        // The shape 23.4.8 warns about, reproduced: the stored effort is gone
        // and the samples are still there. A scan of `workout_metrics` would
        // answer this ride with a number; the join returns nothing, which is
        // the honest answer.
        database.openHelper.writableDatabase
            .execSQL("DELETE FROM workout_power_bests WHERE workout_id = 'w2'")

        assertNull(ask())
    }

    @Test
    fun aTrimmedRideStillSpeaksThroughTheEffortItRecorded() = runBlocking {
        threeHardRides()
        // The other side of the same rule: 23.4 takes the seconds away and the
        // twenty-minute effort survives, because it was computed at finalise.
        database.openHelper.writableDatabase
            .execSQL("DELETE FROM workout_metrics")

        assertNotNull(ask())
    }

    @Test
    fun anEasyRideBreaksNothingAndProvesNothing() = runBlocking {
        threeHardRides()
        // A recovery spin between the hard ones: under the FTP, as almost every
        // ride is, and at a heart rate that says the rider was not trying.
        ride("spin", atMs = 2_500, watts = 120.0, heartRate = 105)

        val reduction = ask()

        assertNotNull("a spin is silent, not counter-evidence", reduction)
        assertEquals(
            listOf("w3", "w2", "w1"),
            reduction!!.evidence.map { it.workoutId }
        )
    }

    @Test
    fun ridesFromBeforeTheRidersLastFtpChangeAreAlreadyAnswered() = runBlocking {
        threeHardRides()
        assertNotNull("the evidence is there to begin with", ask())

        // The rider settles the question themselves. Everything above is now
        // history: they have said what their number is, at a moment after all
        // three rides.
        users.updateFtp(
            userId = riderId,
            ftpWatts = 190,
            source = FtpChangeSource.ManualEdit
        )

        assertNull(repository.ftpReduction(
            userId = riderId,
            currentFtp = 190,
            ftpSettledAt = users.lastFtpChangeAt(riderId)
        ))
    }

    @Test
    fun keepingTheNumberRestartsTheEvidence() = runBlocking {
        threeHardRides()
        assertNotNull(ask())

        // 7.11.4's cooldown, which the upward path has never had. The flag is on
        // the newest ride, and the window starts from that ride's timestamp —
        // so the two before it are out too, and the rider has to ride three
        // fresh hard rides before being asked again.
        repository.declineFtpProposal("w3")
        assertNull(ask())

        ride("w4", atMs = 4_000)
        ride("w5", atMs = 5_000)
        assertNull("two is not a trend", ask())

        ride("w6", atMs = 6_000)
        assertNotNull("three fresh ones are", ask())
    }

    @Test
    fun aRideThatMetTheNumberEndsIt() = runBlocking {
        ride("w1", atMs = 1_000)
        ride("w2", atMs = 2_000)
        // 215 × 0.95 = 204, above a current 200. Whatever the two before it
        // said, the rider has just produced the watts.
        ride("w3", atMs = 3_000, watts = 215.0)

        assertNull(ask())
    }

    @Test
    fun aRiderWithNoFtpIsAskedNothing() = runBlocking {
        threeHardRides()
        assertNull(ask(currentFtp = 0))
    }
}
