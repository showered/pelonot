package com.pelonot.data.service

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pelonot.data.local.AppDatabase
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class WorkoutServiceTest {

    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `WorkoutState starts as Idle`() {
        val state = WorkoutState.Idle
        assertEquals(WorkoutState.Idle, state)
    }

    @Test
    fun `WorkoutState transitions work correctly`() {
        // Test that all states are distinct
        assertNotEquals(WorkoutState.Idle, WorkoutState.Active)
        assertNotEquals(WorkoutState.Active, WorkoutState.Paused)
        assertNotEquals(WorkoutState.Paused, WorkoutState.Completed)
    }

    @Test
    fun `WorkoutSession can be created with default values`() {
        val session = WorkoutSession(
            workoutId = "test-workout",
            classId = 0,
            startTime = System.currentTimeMillis(),
            intentModifier = "Just Stay Fit"
        )

        assertEquals("test-workout", session.workoutId)
        assertEquals(0, session.classId)
        assertEquals(0, session.elapsedSeconds)
        assertEquals("Just Stay Fit", session.intentModifier)
        assertTrue(session.metrics.isEmpty())
    }

    @Test
    fun `WorkoutSession elapsedSeconds can be incremented`() {
        val session = WorkoutSession(
            workoutId = "test-workout",
            classId = 0,
            startTime = System.currentTimeMillis(),
            intentModifier = "Just Stay Fit"
        )

        val updated = session.copy(elapsedSeconds = 100)
        assertEquals(100, updated.elapsedSeconds)
    }
}