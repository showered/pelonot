package com.pelonot.data.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.7.3 — the fence between the sensor board and the rider's permanent record.
 *
 * The numbers in these tests are the ones the bike actually produced on 1
 * August 2026 while the rider was turning the cranks at 61 rpm and 47 W.
 */
class TelemetryPlausibilityTest {

    private fun reading(
        cadence: Double = 88.0,
        resistance: Double = 45.0,
        power: Double = 180.0
    ) = SensorReading(
        powerWatts = power,
        cadenceRpm = cadence,
        resistancePercent = resistance,
        timestampMs = 1_000_000L
    )

    @Test
    fun `an ordinary reading passes`() {
        assertTrue(reading().isPlausible)
    }

    @Test
    fun `the cadence the bike reported is not a cadence`() {
        val impossible = reading(cadence = 603.0).implausibleValues()

        assertEquals(listOf(ImplausibleValue(TelemetryField.Cadence, 603.0)), impossible)
    }

    @Test
    fun `the resistance the bike reported is not a resistance`() {
        val impossible = reading(resistance = 602.0).implausibleValues()

        assertEquals(listOf(ImplausibleValue(TelemetryField.Resistance, 602.0)), impossible)
    }

    @Test
    fun `the 636 watt spike on the first ride was plausible and is not caught`() {
        // Worth stating as a test rather than a comment: the fence is about
        // impossibility, not suspicion. 636 W off a rider averaging 47 W is
        // almost certainly the same corruption, and no bound can say so —
        // which is exactly why 2.7.3 says a fence is not the fix.
        assertTrue(reading(power = 636.0).isPlausible)
    }

    @Test
    fun `every field can be wrong at once`() {
        val impossible = reading(cadence = 603.0, resistance = 602.0, power = 9_000.0)
            .implausibleValues()

        assertEquals(3, impossible.size)
    }

    @Test
    fun `negative values are not measurements`() {
        assertFalse(reading(cadence = -1.0).isPlausible)
        assertFalse(reading(resistance = -0.5).isPlausible)
        assertFalse(reading(power = -20.0).isPlausible)
    }

    @Test
    fun `NaN and infinity are not measurements`() {
        assertFalse(reading(power = Double.NaN).isPlausible)
        assertFalse(reading(cadence = Double.POSITIVE_INFINITY).isPlausible)
    }

    /**
     * The bounds have to be generous enough that a real effort is never
     * dropped. A dropped sample is a hole in someone's ride.
     */
    @Test
    fun `a hard sprint is never rejected`() {
        assertTrue(reading(cadence = 140.0, resistance = 100.0, power = 900.0).isPlausible)
    }

    @Test
    fun `a stationary bike is never rejected`() {
        assertTrue(reading(cadence = 0.0, resistance = 0.0, power = 0.0).isPlausible)
    }

    /**
     * The whole argument of 2.7.3 in one assertion: there is no API here that
     * turns 603 into 200. A rejected value leaves nothing behind, and the
     * caller's only option is to record nothing.
     */
    @Test
    fun `the fence rejects and never clamps`() {
        val corrupted = reading(cadence = 603.0)

        assertEquals(603.0, corrupted.cadenceRpm, 0.0)
        assertFalse(corrupted.isPlausible)
    }
}
