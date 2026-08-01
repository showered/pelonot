package com.pelonot.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pelonot.data.local.AppDatabase
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.data.local.entity.WorkoutMetricEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Saving a profile must not cost the rider their history.
 *
 * This exists because it did. `UserDao.insertUser` was
 * `@Insert(onConflict = REPLACE)`, and SQLite implements REPLACE as a delete
 * followed by an insert — with foreign-key actions firing on the delete. Since
 * `workouts.user_id` is `ON DELETE SET NULL`, **every FTP change, weight change
 * and rename silently unattributed that rider's entire history**, for the whole
 * life of the project. Nothing looked broken: the rides were still there, and
 * the dashboard simply started saying "No rides recorded yet".
 *
 * Found by toggling one setting on the tablet AVD and watching seven rides lose
 * their owner. The test is written against the *behaviour a rider cares about*
 * rather than against the annotation, so it keeps holding if the implementation
 * changes again.
 */
@RunWith(AndroidJUnit4::class)
class UserDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var userDao: UserDao
    private lateinit var workoutDao: WorkoutDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        )
            // The defect under test is a foreign-key action, so they have to be
            // switched on: without this the test would pass against the bug.
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE)
            .build()
        userDao = database.userDao()
        workoutDao = database.workoutDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun savingAProfileAgainKeepsItsRidesAttached() = runBlocking {
        userDao.insertUser(UserEntity(localUserId = 1, name = "Test Rider", ftpWatts = 200))
        workoutDao.insertWorkout(
            WorkoutEntity(
                id = "w1",
                userId = 1,
                durationSec = 1200,
                totalOutputKj = 150.0,
                isComplete = true
            )
        )

        // What Settings does when the rider edits their FTP.
        val existing = userDao.getUserById(1)!!
        userDao.insertUser(existing.copy(ftpWatts = 220))

        assertEquals(220, userDao.getUserById(1)?.ftpWatts)
        assertEquals(
            "saving a profile detached its rider's rides",
            1,
            workoutDao.getWorkoutById("w1")?.userId
        )
    }

    @Test
    fun savingAWorkoutAgainKeepsItsSamples() = runBlocking {
        workoutDao.insertWorkout(
            WorkoutEntity(id = "w1", durationSec = 1200, totalOutputKj = 0.0)
        )
        database.workoutMetricDao().insertMetrics(
            listOf(
                WorkoutMetricEntity(workoutId = "w1", timestampSec = 0, cadence = 88.0),
                WorkoutMetricEntity(workoutId = "w1", timestampSec = 1, cadence = 89.0)
            )
        )

        workoutDao.insertWorkout(
            WorkoutEntity(id = "w1", durationSec = 1200, totalOutputKj = 150.0, isComplete = true)
        )

        // `workout_metrics.workout_id` is ON DELETE CASCADE, so a REPLACE here
        // would take the whole time series with it — the one table in this app
        // that cannot be regenerated.
        assertEquals(
            "re-saving a workout destroyed its samples",
            2,
            database.workoutMetricDao().getMetricsForWorkout("w1").size
        )
    }
}
