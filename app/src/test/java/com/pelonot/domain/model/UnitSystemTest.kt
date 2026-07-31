package com.pelonot.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.Locale

/**
 * Conversions and locale defaults.
 *
 * The property that matters most here is the round trip: the settings screen
 * shows a stored kilogram value in pounds, the rider edits it, and the result
 * is converted back. If those two constants disagree, a rider on imperial
 * units loses weight every time they open Settings and press Save.
 */
class UnitSystemTest {

    @Test
    fun metricLeavesEveryValueAlone() {
        assertEquals(42.0, UnitSystem.METRIC.distanceFromKm(42.0), 0.0)
        assertEquals(30.0, UnitSystem.METRIC.speedFromKmh(30.0), 0.0)
        assertEquals(72.5, UnitSystem.METRIC.weightFromKg(72.5), 0.0)
        assertEquals(72.5, UnitSystem.METRIC.weightToKg(72.5), 0.0)
    }

    @Test
    fun imperialConvertsDistanceAndSpeed() {
        // A 40 km ride is a shade under 25 miles.
        assertEquals(24.855, UnitSystem.IMPERIAL.distanceFromKm(40.0), 0.001)
        assertEquals(18.641, UnitSystem.IMPERIAL.speedFromKmh(30.0), 0.001)
    }

    @Test
    fun imperialConvertsBodyWeight() {
        assertEquals(158.7, UnitSystem.IMPERIAL.weightFromKg(72.0), 0.1)
    }

    @Test
    fun weightSurvivesARoundTripThroughTheSettingsField() {
        val stored = 72.4
        val shown = UnitSystem.IMPERIAL.weightFromKg(stored)
        assertEquals(stored, UnitSystem.IMPERIAL.weightToKg(shown), 1e-9)
    }

    @Test
    fun zeroAndNegativeValuesAreNotSpecialCased() {
        // Distance is never negative, but a formatter that quietly clamped
        // would hide a real bug upstream rather than showing it.
        assertEquals(0.0, UnitSystem.IMPERIAL.distanceFromKm(0.0), 0.0)
        assertEquals(-0.621371, UnitSystem.IMPERIAL.distanceFromKm(-1.0), 1e-6)
    }

    @Test
    fun localesThatExpectMilesGetImperial() {
        assertSame(UnitSystem.IMPERIAL, UnitSystem.fromLocale(Locale.US))
        assertSame(UnitSystem.IMPERIAL, UnitSystem.fromLocale(Locale.UK))
        assertSame(UnitSystem.IMPERIAL, UnitSystem.fromLocale(Locale("en", "LR")))
    }

    @Test
    fun everywhereElseGetsMetric() {
        assertSame(UnitSystem.METRIC, UnitSystem.fromLocale(Locale.FRANCE))
        assertSame(UnitSystem.METRIC, UnitSystem.fromLocale(Locale.JAPAN))
        assertSame(UnitSystem.METRIC, UnitSystem.fromLocale(Locale("en", "AU")))
    }

    @Test
    fun aLocaleWithNoCountryFallsBackToMetricRatherThanThrowing() {
        // `Locale("en")` has an empty country, and so does a device that has
        // never been through setup.
        assertSame(UnitSystem.METRIC, UnitSystem.fromLocale(Locale("en")))
        assertSame(UnitSystem.METRIC, UnitSystem.fromLocale(Locale.ROOT))
    }

    @Test
    fun countryMatchingIsCaseInsensitive() {
        assertSame(UnitSystem.IMPERIAL, UnitSystem.fromLocale(Locale("en", "us")))
    }

    @Test
    fun anUnrecognisedStoredNameIsNullRatherThanADefault() {
        // The caller falls back to the locale, which is a better answer than
        // pinning metric on a preference file written by an older build.
        assertSame(UnitSystem.METRIC, UnitSystem.fromName("METRIC"))
        assertSame(UnitSystem.IMPERIAL, UnitSystem.fromName("IMPERIAL"))
        assertEquals(null, UnitSystem.fromName("MILES"))
        assertEquals(null, UnitSystem.fromName(null))
    }
}
