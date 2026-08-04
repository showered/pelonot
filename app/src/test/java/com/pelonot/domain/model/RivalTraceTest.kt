package com.pelonot.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RivalTraceTest {

    private fun ride(seconds: Int, power: Double = 200.0) =
        (0..seconds).map { MetricSample(it, power, cadence = 90.0, heartRate = 150) }

    @Test
    fun `an empty rival has nothing to compare against`() {
        val trace = RivalTrace.from(emptyList())

        assertTrue(trace.isEmpty)
        assertNull(trace.kjAt(0))
        assertEquals(0.0, trace.finalKj, 0.0001)
    }

    @Test
    fun `the final point matches WorkoutAggregates for the same samples`() {
        // Same trapezoidal rule, same input — the two must agree, because a
        // finished ride's own total is read off WorkoutAggregates and the
        // ghost must not silently disagree with the ride it is drawn from.
        val samples = ride(600, power = 200.0)

        val trace = RivalTrace.from(samples)
        val aggregates = WorkoutAggregates.from(samples)

        assertEquals(aggregates.totalOutputKj, trace.finalKj, 0.0001)
        assertEquals(aggregates.durationSec, trace.finalSecond)
    }

    @Test
    fun `kjAt returns the cumulative total recorded by that second`() {
        // 200 W held for 300 s is 60 kJ.
        val trace = RivalTrace.from(ride(600, power = 200.0))

        assertEquals(60.0, trace.kjAt(300)!!, 0.01)
    }

    @Test
    fun `a ghost that runs out returns null past its last second, never extrapolated`() {
        val trace = RivalTrace.from(ride(600, power = 200.0))

        assertNull(trace.kjAt(601))
        assertNull(trace.kjAt(10_000))
    }

    @Test
    fun `between two recorded seconds the last known total holds, not an interpolation`() {
        val withGap = listOf(
            MetricSample(0, power = 200.0, cadence = 90.0, heartRate = null),
            MetricSample(5, power = 200.0, cadence = 90.0, heartRate = null),
            MetricSample(20, power = 200.0, cadence = 90.0, heartRate = null)
        )
        val trace = RivalTrace.from(withGap)

        // Clamped to a 5 s step between 5 and 20, same as WorkoutAggregates:
        // 200 W x 5 s = 1.0 kJ total by second 5, and nothing more is credited
        // for the missing seconds in between.
        assertEquals(1.0, trace.kjAt(5)!!, 0.001)
        assertEquals(1.0, trace.kjAt(12)!!, 0.001)
    }

    @Test
    fun `statusAt reports how far ahead or behind you are at the same second`() {
        val trace = RivalTrace.from(ride(600, power = 200.0))

        val ahead = trace.statusAt(second = 300, yourKj = 70.0, rivalName = "Kilo")
        assertEquals("Kilo", ahead.rivalName)
        assertEquals(10.0, ahead.gapKj, 0.01)
        assertTrue(!ahead.rivalFinished)

        val behind = trace.statusAt(second = 300, yourKj = 50.0, rivalName = "Kilo")
        assertEquals(-10.0, behind.gapKj, 0.01)
    }

    @Test
    fun `once the rival has finished the gap freezes at their final total`() {
        val trace = RivalTrace.from(ride(600, power = 200.0)) // finishes at 120 kJ

        val status = trace.statusAt(second = 900, yourKj = 130.0, rivalName = "Kilo")

        assertTrue(status.rivalFinished)
        assertEquals(10.0, status.gapKj, 0.01)
    }
}
