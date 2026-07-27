package com.pelonot.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pelonot.data.local.AppDatabase
import com.pelonot.data.local.entity.WorkoutEntity
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class WorkoutDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var workoutDao: WorkoutDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        workoutDao = database.workoutDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `insertWorkout and getWorkoutById returns correct workout`() = runBlocking {
        val workout = WorkoutEntity(
            id = "test-workout-1",
            localUserId = 1,
            classTemplateId = 1,
            durationSec = 1800,
            totalOutputKj = 150.0,
            totalDistanceKm = 10.0,
            avgCadence = 90.0,
            avgPower = 200.0,
            avgHr = 150,
            intentModifier = "Reach New Milestones",
            timestamp = System.currentTimeMillis()
        )

        workoutDao.insertWorkout(workout)
        val retrieved = workoutDao.getWorkoutById("test-workout-1")

        assertNotNull(retrieved)
        assertEquals("test-workout-1", retrieved?.id)
        assertEquals(1, retrieved?.localUserId)
        assertEquals(1800, retrieved?.durationSec)
        assertEquals(150.0, retrieved?.totalOutputKj, 0.01)
    }

    @Test
    fun `getLatestWorkout returns most recent workout`() = runBlocking {
        val workout1 = WorkoutEntity(
            id = "workout-1",
            localUserId = 1,
            durationSec = 1000,
            totalOutputKj = 100.0,
            totalDistanceKm = 5.0,
            avgCadence = 80.0,
            avgPower = 180.0,
            avgHr = null,
            intentModifier = "Just Stay Fit",
            timestamp = 1000L
        )
        val workout2 = WorkoutEntity(
            id = "workout-2",
            localUserId = 1,
            durationSec = 2000,
            totalOutputKj = 200.0,
            totalDistanceKm = 10.0,
            avgCadence = 90.0,
            avgPower = 200.0,
            avgHr = null,
            intentModifier = "Just Stay Fit",
            timestamp = 2000L
        )

        workoutDao.insertWorkout(workout1)
        workoutDao.insertWorkout(workout2)

        val latest = workoutDao.getLatestWorkout(1)
        assertEquals("workout-2", latest?.id)
    }

    @Test
    fun `getPersonalBestOutput returns max output in duration range`() = runBlocking {
        val workout1 = WorkoutEntity(
            id = "workout-1",
            localUserId = 1,
            durationSec = 1800,
            totalOutputKj = 150.0,
            totalDistanceKm = 10.0,
            avgCadence = 90.0,
            avgPower = 200.0,
            avgHr = null,
            intentModifier = "Just Stay Fit",
            timestamp = 1000L
        )
        val workout2 = WorkoutEntity(
            id = "workout-2",
            localUserId = 1,
            durationSec = 2000,
            totalOutputKj = 200.0,
            totalDistanceKm = 12.0,
            avgCadence = 85.0,
            avgPower = 190.0,
            avgHr = null,
            intentModifier = "Just Stay Fit",
            timestamp = 2000L
        )

        workoutDao.insertWorkout(workout1)
        workoutDao.insertWorkout(workout2)

        val pb = workoutDao.getPersonalBestOutput(1, 1500, 2500)
        assertEquals(200.0, pb, 0.01)
    }

    @Test
    fun `getPersonalAverageOutput returns average output in duration range`() = runBlocking {
        val workout1 = WorkoutEntity(
            id = "workout-1",
            localUserId = 1,
            durationSec = 1800,
            totalOutputKj = 150.0,
            totalDistanceKm = 10.0,
            avgCadence = 90.0,
            avgPower = 200.0,
            avgHr = null,
            intentModifier = "Just Stay Fit",
            timestamp = 1000L
        )
        val workout2 = WorkoutEntity(
            id = "workout-2",
            localUserId = 1,
            durationSec = 2000,
            totalOutputKj = 200.0,
            totalDistanceKm = 12.0,
            avgCadence = 85.0,
            avgPower = 190.0,
            avgHr = null,
            intentModifier = "Just Stay Fit",
            timestamp = 2000L
        )

        workoutDao.insertWorkout(workout1)
        workoutDao.insertWorkout(workout2)

        val avg = workoutDao.getPersonalAverageOutput(1, 1500, 2500)
        assertEquals(175.0, avg, 0.01)
    }
}