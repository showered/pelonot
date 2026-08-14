package com.pelonot.data.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.abs

/**
 * Covers the running means a ride accumulates as it goes.
 *
 * These exist because of a defect found only by putting a heart-rate strap on
 * a real ride and comparing `workouts.avg_hr` against the mean of that ride's
 * own `workout_metrics` rows: the stored average was 79 bpm — the lowest
 * reading of the warm-up — where the true mean was 105.4. Nothing on any
 * screen could have shown that, and `avg_power` and `avg_cadence` beside it
 * were both exactly right.
 */
class WorkoutSessionTest {

    private fun session() = WorkoutSession(
        workoutId = "w1",
        userId = 1,
        classId = null,
        startedAtEpochMs = 0L
    )

    private fun WorkoutSession.feedHeartRates(values: List<Int>): WorkoutSession =
        values.fold(this) { acc, bpm ->
            acc.withSample(powerWatts = 100.0, cadenceRpm = 80.0, heartRateBpm = bpm)
        }

    @Test
    fun `heart rate average keeps moving late in a long ride`() {
        // The original bug in miniature: a low warm-up, then a long hard
        // effort. Rounding the mean to an Int on every tick froze it as soon
        // as one sample moved it by less than 1 bpm, so the climb never
        // registered at all.
        val warmUp = List(60) { 79 }
        val effort = List(240) { 125 }

        val result = session().feedHeartRates(warmUp + effort)

        val expected = (warmUp + effort).average()
        assertEquals(expected, result.avgHeartRateBpm!!, 1e-9)
        // Sanity: the true mean is nowhere near the warm-up value it used to stick at.
        assertEquals(115.8, result.avgHeartRateBpm!!, 0.1)
        assertEquals(116, result.avgHeartRate)
    }

    @Test
    fun `a strap connecting late is not diluted by the ticks it missed`() {
        // 100 ticks with no strap, then 100 real readings at 150 bpm. The
        // average of what was actually measured is 150 — dividing by the tick
        // count instead would drag it toward the first reading and report
        // something in the 120s.
        var s = session()
        repeat(100) {
            s = s.withSample(powerWatts = 100.0, cadenceRpm = 80.0, heartRateBpm = null)
        }
        s = s.feedHeartRates(List(100) { 150 })

        assertEquals(150.0, s.avgHeartRateBpm!!, 1e-9)
        assertEquals(100, s.heartRateSampleCount)
        assertEquals(200, s.sampleCount)
    }

    @Test
    fun `no strap leaves the average null rather than zero`() {
        var s = session()
        repeat(50) {
            s = s.withSample(powerWatts = 100.0, cadenceRpm = 80.0, heartRateBpm = null)
        }

        // Null means unknown. A 0 here would be written into the rider's
        // permanent record as a genuine sample.
        assertNull(s.avgHeartRateBpm)
        assertNull(s.avgHeartRate)
        assertEquals(0, s.heartRateSampleCount)
    }

    @Test
    fun `a gap in strap coverage averages only the readings that arrived`() {
        var s = session().feedHeartRates(List(30) { 100 })
        repeat(30) {
            s = s.withSample(powerWatts = 100.0, cadenceRpm = 80.0, heartRateBpm = null)
        }
        s = s.feedHeartRates(List(30) { 140 })

        assertEquals(120.0, s.avgHeartRateBpm!!, 1e-9)
        assertEquals(60, s.heartRateSampleCount)
        assertEquals(90, s.sampleCount)
    }

    @Test
    fun `power and cadence averages match a plain mean of the pedalling seconds`() {
        // The first sample is a standing start: cadence 0, and 19.1.2b says it
        // is not part of "what did I average". Every other second is.
        val powers = listOf(50.0, 120.0, 300.0, 0.0, 175.5)
        val cadences = listOf(0.0, 60.0, 95.0, 110.0, 82.5)

        val result = powers.indices.fold(session()) { acc, i ->
            acc.withSample(powers[i], cadences[i], heartRateBpm = null)
        }

        assertEquals(powers.drop(1).average(), result.avgPower, 1e-9)
        assertEquals(cadences.drop(1).average(), result.avgCadence, 1e-9)
        // Every second is still counted; only the denominator differs.
        assertEquals(powers.size, result.sampleCount)
        assertEquals(powers.size - 1, result.pedallingSampleCount)
    }

    @Test
    fun `a bottle stop does not drag the averages down`() {
        // 19.1.2b, in the shape it was measured: 66 seconds of riding and then
        // 20 seconds of stillness — the window an auto-pause has to watch
        // before it can know a stop is a stop. Those 20 rows are recorded and
        // drawn; they are not averaged in.
        val riding = List(66) { 90.0 }
        val stopped = List(20) { 0.0 }

        val result = (riding + stopped).fold(session()) { acc, cadence ->
            acc.withSample(
                powerWatts = if (cadence > 0) 180.0 else 0.0,
                cadenceRpm = cadence,
                heartRateBpm = null
            )
        }

        assertEquals(90.0, result.avgCadence, 1e-9)
        assertEquals(180.0, result.avgPower, 1e-9)
        assertEquals(86, result.sampleCount)
        assertEquals(66, result.pedallingSampleCount)
    }

    @Test
    fun `a heart rate recorded while stopped still counts`() {
        // The asymmetry is deliberate and is the whole of the rule: cadence and
        // power measure the riding, and there is none while the cranks are
        // still; a heart rate measures the rider, who is still there.
        val result = (1..4).fold(session()) { acc, i ->
            acc.withSample(
                powerWatts = 0.0,
                cadenceRpm = 0.0,
                heartRateBpm = 100 + i
            )
        }

        assertEquals(102.5, result.avgHeartRateBpm!!, 1e-9)
        assertEquals(4, result.heartRateSampleCount)
        assertEquals(0, result.pedallingSampleCount)
        // And with nothing to average, the averages stay at their honest zero
        // rather than becoming NaN.
        assertEquals(0.0, result.avgPower, 1e-9)
        assertEquals(0.0, result.avgCadence, 1e-9)
    }

    @Test
    fun `the mean does not drift over a full length ride`() {
        // 90 minutes at one sample a second. Welford should not accumulate
        // meaningful error over 5,400 samples.
        val values = List(5_400) { 60 + (it % 90) }

        val result = session().feedHeartRates(values)

        assertEquals(values.average(), result.avgHeartRateBpm!!, 1e-6)
        // And the increment never vanishes the way truncation made it.
        assert(abs(result.avgHeartRateBpm!! - 79.0) > 20.0)
    }
}
