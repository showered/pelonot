package com.pelonot.data.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CadenceTrackerTest {

    private val tracker = CadenceTracker()

    private data class Ride(val cadence: Double, val lastTickMs: Long)

    /** Feeds [count] evenly spaced ticks at [rpm], starting at [startMs]. */
    private fun ride(rpm: Double, count: Int, startMs: Long = 0L): Ride {
        val intervalMs = (60_000.0 / rpm).toLong()
        var cadence = 0.0
        var timestamp = startMs
        repeat(count) { index ->
            timestamp = startMs + index * intervalMs
            cadence = tracker.onTick(timestamp)
        }
        return Ride(cadence, timestamp)
    }

    @Test
    fun `converts a steady tick interval to RPM`() {
        // 90 RPM is one revolution every 667ms.
        assertEquals(90.0, ride(rpm = 90.0, count = 6).cadence, 1.0)
    }

    @Test
    fun `the first tick alone cannot establish a cadence`() {
        assertEquals(0.0, tracker.onTick(0L), 0.0)
    }

    @Test
    fun `smooths jitter across the averaging window`() {
        // Alternating fast and slow revolutions around 60 RPM.
        tracker.onTick(0)
        tracker.onTick(900)
        tracker.onTick(2000)
        tracker.onTick(2900)
        val cadence = tracker.onTick(4000)

        assertEquals(60.0, cadence, 4.0)
    }

    @Test
    fun `decays to zero when the rider stops pedalling`() {
        // Regression: cadence used to hold its last value indefinitely once
        // ticks stopped, so a stationary bike read 90 RPM and every derived
        // metric — power, distance, energy — kept accumulating.
        val (cadence, lastTickMs) = ride(rpm = 90.0, count = 6)
        assertTrue("no cadence established", cadence > 0.0)

        assertTrue(
            "cadence dropped too eagerly, mid-pedal-stroke",
            tracker.cadenceAt(lastTickMs + 500) > 0.0
        )
        assertEquals(
            "cadence never fell to zero after the rider stopped",
            0.0,
            tracker.cadenceAt(lastTickMs + 3_000),
            0.0
        )
    }

    @Test
    fun `resuming after a stop restarts the measurement cleanly`() {
        val (_, lastTickMs) = ride(rpm = 90.0, count = 5)

        // A long gap, then pedalling resumes. The gap must not be averaged in
        // as though it were one very slow revolution.
        val resumed = ride(rpm = 100.0, count = 6, startMs = lastTickMs + 60_000)

        assertEquals(100.0, resumed.cadence, 3.0)
    }

    @Test
    fun `rejects implausible readings from electrical noise`() {
        // Ticks 1ms apart would otherwise compute to 60,000 RPM.
        var cadence = 0.0
        for (i in 0..5) cadence = tracker.onTick(i.toLong())

        assertTrue("unclamped cadence $cadence", cadence <= 250.0)
    }

    @Test
    fun `ignores a clock going backwards`() {
        tracker.onTick(10_000)
        val cadence = tracker.onTick(9_000)

        assertEquals(0.0, cadence, 0.0)
    }

    @Test
    fun `reset clears all history`() {
        ride(rpm = 90.0, count = 6)
        tracker.reset()

        assertEquals(0.0, tracker.cadenceAt(0L), 0.0)
    }
}
