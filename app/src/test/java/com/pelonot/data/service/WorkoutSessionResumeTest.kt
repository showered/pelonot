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
    fun `restoring does not wipe the interruption the row was stamped with`() {
        // The defect this pins: `resumeInterruptedWorkout` stamps resume_count
        // on `workouts`, and `stopWorkout` then finalises the ride by building
        // a fresh entity out of the session. Anything the session does not
        // carry is written back as its default — so a ride observed to resume
        // twice ended up on disk claiming it was ridden straight through.
        // Found in the database, not on any screen.
        val aggregates = WorkoutAggregates.from(samples(60, power = 200.0, cadence = 80.0, hr = null))

        val resumed = session()
            .copy(resumeCount = 2, interruptedSec = 494)
            .restoredWith(aggregates)

        assertEquals(2, resumed.resumeCount)
        assertEquals(494, resumed.interruptedSec)
    }

    @Test
    fun `a ride that was never interrupted still says so`() {
        val plain = session()

        assertEquals(0, plain.resumeCount)
        assertEquals(0, plain.interruptedSec)
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

    @Test
    fun `a resume weights new samples against the pedalling count, not the tick count`() {
        // 19.1.2b's half of 8.3d. The first half of the ride was 40 s of riding
        // and 20 s of standing still; the mean it carries forward was built on
        // 40 samples, so one 400 W second afterwards must move it by 200/41,
        // not by 200/61. Dividing by the tick count would silently under-weight
        // every second ridden after the pick-up.
        val interrupted = samples(40, power = 200.0, cadence = 80.0, hr = null) +
            (41..60).map { MetricSample(it, 0.0, 0.0, null) }

        val aggregates = WorkoutAggregates.from(interrupted)
        assertEquals(60, aggregates.sampleCount)
        assertEquals(40, aggregates.pedallingSampleCount)

        val resumed = session()
            .restoredWith(aggregates)
            .withSample(powerWatts = 400.0, cadenceRpm = 80.0, heartRateBpm = null)

        assertEquals(41, resumed.pedallingSampleCount)
        assertEquals(200.0 + 200.0 / 41.0, resumed.avgPower, 0.001)
    }

    @Test
    fun `the live path and the cold path agree across a stop`() {
        // The two are separate implementations of one definition, and 8.3d's
        // whole premise is that a recovered ride is comparable with one that
        // finished normally. A stop is where they would most easily diverge.
        val series = samples(30, power = 210.0, cadence = 95.0, hr = 150) +
            (31..50).map { MetricSample(it, 0.0, 0.0, 130) } +
            (51..80).map { MetricSample(it, 190.0, 85.0, 155) }

        val cold = WorkoutAggregates.from(series)
        val live = series.fold(session()) { acc, sample ->
            acc.withSample(sample.power, sample.cadence, sample.heartRate)
        }

        assertEquals(cold.avgPower, live.avgPower, 1e-9)
        assertEquals(cold.avgCadence, live.avgCadence, 1e-9)
        assertEquals(cold.avgHeartRateExact!!, live.avgHeartRateBpm!!, 1e-9)
        assertEquals(cold.pedallingSampleCount, live.pedallingSampleCount)
    }
}
