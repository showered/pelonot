package com.pelonot.domain.calibration

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The fitter run against the only measured data this project actually has:
 * `calibration/2026-07-31-sweep-PLTN-RB1VQ.csv`, 494 seconds off a real Gen 1
 * board.
 *
 * This is the test that matters most here, because every other test in this
 * package invents its machine. The sweep is the one thing that can say whether
 * the gates in [PowerCurveFitter] behave on real data, and the answer it has to
 * give is already known from the analysis in `calibration/README.md`:
 *
 * > A refit reaches median 10.7% *in sample*, but holding out one resistance
 * > level at a time gives 13–25%, and at R=40 the shipped coefficients beat the
 * > refit outright. The sweep has effectively six distinct levels, mostly
 * > between 20 and 75, and the exponent is poorly determined by that.
 *
 * So the sweep **must not clear the coverage gate**. If it ever does, 2.2a.4's
 * thresholds have drifted below the amount of evidence that was already known
 * to be insufficient, and the auto-calibration would ship exactly the fit that
 * was deliberately not shipped in 2.2.5.
 */
class RealSweepCalibrationTest {

    @Test
    fun `the 31 July sweep is not enough evidence to recalibrate on`() {
        val samples = loadSweep() ?: return

        val grid = CalibrationGrid().plusRide(samples)

        // The sweep is real riding, so a good deal of it survives the filters.
        assertTrue(
            "expected a usable number of steady-state samples, got ${grid.sampleCount}",
            grid.sampleCount > 150
        )

        val outcome = PowerCurveFitter.fit(grid)
        assertTrue(
            "The 31 July sweep failed cross-validation when analysed by hand " +
                "(calibration/README.md). If it now produces a curve, the coverage " +
                "or accuracy gates have been loosened past what that analysis says " +
                "is sufficient. Got: $outcome",
            outcome !is FitOutcome.Adopted
        )
    }

    /**
     * And the reason it is refused is the honest one: not enough of the
     * resistance range was ridden. The README's list of what a sufficient
     * sweep needs is a list of coverage gaps — 5–20 and 80–100 barely touched,
     * six distinct levels where the exponent needs more.
     */
    @Test
    fun `and it is refused for want of coverage, not for want of accuracy`() {
        val samples = loadSweep() ?: return
        val grid = CalibrationGrid().plusRide(samples)

        assertTrue(
            "the sweep should be short of resistance coverage, not merely inaccurate",
            PowerCurveFitter.fit(grid) is FitOutcome.NotEnoughCoverage
        )
    }

    /**
     * The sweep's own headline finding, kept as a regression: the shipped
     * coefficients are not approximately right, they are wrong by more than
     * half.
     */
    @Test
    fun `the shipped curve is still badly wrong on this bike`() {
        val samples = loadSweep() ?: return
        val steady = CalibrationGrid.steadyStateOf(samples).filter { it.isUsable() }
        assumeTrue(steady.isNotEmpty())

        val errors = steady.map { sample ->
            val predicted = ShippedPowerCurve.watts(sample.cadenceRpm, sample.resistancePercent)
            kotlin.math.abs(predicted - sample.measuredWatts) / sample.measuredWatts * 100.0
        }.sorted()
        val median = errors[errors.size / 2]

        assertTrue(
            "calibration/README.md records a median absolute error near 66%; got $median%",
            median > 30.0
        )
    }

    /**
     * Resolved by walking up from the module directory, so the test works
     * whether Gradle runs it from `app/` or from the repository root. Returns
     * null — and the test passes vacuously — when the data is not checked out,
     * because a missing dataset is not a failing fitter.
     */
    private fun loadSweep(): List<CalibrationSample>? {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        var csv: File? = null
        while (dir != null && csv == null) {
            val candidate = File(dir, "calibration/2026-07-31-sweep-PLTN-RB1VQ.csv")
            if (candidate.isFile) csv = candidate
            dir = dir.parentFile
        }
        if (csv == null) return null

        return csv.readLines()
            .drop(1)
            .mapNotNull { line ->
                val parts = line.split(',')
                if (parts.size < 4) return@mapNotNull null
                val cadence = parts[1].toDoubleOrNull() ?: return@mapNotNull null
                val resistance = parts[2].toDoubleOrNull() ?: return@mapNotNull null
                val watts = parts[3].toDoubleOrNull() ?: return@mapNotNull null
                CalibrationSample(cadence, resistance, watts)
            }
    }
}
