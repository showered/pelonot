package com.pelonot.data.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterisation tests for the power curve.
 *
 * These pin down the model's *shape* — monotonicity, bounds, coasting
 * behaviour — rather than specific wattages, because the coefficients
 * themselves are unvalidated (see [PowerModel]'s documentation and PLAN item
 * 2.2). If the curve is later calibrated against a real power meter, these
 * should still hold.
 */
class PowerModelTest {

    @Test
    fun `a stationary flywheel produces no power`() {
        assertEquals(0.0, PowerModel.estimateWatts(cadenceRpm = 0.0, resistancePercent = 50.0), 0.0)
    }

    @Test
    fun `coasting below the cadence floor produces no power`() {
        assertEquals(0.0, PowerModel.estimateWatts(cadenceRpm = 5.0, resistancePercent = 80.0), 0.0)
    }

    @Test
    fun `power rises with cadence at a fixed resistance`() {
        var previous = -1.0
        for (rpm in 20..140 step 5) {
            val watts = PowerModel.estimateWatts(rpm.toDouble(), resistancePercent = 50.0)
            assertTrue("power fell going from below ${rpm}rpm", watts >= previous)
            previous = watts
        }
    }

    @Test
    fun `power rises with resistance at a fixed cadence`() {
        var previous = -1.0
        for (resistance in 0..100 step 10) {
            val watts = PowerModel.estimateWatts(cadenceRpm = 90.0, resistancePercent = resistance.toDouble())
            assertTrue("power fell going to resistance $resistance", watts >= previous)
            previous = watts
        }
    }

    @Test
    fun `a typical endurance effort lands in a plausible range`() {
        // 90 RPM at middling resistance should read as a real but sustainable
        // effort, not 5W and not 900W.
        val watts = PowerModel.estimateWatts(cadenceRpm = 90.0, resistancePercent = 50.0)

        assertTrue("implausibly low: $watts", watts > 80.0)
        assertTrue("implausibly high: $watts", watts < 400.0)
    }

    @Test
    fun `never returns a negative value`() {
        for (rpm in 0..200 step 5) {
            for (resistance in 0..100 step 10) {
                val watts = PowerModel.estimateWatts(rpm.toDouble(), resistance.toDouble())
                assertTrue("negative power at ${rpm}rpm/$resistance%", watts >= 0.0)
            }
        }
    }

    @Test
    fun `clamps a resistance reading outside the valid range`() {
        // The sensor board reports a raw byte; a glitch must not scale power
        // by an arbitrary factor.
        val atMax = PowerModel.estimateWatts(cadenceRpm = 90.0, resistancePercent = 100.0)
        val beyondMax = PowerModel.estimateWatts(cadenceRpm = 90.0, resistancePercent = 255.0)

        assertEquals(atMax, beyondMax, 0.001)
    }

    @Test
    fun `caps implausible output from a noisy cadence reading`() {
        val watts = PowerModel.estimateWatts(cadenceRpm = 5_000.0, resistancePercent = 100.0)

        assertTrue("uncapped: $watts", watts <= 2_500.0)
    }
}
