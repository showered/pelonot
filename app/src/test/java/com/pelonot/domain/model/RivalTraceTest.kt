package com.pelonot.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertNull(trace.valueAt(0))
        assertEquals(0.0, trace.finalValue, 0.0001)
    }

    // ── The race is metric-agnostic (24.3.14) ───────────────────────

    @Test
    fun `a distance race integrates the same samples into kilometres`() {
        // The owner's ask was the *shape*, not only the score: "let's not rule
        // out racing by OTHER metrics too, such as distance". Same trapezoid,
        // same gap clamp, same samples — only the quantity differs, and the
        // proof of that is that both agree with WorkoutAggregates.
        val samples = ride(600, power = 200.0)
        val aggregates = WorkoutAggregates.from(samples)

        val distance = RivalTrace.from(samples, RaceMetric.Distance)

        assertEquals(RaceMetric.Distance, distance.metric)
        assertEquals(aggregates.distanceKm, distance.finalValue, 0.0001)
        assertEquals(aggregates.durationSec, distance.finalSecond)
    }

    @Test
    fun `the gap carries the metric it was measured in`() {
        // Nothing downstream may assume kilojoules. The ride screen picks its
        // formatter off this, so a status that lost its metric would print
        // kilometres with a kJ label.
        val trace = RivalTrace.from(ride(600), RaceMetric.Distance)

        assertEquals(RaceMetric.Distance, trace.statusAt(300, 1.0, "Alex").metric)
        assertEquals(
            RaceMetric.Output,
            RivalTrace.from(ride(600)).statusAt(300, 1.0, "Alex").metric
        )
    }

    @Test
    fun `only an output race needs the power to have been measured`() {
        // The non-obvious half of why this enum earns its place: distance is
        // integrated cadence, which is measured on every ride this app has
        // recorded — so a distance race works on rides 24.4.2 has to exclude.
        assertTrue(RaceMetric.Output.requiresMeasuredPower)
        assertFalse(RaceMetric.Distance.requiresMeasuredPower)
    }

    @Test
    fun `the final point matches WorkoutAggregates for the same samples`() {
        // Same trapezoidal rule, same input — the two must agree, because a
        // finished ride's own total is read off WorkoutAggregates and the
        // ghost must not silently disagree with the ride it is drawn from.
        val samples = ride(600, power = 200.0)

        val trace = RivalTrace.from(samples)
        val aggregates = WorkoutAggregates.from(samples)

        assertEquals(aggregates.totalOutputKj, trace.finalValue, 0.0001)
        assertEquals(aggregates.durationSec, trace.finalSecond)
    }

    @Test
    fun `valueAt returns the cumulative total recorded by that second`() {
        // 200 W held for 300 s is 60 kJ.
        val trace = RivalTrace.from(ride(600, power = 200.0))

        assertEquals(60.0, trace.valueAt(300)!!, 0.01)
    }

    @Test
    fun `a ghost that runs out returns null past its last second, never extrapolated`() {
        val trace = RivalTrace.from(ride(600, power = 200.0))

        assertNull(trace.valueAt(601))
        assertNull(trace.valueAt(10_000))
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
        assertEquals(1.0, trace.valueAt(5)!!, 0.001)
        assertEquals(1.0, trace.valueAt(12)!!, 0.001)
    }

    @Test
    fun `statusAt reports how far ahead or behind you are at the same second`() {
        val trace = RivalTrace.from(ride(600, power = 200.0))

        val ahead = trace.statusAt(second = 300, yourValue = 70.0, rivalName = "Kilo")
        assertEquals("Kilo", ahead.rivalName)
        assertEquals(10.0, ahead.gap, 0.01)
        assertTrue(!ahead.rivalFinished)

        val behind = trace.statusAt(second = 300, yourValue = 50.0, rivalName = "Kilo")
        assertEquals(-10.0, behind.gap, 0.01)
    }

    @Test
    fun `once the rival has finished the gap freezes at their final total`() {
        val trace = RivalTrace.from(ride(600, power = 200.0)) // finishes at 120 kJ

        val status = trace.statusAt(second = 900, yourValue = 130.0, rivalName = "Kilo")

        assertTrue(status.rivalFinished)
        assertEquals(10.0, status.gap, 0.01)
    }
}
