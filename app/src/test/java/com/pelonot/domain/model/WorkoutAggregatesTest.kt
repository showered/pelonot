package com.pelonot.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutAggregatesTest {

    private fun ride(
        seconds: Int,
        power: Double = 200.0,
        cadence: Double = 90.0,
        heartRate: Int? = 150
    ) = (0..seconds).map { MetricSample(it, power, cadence, heartRate) }

    @Test
    fun `an empty series aggregates to nothing`() {
        val aggregates = WorkoutAggregates.from(emptyList())

        assertTrue(aggregates.isEmpty)
        assertEquals(0, aggregates.durationSec)
        assertEquals(0.0, aggregates.totalOutputKj, 0.0001)
    }

    @Test
    fun `duration is the last recorded second`() {
        assertEquals(600, WorkoutAggregates.from(ride(600)).durationSec)
    }

    @Test
    fun `energy is power integrated over time, not summed per sample`() {
        // 200 W held for 600 s is 120 kJ. The live calculator once multiplied by
        // sample *count* instead of elapsed time and over-reported by three
        // orders of magnitude; this is the cold path and must not repeat it.
        val aggregates = WorkoutAggregates.from(ride(600, power = 200.0))

        assertEquals(120.0, aggregates.totalOutputKj, 0.01)
    }

    @Test
    fun `averages are means over the samples`() {
        val mixed = listOf(
            MetricSample(0, power = 100.0, cadence = 80.0, heartRate = 120),
            MetricSample(1, power = 200.0, cadence = 90.0, heartRate = 140),
            MetricSample(2, power = 300.0, cadence = 100.0, heartRate = 160)
        )

        val aggregates = WorkoutAggregates.from(mixed)

        assertEquals(200.0, aggregates.avgPower, 0.001)
        assertEquals(90.0, aggregates.avgCadence, 0.001)
        assertEquals(140, aggregates.avgHeartRate)
        assertEquals(3, aggregates.pedallingSampleCount)
    }

    @Test
    fun `the seconds the rider was stopped are not averaged in`() {
        // 19.1.2b, on the cold path. The stopped rows are kept — `sampleCount`
        // and `durationSec` still see them, and every chart draws them — but
        // they are not part of what the rider averaged.
        val withStop = (0..65).map { MetricSample(it, 180.0, 90.0, 150) } +
            (66..85).map { MetricSample(it, 0.0, 0.0, 120) }

        val aggregates = WorkoutAggregates.from(withStop)

        assertEquals(180.0, aggregates.avgPower, 0.001)
        assertEquals(90.0, aggregates.avgCadence, 0.001)
        assertEquals(86, aggregates.sampleCount)
        assertEquals(66, aggregates.pedallingSampleCount)
        assertEquals(85, aggregates.durationSec)
        // The heart rate during the stop is a real heart rate and is kept, so
        // its mean sits below the riding figure rather than equalling it.
        assertEquals(143, aggregates.avgHeartRate)
    }

    @Test
    fun `a series of nothing but stillness averages to zero rather than to NaN`() {
        val stillness = (0..30).map { MetricSample(it, 0.0, 0.0, 70) }

        val aggregates = WorkoutAggregates.from(stillness)

        assertEquals(0.0, aggregates.avgPower, 0.001)
        assertEquals(0.0, aggregates.avgCadence, 0.001)
        assertEquals(0, aggregates.pedallingSampleCount)
    }

    @Test
    fun `samples without a heart rate are left out rather than counted as zero`() {
        val patchy = listOf(
            MetricSample(0, 200.0, 90.0, heartRate = null),
            MetricSample(1, 200.0, 90.0, heartRate = 150),
            MetricSample(2, 200.0, 90.0, heartRate = null)
        )

        // A rider with no strap is not a rider with no pulse.
        assertEquals(150, WorkoutAggregates.from(patchy).avgHeartRate)
    }

    @Test
    fun `a ride with no strap at all reports an unknown heart rate`() {
        assertNull(WorkoutAggregates.from(ride(60, heartRate = null)).avgHeartRate)
    }

    @Test
    fun `a long gap in the series is clamped rather than integrated through`() {
        // The app was killed for ten minutes. Those minutes were not ridden.
        val withGap = listOf(
            MetricSample(0, 300.0, 90.0, null),
            MetricSample(600, 300.0, 90.0, null)
        )

        val aggregates = WorkoutAggregates.from(withGap)

        // Clamped to a 5s step: 300 W x 5 s = 1.5 kJ, not 180 kJ.
        assertEquals(1.5, aggregates.totalOutputKj, 0.001)
    }

    @Test
    fun `distance accumulates from cadence`() {
        // 90 rpm for 600 s is 900 revolutions at 2.1 m each.
        val aggregates = WorkoutAggregates.from(ride(600, cadence = 90.0))

        assertEquals(900 * 0.0021, aggregates.distanceKm, 0.01)
    }

    @Test
    fun `an out-of-order series is sorted before it is integrated`() {
        val shuffled = listOf(
            MetricSample(2, 200.0, 90.0, null),
            MetricSample(0, 200.0, 90.0, null),
            MetricSample(1, 200.0, 90.0, null)
        )

        val aggregates = WorkoutAggregates.from(shuffled)

        assertEquals(2, aggregates.durationSec)
        assertEquals(0.4, aggregates.totalOutputKj, 0.001)
    }

    @Test
    fun `a single sample has duration but no integrated energy`() {
        val aggregates = WorkoutAggregates.from(listOf(MetricSample(0, 250.0, 95.0, 140)))

        assertEquals(1, aggregates.sampleCount)
        assertEquals(0.0, aggregates.totalOutputKj, 0.0001)
        assertEquals(250.0, aggregates.avgPower, 0.001)
    }
}
