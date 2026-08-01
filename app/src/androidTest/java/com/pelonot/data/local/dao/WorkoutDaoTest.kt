package com.pelonot.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pelonot.data.local.AppDatabase
import com.pelonot.data.local.entity.ClassTemplateEntity
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.data.local.entity.WorkoutMetricEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DAO behaviour against a real in-memory SQLite database.
 *
 * The previous version of this file did not compile: it constructed
 * `WorkoutEntity` with `localUserId`, `classTemplateId` and a String
 * `intentModifier`, none of which have ever been fields on that entity. PLAN
 * item 8.8 was ticked regardless.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var workoutDao: WorkoutDao
    private lateinit var metricDao: WorkoutMetricDao
    private lateinit var userDao: UserDao

    /**
     * Note the explicit `Unit`: `= runBlocking { … }` infers its type from the
     * last expression, and `insertUser` returns the new row id. JUnit rejects a
     * `@Before` that does not return void, so this whole class failed to
     * initialise and **every test in it silently never ran** — while PLAN item
     * 8.8 was ticked. Same failure mode as the previous version of this file,
     * which did not compile at all.
     */
    @Before
    fun setup(): Unit = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        workoutDao = database.workoutDao()
        metricDao = database.workoutMetricDao()
        userDao = database.userDao()

        // workouts.user_id is a foreign key, so profiles must exist first.
        userDao.insertUser(UserEntity(localUserId = USER_ID, name = "Test Rider"))
        userDao.insertUser(UserEntity(localUserId = OTHER_USER_ID, name = "Housemate"))

        // workouts.class_id is one too, and the household leaderboard is
        // per class.
        database.classTemplateDao().upsertAll(
            listOf(CLASS_ID, "SS-04").map { id ->
                ClassTemplateEntity(
                    id = id,
                    title = id,
                    category = "Threshold",
                    durationSec = 1800,
                    intervalsJson = "[]"
                )
            }
        )
    }

    @After
    fun teardown() = database.close()

    private fun workout(
        id: String,
        userId: Int? = USER_ID,
        durationSec: Int = 1800,
        outputKj: Double = 150.0,
        isComplete: Boolean = true,
        timestamp: Long = System.currentTimeMillis(),
        classId: String? = null
    ) = WorkoutEntity(
        id = id,
        userId = userId,
        classId = classId,
        durationSec = durationSec,
        totalOutputKj = outputKj,
        totalDistanceKm = 10.0,
        avgCadence = 90.0,
        avgPower = 200.0,
        avgHr = 150.0,
        intentModifier = 1.05,
        isComplete = isComplete,
        timestamp = timestamp
    )

    @Test
    fun insertAndReadBackAWorkout() = runBlocking {
        workoutDao.insertWorkout(workout("w1"))

        val retrieved = workoutDao.getWorkoutById("w1")

        assertNotNull(retrieved)
        assertEquals(1800, retrieved!!.durationSec)
        assertEquals(150.0, retrieved.totalOutputKj, 0.001)
        assertEquals(USER_ID, retrieved.userId)
    }

    @Test
    fun metricsCanBeWrittenOnceTheParentWorkoutExists() = runBlocking {
        // Regression: metrics were previously written every second during a
        // ride while the workout row was only inserted at the end, so this
        // foreign key was always violated and no time series was ever stored.
        workoutDao.insertWorkout(workout("w1", isComplete = false))

        metricDao.insertMetrics(
            (0 until 10).map { second ->
                WorkoutMetricEntity(
                    workoutId = "w1",
                    timestampSec = second,
                    cadence = 90.0,
                    resistance = 40.0,
                    power = 200.0,
                    heartRate = 140
                )
            }
        )

        assertEquals(10, metricDao.getMetricCountForWorkout("w1"))
    }

    @Test
    fun deletingAWorkoutCascadesToItsMetrics() = runBlocking {
        workoutDao.insertWorkout(workout("w1"))
        metricDao.insertMetric(
            WorkoutMetricEntity(workoutId = "w1", timestampSec = 0, power = 100.0)
        )

        workoutDao.deleteWorkout("w1")

        assertEquals(0, metricDao.getMetricCountForWorkout("w1"))
    }

    @Test
    fun inProgressRidesAreExcludedFromHistoryAndLeaderboards() = runBlocking {
        workoutDao.insertWorkout(workout("finished", outputKj = 150.0))
        workoutDao.insertWorkout(workout("in-progress", outputKj = 0.0, isComplete = false))

        val history = workoutDao.getWorkoutsByUser(USER_ID).first()
        assertEquals(listOf("finished"), history.map { it.id })

        // Without the is_complete filter, the 0 kJ in-progress ride would drag
        // the personal average down.
        assertEquals(150.0, workoutDao.getPersonalAverageOutput(USER_ID, 0, 9999)!!, 0.001)
    }

    @Test
    fun personalBestOnlyConsidersComparableDurations() = runBlocking {
        workoutDao.insertWorkout(workout("30min", durationSec = 1800, outputKj = 150.0))
        workoutDao.insertWorkout(workout("90min", durationSec = 5400, outputKj = 400.0))

        val best = workoutDao.getPersonalBestOutput(USER_ID, 1620, 1980)

        assertEquals(150.0, best!!, 0.001)
    }

    @Test
    fun householdBestSpansEveryProfile() = runBlocking {
        workoutDao.insertWorkout(workout("mine", outputKj = 150.0))
        workoutDao.insertWorkout(workout("theirs", userId = OTHER_USER_ID, outputKj = 220.0))

        assertEquals(150.0, workoutDao.getPersonalBestOutput(USER_ID, 0, 9999)!!, 0.001)
        assertEquals(220.0, workoutDao.getHouseholdBestOutput(0, 9999)!!, 0.001)
    }

    @Test
    fun onlyAnUnfinishedRideIsOfferedForRecovery() = runBlocking {
        workoutDao.insertWorkout(workout("finished"))

        // Regression: the old query was `ORDER BY timestamp DESC LIMIT 1` with
        // no completion filter, so the app offered to resume the ride the user
        // had just finished, on every single launch.
        assertNull(workoutDao.getIncompleteWorkout(excludingId = null))

        workoutDao.insertWorkout(
            workout("crashed", isComplete = false, timestamp = System.currentTimeMillis() + 1000)
        )
        assertEquals("crashed", workoutDao.getIncompleteWorkout(excludingId = null)?.id)

        workoutDao.deleteIncompleteWorkouts(excludingId = null)
        assertNull(workoutDao.getIncompleteWorkout(excludingId = null))
        assertNotNull(workoutDao.getWorkoutById("finished"))
    }

    /**
     * 8.3b. A ride in progress is `is_complete = 0` exactly like a crashed one,
     * and the app used to offer to discard the class the rider was still on —
     * then delete the row out from under the running service.
     */
    @Test
    fun theRideInProgressIsNeitherOfferedForRecoveryNorDeleted() = runBlocking {
        val now = System.currentTimeMillis()
        workoutDao.insertWorkout(workout("crashed", isComplete = false, timestamp = now - 1000))
        workoutDao.insertWorkout(workout("live", isComplete = false, timestamp = now))

        // Newest first would otherwise hand back the live ride every time.
        assertEquals("crashed", workoutDao.getIncompleteWorkout(excludingId = "live")?.id)

        workoutDao.deleteIncompleteWorkouts(excludingId = "live")
        assertNull(workoutDao.getIncompleteWorkout(excludingId = "live"))
        assertNotNull(workoutDao.getWorkoutById("live"))
    }

    /**
     * The null case is spelled out in the SQL because `id != NULL` is never
     * true: written the obvious way, excluding nothing would exclude
     * everything and no ride could ever be recovered.
     */
    @Test
    fun excludingNothingStillFindsTheCrashedRide() = runBlocking {
        workoutDao.insertWorkout(workout("crashed", isComplete = false))

        assertEquals("crashed", workoutDao.getIncompleteWorkout(excludingId = null)?.id)
    }

    @Test
    fun recentWorkoutsAreOrderedNewestFirst() = runBlocking {
        val now = System.currentTimeMillis()
        workoutDao.insertWorkout(workout("old", timestamp = now - 10_000))
        workoutDao.insertWorkout(workout("new", timestamp = now))

        val recent = workoutDao.getRecentWorkouts(USER_ID, limit = 10)

        assertEquals(listOf("new", "old"), recent.map { it.id })
    }

    @Test
    fun rpeCanBeRecordedAfterTheRide() = runBlocking {
        workoutDao.insertWorkout(workout("w1"))

        workoutDao.setRpeRating("w1", 7)

        assertEquals(7, workoutDao.getWorkoutById("w1")?.rpeRating)
    }

    @Test
    fun historyJoinsTheClassTitleAndSkipsInProgressRides() = runBlocking {
        database.classTemplateDao().insert(
            ClassTemplateEntity(
                id = "TB-01",
                title = "Tabata Sprint 20",
                category = "Tabata Bursts",
                durationSec = 1200,
                intervalsJson = "[]"
            )
        )
        workoutDao.insertWorkout(workout("classRide").copy(classId = "TB-01"))
        workoutDao.insertWorkout(workout("justRide"))
        workoutDao.insertWorkout(workout("crashed", isComplete = false))

        val history = workoutDao.observeHistory(USER_ID, limit = 50).first()

        assertEquals(setOf("classRide", "justRide"), history.map { it.id }.toSet())
        assertEquals(
            "Tabata Sprint 20",
            history.first { it.id == "classRide" }.classTitle
        )
        // A Just Ride has no class, and the row says so rather than blank.
        assertNull(history.first { it.id == "justRide" }.classTitle)
        assertEquals("Just Ride", history.first { it.id == "justRide" }.displayTitle)
    }

    @Test
    fun historyWindowTakesTheNewestRidesAndTheCountKnowsTheRest() = runBlocking {
        val now = System.currentTimeMillis()
        repeat(5) { index ->
            workoutDao.insertWorkout(workout("w$index", timestamp = now + index * 1000L))
        }

        val page = workoutDao.observeHistory(USER_ID, limit = 2).first()

        assertEquals(listOf("w4", "w3"), page.map { it.id })
        assertEquals(5, workoutDao.observeCompletedCount(USER_ID).first())
    }

    /**
     * 12.3.4. The cascade is declared on the entity, but a declaration only
     * takes effect if SQLite has foreign keys switched on for the connection —
     * and an orphaned metric series is invisible from every screen and grows
     * forever. This asserts the pragma directly rather than trusting the
     * framework default.
     */
    @Test
    fun foreignKeysAreEnforcedOnTheRealConnection() = runBlocking {
        database.openHelper.readableDatabase
            .query("PRAGMA foreign_keys")
            .use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }

        workoutDao.insertWorkout(workout("w1"))
        metricDao.insertMetrics(
            (0 until 20).map {
                WorkoutMetricEntity(workoutId = "w1", timestampSec = it, power = 200.0)
            }
        )

        workoutDao.deleteWorkout("w1")

        assertEquals(0, metricDao.getMetricCountForWorkout("w1"))
    }

    @Test
    fun powerTimeSeriesComesBackInChronologicalOrder() = runBlocking {
        workoutDao.insertWorkout(workout("w1"))
        // Inserted out of order on purpose.
        metricDao.insertMetrics(
            listOf(
                WorkoutMetricEntity(workoutId = "w1", timestampSec = 2, power = 300.0),
                WorkoutMetricEntity(workoutId = "w1", timestampSec = 0, power = 100.0),
                WorkoutMetricEntity(workoutId = "w1", timestampSec = 1, power = 200.0)
            )
        )

        assertEquals(listOf(100.0, 200.0, 300.0), metricDao.getPowerTimeSeries("w1"))
    }

    // ── The household leaderboard (24.1) ────────────────────────────

    /**
     * Samples for a ride, all of one provenance.
     *
     * `null` is what a ride recorded before `power_is_measured` existed holds,
     * and it is not the same claim as `false` — but the leaderboard treats
     * both the same way, which is what these tests are checking.
     */
    private suspend fun samplesFor(workoutId: String, measured: Boolean?, count: Int = 5) {
        metricDao.insertMetrics(
            (0 until count).map { second ->
                WorkoutMetricEntity(
                    workoutId = workoutId,
                    timestampSec = second,
                    power = 200.0,
                    powerIsMeasured = measured
                )
            }
        )
    }

    @Test
    fun theHouseholdBoardRanksEachRidersBestRideOfOneClass() = runBlocking {
        workoutDao.insertWorkout(workout("mine-1", outputKj = 150.0, classId = CLASS_ID))
        samplesFor("mine-1", measured = true)
        workoutDao.insertWorkout(workout("mine-2", outputKj = 190.0, classId = CLASS_ID))
        samplesFor("mine-2", measured = true)
        workoutDao.insertWorkout(
            workout("theirs", userId = OTHER_USER_ID, outputKj = 240.0, classId = CLASS_ID)
        )
        samplesFor("theirs", measured = true)

        val board = workoutDao.householdLeaderboard(CLASS_ID)

        // One row per rider, not per ride, and my *best* is the one that counts.
        assertEquals(2, board.size)
        assertEquals(OTHER_USER_ID, board.first().localUserId)
        assertEquals(240.0, board.first().bestOutputKj, 0.001)
        assertEquals(190.0, board.first { it.localUserId == USER_ID }.bestOutputKj, 0.001)
    }

    /** 24.1.4: a guest ride has no owner, so there is nobody to place. */
    @Test
    fun theHouseholdBoardExcludesGuestRides() = runBlocking {
        workoutDao.insertWorkout(workout("mine", classId = CLASS_ID))
        samplesFor("mine", measured = true)
        workoutDao.insertWorkout(workout("guest", userId = null, outputKj = 999.0, classId = CLASS_ID))
        samplesFor("guest", measured = true)

        val board = workoutDao.householdLeaderboard(CLASS_ID)

        assertEquals(listOf(USER_ID), board.map { it.localUserId })
    }

    /**
     * 24.4.2, and the reason the `power_is_measured` column exists. A
     * simulated ride's watts are `PowerModel`'s output; ranking one beside a
     * measured ride would put a rider up against a number the app invented.
     */
    @Test
    fun theHouseholdBoardExcludesRidesWhosePowerWasNotMeasured() = runBlocking {
        workoutDao.insertWorkout(workout("measured", classId = CLASS_ID))
        samplesFor("measured", measured = true)
        workoutDao.insertWorkout(
            workout("simulated", userId = OTHER_USER_ID, outputKj = 999.0, classId = CLASS_ID)
        )
        samplesFor("simulated", measured = false)

        val board = workoutDao.householdLeaderboard(CLASS_ID)

        assertEquals(listOf(USER_ID), board.map { it.localUserId })
    }

    @Test
    fun theHouseholdBoardExcludesARideRecordedBeforeProvenanceWasKept() = runBlocking {
        workoutDao.insertWorkout(workout("measured", classId = CLASS_ID))
        samplesFor("measured", measured = true)
        workoutDao.insertWorkout(
            workout("historic", userId = OTHER_USER_ID, outputKj = 999.0, classId = CLASS_ID)
        )
        samplesFor("historic", measured = null)

        assertEquals(
            listOf(USER_ID),
            workoutDao.householdLeaderboard(CLASS_ID).map { it.localUserId }
        )
    }

    /**
     * One unmeasured second is enough. A ride the board would otherwise rank
     * cannot be shown to be measurement all the way through, and half a ride
     * of invented watts still moves a total.
     */
    @Test
    fun oneUnmeasuredSampleDisqualifiesTheWholeRide() = runBlocking {
        workoutDao.insertWorkout(workout("mostly-measured", classId = CLASS_ID))
        samplesFor("mostly-measured", measured = true, count = 100)
        metricDao.insertMetric(
            WorkoutMetricEntity(
                workoutId = "mostly-measured",
                timestampSec = 100,
                power = 200.0,
                powerIsMeasured = false
            )
        )

        assertEquals(emptyList<Int>(), workoutDao.householdLeaderboard(CLASS_ID).map { it.localUserId })
    }

    @Test
    fun theHouseholdBoardIgnoresOtherClassesAndUnfinishedRides() = runBlocking {
        workoutDao.insertWorkout(workout("this-class", classId = CLASS_ID))
        samplesFor("this-class", measured = true)
        workoutDao.insertWorkout(
            workout("other-class", userId = OTHER_USER_ID, outputKj = 999.0, classId = "SS-04")
        )
        samplesFor("other-class", measured = true)
        workoutDao.insertWorkout(
            workout(
                "in-progress",
                userId = OTHER_USER_ID,
                outputKj = 999.0,
                classId = CLASS_ID,
                isComplete = false
            )
        )
        samplesFor("in-progress", measured = true)

        assertEquals(
            listOf(USER_ID),
            workoutDao.householdLeaderboard(CLASS_ID).map { it.localUserId }
        )
    }

    private companion object {
        const val USER_ID = 1
        const val OTHER_USER_ID = 2
        const val CLASS_ID = "TH-01"
    }
}
