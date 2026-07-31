package com.pelonot.data.service

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import com.pelonot.data.sensor.SensorMode
import com.pelonot.di.ServiceLocator
import com.pelonot.domain.model.RideIntent
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The workout lifecycle against the real service, on a real device.
 *
 * This is the one part of the ride that unit tests cannot reach: elapsed time
 * comes from `SystemClock.elapsedRealtime()`, metric batches are flushed
 * through Room, and the foreign key from `workout_metrics` to `workouts` is
 * enforced by SQLite. All three of those have been broken at some point in this
 * project's history and all three compiled fine.
 *
 * Telemetry is forced to Simulated so the test does not depend on a sensor
 * board being present.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutServiceTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    private lateinit var service: WorkoutService
    private var startedWorkoutId: String? = null

    @Before
    fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        ServiceLocator.init(context)

        // Set the *preference*, not the repository (2.4.6). `PelonotApp`
        // collects that preference for the life of the process and applies it,
        // so a test that reached past it and called `setMode` directly was
        // racing the collector: whichever landed last won, and in a full-suite
        // run that was sometimes the device's own stored setting — which is
        // Hardware on any machine where somebody has tried Hardware mode. The
        // symptom was two unrelated tests timing out with no telemetry.
        ServiceLocator.settingsRepository.setSensorMode(SensorMode.Simulated)
        awaitSensorMode(SensorMode.Simulated)

        val binder = serviceRule.bindService(Intent(context, WorkoutService::class.java))
        service = (binder as WorkoutService.WorkoutBinder).getService()
    }

    /** The preference reaches the pipeline through a flow, so it is not instant. */
    private fun awaitSensorMode(mode: SensorMode) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (ServiceLocator.sensorRepository.currentMode() == mode) return
            Thread.sleep(POLL_MS)
        }
        throw AssertionError(
            "telemetry never switched to $mode " +
                "(stuck at ${ServiceLocator.sensorRepository.currentMode()})"
        )
    }

    @After
    fun teardown() = runBlocking {
        if (service.workoutState.value != WorkoutState.Idle &&
            service.workoutState.value != WorkoutState.Completed
        ) {
            service.stopWorkout()
        }
        // The service writes to the app's real database, so the test cleans up
        // after itself rather than leaving a phantom ride in the rider's history.
        startedWorkoutId?.let { ServiceLocator.workoutRepository.discardWorkout(it) }
        ServiceLocator.workoutRepository.clearRecoverableWorkouts()
    }

    // ── Start ───────────────────────────────────────────────────────

    @Test
    fun startingARideWritesTheWorkoutRowBeforeAnyMetric() = runBlocking {
        startRide()

        val session = service.currentSession.value
        assertNotNull("a ride should have a session", session)
        startedWorkoutId = session!!.workoutId
        assertEquals(WorkoutState.Active, service.workoutState.value)

        // The row must exist immediately, and incomplete. Writing it only at
        // ride end is what made every metric insert violate the foreign key,
        // so no ride in this project's history ever stored a time series.
        val row = awaitWorkoutRow(session.workoutId)
        assertNotNull("the workout row must exist from the start", row)
        assertTrue("an in-flight ride must not count as complete", !row!!.isComplete)
    }

    @Test
    fun startingTwiceDoesNotReplaceTheRideInProgress() = runBlocking {
        startRide()
        val first = service.currentSession.value!!.workoutId
        startedWorkoutId = first

        service.startWorkout(null, null, RideIntent.DEFAULT, FTP)

        assertEquals(first, service.currentSession.value!!.workoutId)
    }

    // ── Recording ───────────────────────────────────────────────────

    @Test
    fun theRideAccumulatesElapsedTimeAndSamples() = runBlocking {
        startRide()
        startedWorkoutId = service.currentSession.value!!.workoutId

        awaitElapsedAtLeast(3)

        val snapshot = service.rideSnapshot.value
        assertTrue("elapsed should advance", snapshot.elapsedSeconds >= 3)
        assertTrue(
            "one sample per second",
            service.currentSession.value!!.sampleCount >= 3
        )
    }

    @Test
    fun pausingStopsTheClockAndResumingRestartsIt() = runBlocking {
        startRide()
        startedWorkoutId = service.currentSession.value!!.workoutId
        awaitElapsedAtLeast(2)

        service.pauseWorkout()
        assertEquals(WorkoutState.Paused, service.workoutState.value)
        val whenPaused = service.rideSnapshot.value.elapsedSeconds

        Thread.sleep(2_500)
        assertEquals(
            "paused time must not count towards the ride",
            whenPaused,
            service.rideSnapshot.value.elapsedSeconds
        )

        service.resumeWorkout()
        assertEquals(WorkoutState.Active, service.workoutState.value)
        awaitElapsedAtLeast(whenPaused + 2)
    }

    // ── Finish ──────────────────────────────────────────────────────

    @Test
    fun stoppingFinalisesTheRowAndItsMetrics() = runBlocking {
        startRide()
        val workoutId = service.currentSession.value!!.workoutId
        startedWorkoutId = workoutId
        awaitElapsedAtLeast(3)

        service.stopWorkout()
        assertEquals(WorkoutState.Completed, service.workoutState.value)

        val row = awaitCondition("the ride to be finalised") {
            ServiceLocator.workoutRepository.getWorkout(workoutId)?.takeIf { it.isComplete }
        }
        assertNotNull(row)
        assertTrue("a finished ride has a duration", row!!.durationSec >= 3)

        // The buffered tail must be flushed, not dropped: metrics are batched
        // fifteen at a time and a short ride never reaches a full batch.
        val metrics = ServiceLocator.workoutRepository.getMetrics(workoutId)
        assertTrue("the time series must survive the flush", metrics.size >= 3)
        assertEquals(
            "samples are recorded one per elapsed second",
            metrics.map { it.timestampSec }.sorted(),
            metrics.map { it.timestampSec }
        )
    }

    @Test
    fun aFinishedRideIsNoLongerOfferedForRecovery() = runBlocking {
        startRide()
        val workoutId = service.currentSession.value!!.workoutId
        startedWorkoutId = workoutId
        awaitElapsedAtLeast(2)

        service.stopWorkout()
        awaitCondition("the ride to be finalised") {
            ServiceLocator.workoutRepository.getWorkout(workoutId)?.takeIf { it.isComplete }
        }

        // Regression: the recovery query had no completion filter, so the app
        // offered to resume the ride you had just finished, on every launch.
        assertNull(ServiceLocator.workoutRepository.findRecoverableWorkout())
    }

    @Test
    fun stoppingWithoutStartingIsHarmless() {
        service.stopWorkout()

        assertEquals(WorkoutState.Idle, service.workoutState.value)
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private fun startRide() {
        service.startWorkout(
            userId = null,
            classId = null,
            intent = RideIntent.DEFAULT,
            ftpWatts = FTP
        )
    }

    private suspend fun awaitWorkoutRow(workoutId: String) =
        awaitCondition("the workout row to be written") {
            ServiceLocator.workoutRepository.getWorkout(workoutId)
        }

    private fun awaitElapsedAtLeast(seconds: Int) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (service.rideSnapshot.value.elapsedSeconds >= seconds) return
            Thread.sleep(POLL_MS)
        }
        throw AssertionError(
            "elapsed never reached ${seconds}s " +
                "(stuck at ${service.rideSnapshot.value.elapsedSeconds}s)"
        )
    }

    /** Polls a suspending producer until it yields non-null, or times out. */
    private suspend fun <T : Any> awaitCondition(what: String, produce: suspend () -> T?): T? {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            produce()?.let { return it }
            Thread.sleep(POLL_MS)
        }
        throw AssertionError("timed out waiting for $what")
    }

    private companion object {
        const val FTP = 200
        const val TIMEOUT_MS = 15_000L
        const val POLL_MS = 100L
    }
}
