package com.pelonot.data.service

import com.pelonot.domain.model.MetricSample
import com.pelonot.domain.model.WorkoutAggregates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 8.3d — a resumed ride's row must describe the whole ride, not the part after
 * the crash.
 */
class WorkoutSessionResumeTest {

    private fun session() = WorkoutSession(
        workoutId = "w1",
        userId = 1,
        classId = "END-01",
        startedAtEpochMs = 0L
    )

    private fun samples(count: Int, power: Double, cadence: Double, hr: Int?) =
        (1..count).map { MetricSample(it, power, cadence, hr) }

    @Test
    fun `restoring carries the totals and the means forward`() {
        val aggregates = WorkoutAggregates.from(samples(60, power = 200.0, cadence = 80.0, hr = 140))

        val resumed = session().restoredWith(aggregates)

        assertEquals(60, resumed.elapsedSeconds)
        assertEquals(200.0, resumed.avgPower, 0.001)
        assertEquals(80.0, resumed.avgCadence, 0.001)
        assertEquals(140.0, resumed.avgHeartRateBpm!!, 0.001)
        assertEquals(60, resumed.sampleCount)
        assertEquals(60, resumed.heartRateSampleCount)
        assertEquals(aggregates.totalOutputKj, resumed.totalOutputKj, 0.001)
    }

    @Test
    fun `a reading after the resume is weighted against the samples already banked`() {
        // 60 samples at 200 W, then one at 400 W. The mean must move by
        // 200/61 ≈ 3.3 W, not jump to 300 as it would from a zeroed session.
        val aggregates = WorkoutAggregates.from(samples(60, power = 200.0, cadence = 80.0, hr = null))

        val resumed = session()
            .restoredWith(aggregates)
            .withSample(powerWatts = 400.0, cadenceRpm = 80.0, heartRateBpm = null)

        assertEquals(61, resumed.sampleCount)
        assertEquals(203.279, resumed.avgPower, 0.01)
    }

    @Test
    fun `heart rate is weighted by its own sample count, not the ride's`() {
        // The strap joined for the last 10 of 60 seconds. A new 180 bpm reading
        // is the 11th heart-rate sample, so it must move that mean by 1/11 —
        // weighting it 1/61 would make the strap's readings nearly invisible.
        val withoutStrap = samples(50, power = 200.0, cadence = 80.0, hr = null)
        val withStrap = (51..60).map { MetricSample(it, 200.0, 80.0, 100) }
        val aggregates = WorkoutAggregates.from(withoutStrap + withStrap)

        assertEquals(10, aggregates.heartRateSampleCount)

        val resumed = session()
            .restoredWith(aggregates)
            .withSample(powerWatts = 200.0, cadenceRpm = 80.0, heartRateBpm = 180)

        assertEquals(11, resumed.heartRateSampleCount)
        assertEquals(107.272, resumed.avgHeartRateBpm!!, 0.01)
    }

    @Test
    fun `a ride nobody wore a strap for resumes with no heart rate rather than zero`() {
        val aggregates = WorkoutAggregates.from(samples(30, power = 150.0, cadence = 70.0, hr = null))

        val resumed = session().restoredWith(aggregates)

        assertNull(resumed.avgHeartRateBpm)
        assertEquals(0, resumed.heartRateSampleCount)
    }

    @Test
    fun `the exact mean survives the round trip rather than being re-rounded`() {
        // Mean of 100 and 101 is 100.5. The Int column would make it 100, and a
        // ride resumed twice would lose half a beat each time.
        val aggregates = WorkoutAggregates.from(
            listOf(
                MetricSample(1, 200.0, 80.0, 100),
                MetricSample(2, 200.0, 80.0, 101)
            )
        )

        assertEquals(100, aggregates.avgHeartRate)
        assertEquals(100.5, aggregates.avgHeartRateExact!!, 0.001)
        assertEquals(100.5, session().restoredWith(aggregates).avgHeartRateBpm!!, 0.001)
    }
}
