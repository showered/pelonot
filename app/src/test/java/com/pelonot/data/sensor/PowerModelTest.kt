package com.pelonot.data.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    // ── The inverse: what should I set the knob to? ─────────────────

    @Test
    fun `resistance for a target round-trips through the forward model`() {
        val cadence = 90.0
        val target = 220.0

        val resistance = PowerModel.resistanceForWatts(target, cadence)!!

        assertEquals(target, PowerModel.estimateWatts(cadence, resistance), 0.01)
    }

    @Test
    fun `a harder target needs more resistance at the same cadence`() {
        val easy = PowerModel.resistanceForWatts(150.0, 90.0)!!
        val hard = PowerModel.resistanceForWatts(250.0, 90.0)!!

        assertTrue(hard > easy)
    }

    @Test
    fun `the same target needs less resistance at a higher cadence`() {
        val slow = PowerModel.resistanceForWatts(200.0, 80.0)!!
        val fast = PowerModel.resistanceForWatts(200.0, 100.0)!!

        assertTrue(fast < slow)
    }

    @Test
    fun `a target that needs more than the knob can give is unreachable`() {
        // 200 W at 70 rpm would need about 164% resistance on this curve. The
        // honest answer is "spin faster", not "turn it all the way up".
        assertNull(PowerModel.resistanceForWatts(200.0, 70.0))
    }

    @Test
    fun `an unreachable target has no resistance rather than a clamped one`() {
        // 800 W at 60 rpm is not a knob problem. Reporting "100%" would be a
        // lie the rider would act on.
        assertNull(PowerModel.resistanceForWatts(800.0, 60.0))
        // And nothing produces power on a flywheel that is barely turning.
        assertNull(PowerModel.resistanceForWatts(200.0, 15.0))
    }

    @Test
    fun `a target below what the legs alone produce needs no resistance at all`() {
        // At 100 rpm the unloaded curve already exceeds 50 W, so there is no
        // non-negative resistance that lands on it.
        assertNull(PowerModel.resistanceForWatts(50.0, 100.0))
    }

    @Test
    fun `a nonsensical target is rejected`() {
        assertNull(PowerModel.resistanceForWatts(0.0, 90.0))
        assertNull(PowerModel.resistanceForWatts(-10.0, 90.0))
    }
}
