package com.pelonot.domain.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

class PowerCurveFitterTest {

    /**
     * A plausible machine to generate synthetic rides from.
     *
     * Scaled to real figures rather than arbitrary ones — 85 rpm at 45%
     * resistance lands near 200 W, and full resistance at 100 rpm near 540 W —
     * so a fit that silently walks into the 2,500 W clamp fails here rather
     * than passing on numbers no bike produces.
     */
    private val truth = CalibratedPowerCurve(a = 0.35, b = 0.017, k = 1.25, c = 0.0000045)

    private fun gridFrom(
        curve: PowerCurve,
        resistances: List<Double>,
        cadences: List<Double>,
        noise: (Double) -> Double = { 0.0 }
    ): CalibrationGrid {
        var grid = CalibrationGrid()
        resistances.forEach { r ->
            cadences.forEach { rpm ->
                val watts = curve.watts(rpm, r)
                grid = grid.plus(CalibrationSample(rpm, r, watts + noise(watts)))
            }
        }
        return grid
    }

    @Test
    fun `it refuses to fit anything until the range has been ridden`() {
        val grid = gridFrom(truth, resistances = listOf(20.0, 30.0), cadences = listOf(70.0, 85.0))

        val outcome = PowerCurveFitter.fit(grid)
        assertTrue(outcome is FitOutcome.NotEnoughCoverage)
        assertEquals(
            CalibrationGrid.MIN_RESISTANCE_LEVELS,
            (outcome as FitOutcome.NotEnoughCoverage).levelsNeeded
        )
    }

    /**
     * Levels clustered together determine the exponent no better than one
     * level does, however many of them there are.
     */
    @Test
    fun `resistance levels bunched into a narrow band are not coverage`() {
        val grid = gridFrom(
            truth,
            resistances = listOf(30.0, 32.0, 34.0, 36.0, 38.0, 40.0, 42.0),
            cadences = listOf(65.0, 80.0, 95.0)
        )
        assertTrue(PowerCurveFitter.fit(grid) is FitOutcome.NotEnoughCoverage)
    }

    @Test
    fun `it recovers a machine it can actually see`() {
        val grid = gridFrom(
            truth,
            resistances = listOf(5.0, 18.0, 30.0, 45.0, 58.0, 72.0, 85.0, 95.0),
            cadences = listOf(55.0, 70.0, 85.0, 100.0)
        )

        val outcome = PowerCurveFitter.fit(grid)
        assertTrue("expected adoption, got $outcome", outcome is FitOutcome.Adopted)

        val fitted = (outcome as FitOutcome.Adopted).curve
        // Not the coefficients — several (a, b, k) combinations describe the
        // same surface. What has to match is the surface.
        listOf(40.0, 60.0, 90.0).forEach { rpm ->
            listOf(10.0, 50.0, 80.0).forEach { r ->
                val expected = truth.watts(rpm, r)
                val actual = fitted.watts(rpm, r)
                assertTrue(
                    "at ${rpm}rpm/${r}% expected ~$expected, got $actual",
                    abs(actual - expected) <= expected * 0.15 + 5.0
                )
            }
        }
    }

    /**
     * 2.2a.3, and the whole lesson of the 31 July sweep: a fit that cannot
     * predict a resistance level it never saw is interpolating between the
     * levels that happened to get ridden.
     */
    @Test
    fun `noise that swamps the signal is not adopted`() {
        val rng = java.util.Random(20260731)
        val grid = gridFrom(
            truth,
            resistances = listOf(5.0, 18.0, 30.0, 45.0, 58.0, 72.0, 85.0, 95.0),
            cadences = listOf(55.0, 70.0, 85.0, 100.0),
            // Each cell is displaced by up to ±120% of its own value, so the
            // grid describes no surface at all.
            noise = { watts -> (rng.nextDouble() - 0.5) * 2.4 * watts }
        )

        val outcome = PowerCurveFitter.fit(grid)
        assertTrue(
            "a fit to noise must not be adopted, got $outcome",
            outcome !is FitOutcome.Adopted
        )
    }

    @Test
    fun `an adopted curve is monotone in resistance`() {
        val grid = gridFrom(
            truth,
            resistances = listOf(5.0, 18.0, 30.0, 45.0, 58.0, 72.0, 85.0, 95.0),
            cadences = listOf(55.0, 70.0, 85.0, 100.0)
        )
        val fitted = (PowerCurveFitter.fit(grid) as FitOutcome.Adopted).curve

        // Turning the knob up must never be predicted to produce less power,
        // or the prescription in 11.2.1 would tell a rider to ease off in
        // order to work harder.
        var previous = -1.0
        for (r in 0..100 step 5) {
            val watts = fitted.watts(85.0, r.toDouble())
            assertTrue("not monotone at R=$r", watts >= previous)
            previous = watts
        }
    }

    @Test
    fun `the inverse round-trips through the fitted curve`() {
        val grid = gridFrom(
            truth,
            resistances = listOf(5.0, 18.0, 30.0, 45.0, 58.0, 72.0, 85.0, 95.0),
            cadences = listOf(55.0, 70.0, 85.0, 100.0)
        )
        val fitted = (PowerCurveFitter.fit(grid) as FitOutcome.Adopted).curve

        listOf(60.0, 80.0, 95.0).forEach { rpm ->
            listOf(15.0, 40.0, 70.0).forEach { r ->
                val watts = fitted.watts(rpm, r)
                val recovered = fitted.resistanceForWatts(watts, rpm)
                assertNotNull("no resistance for ${watts}W at ${rpm}rpm", recovered)
                assertEquals(r, recovered!!, 0.5)
            }
        }
    }

    @Test
    fun `an unreachable target reports no band rather than a clamped one`() {
        // More watts than the knob can produce at that cadence: the honest
        // instruction is "spin faster", not "set it to 100%".
        assertNull(truth.resistanceForWatts(2_000.0, 50.0))
        // And below the unloaded curve, where the answer is "ease off".
        assertNull(truth.resistanceForWatts(1.0, 100.0))
    }

    @Test
    fun `a curve with no resistance term has no usable inverse`() {
        val flat = CalibratedPowerCurve(a = 0.5, b = 0.0, k = 1.0, c = 0.0)
        assertNull(flat.resistanceForWatts(200.0, 85.0))
    }

    @Test
    fun `neither curve claims power from a stationary flywheel`() {
        assertEquals(0.0, ShippedPowerCurve.watts(0.0, 80.0), 0.0)
        assertEquals(0.0, truth.watts(0.0, 80.0), 0.0)
        assertEquals(0.0, truth.watts(5.0, 100.0), 0.0)
    }

    /**
     * Sanity check on the synthetic machine itself: if the shipped curve
     * already matched it, "the fit beat the shipped curve" would prove
     * nothing.
     */
    @Test
    fun `the shipped curve does not already describe the test machine`() {
        val errors = listOf(50.0, 70.0, 90.0).flatMap { rpm ->
            listOf(20.0, 50.0, 80.0).map { r ->
                val expected = truth.watts(rpm, r)
                abs(ShippedPowerCurve.watts(rpm, r) - expected) / expected
            }
        }
        assertTrue(
            "shipped curve is suspiciously close to the synthetic machine",
            errors.average() > 0.2
        )
    }

    @Test
    fun `the fitted form is linear in cadence at fixed resistance, plus a cubic`() {
        // Guards the algebra the inverse depends on.
        val curve = CalibratedPowerCurve(a = 0.4, b = 0.015, k = 1.5, c = 0.000005)
        val rpm = 90.0
        val r = 60.0
        val expected = (0.4 + 0.015 * r.pow(1.5)) * rpm + 0.000005 * rpm.pow(3)
        assertEquals(expected, curve.watts(rpm, r), 0.0001)
    }
}
