package com.pelonot.data.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** PLAN 11.6.20 — the watts a standing rider sees, and only the watts. */
class PowerSmootherTest {

    private val smoother = PowerSmoother(windowMs = 3_000L)

    private fun reading(second: Double, watts: Double) = SensorReading(
        powerWatts = watts,
        cadenceRpm = 60.0,
        resistancePercent = 55.0,
        heartRateBpm = 148,
        powerIsMeasured = true,
        timestampMs = START_MS + (second * 1000).toLong()
    )

    @Test
    fun `a standing rider's pedal stroke stops swinging the number`() {
        // The owner's case: two hertz of alternating high and low watts at
        // 60 rpm out of the saddle. The raw stream swings 100 W either side of
        // the effort; four seconds in, the display must not.
        var last = 0.0
        var second = 0.0
        while (second <= 4.0) {
            val watts = if ((second * 4).toInt() % 2 == 0) 350.0 else 150.0
            last = smoother.smooth(reading(second, watts)).powerWatts
            second += 0.25
        }

        assertEquals("the effort is 250 W and that is what should be drawn", 250.0, last, 15.0)
    }

    @Test
    fun `nothing but the power is touched`() {
        // Cadence does not pulse, resistance moves when a hand moves it, and a
        // heart rate is nullable — a mean of any of the three would be a lie
        // with no complaint behind it.
        smoother.smooth(reading(0.0, 100.0))
        val out = smoother.smooth(reading(1.0, 300.0))

        assertEquals(60.0, out.cadenceRpm, 0.0)
        assertEquals(55.0, out.resistancePercent, 0.0)
        assertEquals(148, out.heartRateBpm)
        assertTrue(out.powerIsMeasured)
        assertEquals(START_MS + 1000, out.timestampMs)
    }

    @Test
    fun `the first reading is drawn as it arrived`() {
        // A rider who has just started pedalling must not watch the number
        // climb out of an average of one sample and a history that is not
        // theirs. One sample in, the mean is that sample.
        assertEquals(180.0, smoother.smooth(reading(0.0, 180.0)).powerWatts, 0.0)
    }

    @Test
    fun `a dropout is not averaged across`() {
        // Ten seconds of silence and then a reading is the first of a new
        // effort, not the continuation of an old one. Carrying the pre-dropout
        // mean into it would draw the watts of a rider who had stopped — the
        // same argument as the gap clamp and as isStaleAt.
        smoother.smooth(reading(0.0, 400.0))
        smoother.smooth(reading(0.5, 400.0))

        assertEquals(90.0, smoother.smooth(reading(10.5, 90.0)).powerWatts, 0.0)
    }

    @Test
    fun `the window only ever holds the last few seconds`() {
        // Otherwise the number stops answering "what am I doing now", which is
        // the question the tile exists for.
        for (second in 0..9) smoother.smooth(reading(second.toDouble(), 100.0))
        val out = smoother.smooth(reading(10.0, 400.0))

        // The window is 3 s at 1 Hz: three 100s and the new 400.
        assertEquals(175.0, out.powerWatts, 0.001)
    }

    @Test
    fun `a clock that goes backwards starts again`() {
        // A new ride, or a device clock change. Averaging across it would mix
        // two rides' watts into one number.
        smoother.smooth(reading(50.0, 300.0))

        assertEquals(80.0, smoother.smooth(reading(0.0, 80.0)).powerWatts, 0.0)
    }

    private companion object {
        const val START_MS = 1_700_000_000_000L
    }
}
