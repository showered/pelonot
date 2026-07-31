package com.pelonot.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pelonot.data.local.AppDatabase
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
    }

    @After
    fun teardown() = database.close()

    private fun workout(
        id: String,
        userId: Int = USER_ID,
        durationSec: Int = 1800,
        outputKj: Double = 150.0,
        isComplete: Boolean = true,
        timestamp: Long = System.currentTimeMillis()
    ) = WorkoutEntity(
        id = id,
        userId = userId,
        classId = null,
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
        assertNull(workoutDao.getIncompleteWorkout())

        workoutDao.insertWorkout(
            workout("crashed", isComplete = false, timestamp = System.currentTimeMillis() + 1000)
        )
        assertEquals("crashed", workoutDao.getIncompleteWorkout()?.id)

        workoutDao.deleteIncompleteWorkouts()
        assertNull(workoutDao.getIncompleteWorkout())
        assertNotNull(workoutDao.getWorkoutById("finished"))
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

    private companion object {
        const val USER_ID = 1
        const val OTHER_USER_ID = 2
    }
}
