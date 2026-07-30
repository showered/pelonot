package com.pelonot.data.sensor

import com.pelonot.domain.model.PowerZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutMetricsCalculatorTest {

    private val calculator = WorkoutMetricsCalculator()

    private fun reading(
        seconds: Int,
        power: Double,
        cadence: Double = 90.0
    ) = SensorReading(
        powerWatts = power,
        cadenceRpm = cadence,
        resistancePercent = 40.0,
        heartRateBpm = null,
        timestampMs = START_MS + seconds * 1000L
    )

    /** Feeds [durationSec] seconds of 1 Hz samples at a constant power. */
    private fun rideAt(power: Double, durationSec: Int, cadence: Double = 90.0): CalculatedMetrics {
        var last: CalculatedMetrics? = null
        for (second in 0..durationSec) {
            last = calculator.processReading(reading(second, power, cadence), FTP)
        }
        return last!!
    }

    @Test
    fun `holding 200W for 60 seconds yields 12 kilojoules`() {
        // 200 J/s x 60 s = 12,000 J = 12 kJ.
        val metrics = rideAt(power = 200.0, durationSec = 60)
        assertEquals(12.0, metrics.totalOutputKj, 0.001)
    }

    @Test
    fun `total output grows linearly with duration, not quadratically`() {
        // Regression: the old integration multiplied each trapezoid by the
        // sample count, so doubling the ride length quadrupled the energy.
        val oneMinute = WorkoutMetricsCalculator().let { calc ->
            for (s in 0..60) calc.processReading(reading(s, 200.0), FTP)
            calc.processReading(reading(60, 200.0), FTP).totalOutputKj
        }
        val twoMinutes = WorkoutMetricsCalculator().let { calc ->
            var last = 0.0
            for (s in 0..120) last = calc.processReading(reading(s, 200.0), FTP).totalOutputKj
            last
        }
        assertEquals(2.0, twoMinutes / oneMinute, 0.02)
    }

    @Test
    fun `a realistic 45 minute ride reports a plausible energy total`() {
        // ~180W for 45 min is about 486 kJ. The old maths returned millions.
        val metrics = rideAt(power = 180.0, durationSec = 45 * 60)
        assertEquals(486.0, metrics.totalOutputKj, 1.0)
        assertTrue("implausible output ${metrics.totalOutputKj}", metrics.totalOutputKj < 1_000)
    }

    @Test
    fun `trapezoidal integration handles a ramp correctly`() {
        // Linear ramp 0W -> 100W over 10s integrates to the triangle area,
        // 0.5 x 100 x 10 = 500 J.
        var last: CalculatedMetrics? = null
        for (second in 0..10) {
            last = calculator.processReading(reading(second, second * 10.0), FTP)
        }
        assertEquals(0.5, last!!.totalOutputKj, 0.001)
    }

    @Test
    fun `distance accumulates across the ride`() {
        // Regression: distance used to be the latest sample's contribution
        // only, so it never exceeded a few metres however long the ride was.
        val oneMinute = rideAt(power = 200.0, durationSec = 60, cadence = 90.0)
        assertTrue("distance did not accumulate", oneMinute.distanceKm > 0.1)

        val expectedKm = (90.0 / 60.0) * 0.0021 * 60.0
        assertEquals(expectedKm, oneMinute.distanceKm, 0.0001)
    }

    @Test
    fun `distance is monotonically non-decreasing`() {
        var previous = 0.0
        for (second in 0..300) {
            val metrics = calculator.processReading(reading(second, 150.0), FTP)
            assertTrue("distance decreased", metrics.distanceKm >= previous)
            previous = metrics.distanceKm
        }
    }

    @Test
    fun `a long gap between samples is not integrated as continuous riding`() {
        calculator.processReading(reading(0, 200.0), FTP)
        // Simulate the app being backgrounded for an hour.
        val afterGap = calculator.processReading(reading(3600, 200.0), FTP)
        // Only the 5s clamp is credited: 200W x 5s = 1 kJ.
        assertEquals(1.0, afterGap.totalOutputKj, 0.001)
    }

    @Test
    fun `rolling averages only consider their window`() {
        for (second in 0..29) {
            calculator.processReading(reading(second, 100.0), FTP)
        }
        val metrics = calculator.processReading(reading(30, 300.0), FTP)

        // The newest sample dominates a 1s window but barely moves a 30s one.
        assertTrue(metrics.avgPower1s > metrics.avgPower30s)
        assertEquals(300.0, metrics.avgPower1s, 100.0)
        assertEquals(106.0, metrics.avgPower30s, 5.0)
    }

    @Test
    fun `power zone is reported from the current reading and FTP`() {
        val metrics = calculator.processReading(reading(0, 250.0), FTP)
        // 250W on a 200W FTP is 125%, which is Z6 (121–150%).
        assertEquals(PowerZone.Z6, metrics.currentPowerZone)
        assertEquals(125.0, metrics.currentFtpPercentage, 0.01)
    }

    @Test
    fun `an unknown FTP does not produce infinite or NaN percentages`() {
        val metrics = calculator.processReading(reading(0, 250.0), ftpWatts = 0)
        assertEquals(0.0, metrics.currentFtpPercentage, 0.0)
        assertEquals(PowerZone.Z1, metrics.currentPowerZone)
    }

    @Test
    fun `reset clears cumulative totals`() {
        rideAt(power = 200.0, durationSec = 60)
        calculator.reset()
        val metrics = calculator.processReading(reading(0, 200.0), FTP)
        assertEquals(0.0, metrics.totalOutputKj, 0.001)
        assertEquals(0.0, metrics.distanceKm, 0.001)
    }

    private companion object {
        const val FTP = 200
        const val START_MS = 1_700_000_000_000L
    }
}
