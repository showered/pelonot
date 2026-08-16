package com.pelonot.core

import com.pelonot.domain.model.UnitSystem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * Formatting, including under a locale that would otherwise corrupt it.
 *
 * Every `String.format` in `Formatters` passes `Locale.US` explicitly. Without
 * it, a rider in France sees `12,34 km` — which is correct prose and wrong
 * everywhere the string is parsed back, exported to CSV, or compared. The same
 * class of defect already cost this project once, in `WorkoutDto`'s timestamp.
 */
class FormattersTest {

    private val defaultLocale = Locale.getDefault()

    @After
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    @Test
    fun durationWrapsPastAnHour() {
        assertEquals("00:00", Formatters.duration(0))
        assertEquals("00:59", Formatters.duration(59))
        assertEquals("45:00", Formatters.duration(2700))
        assertEquals("1:00:00", Formatters.duration(3600))
        assertEquals("2:05:07", Formatters.duration(7507))
    }

    @Test
    fun aNegativeDurationClampsRatherThanRenderingBackwards() {
        assertEquals("00:00", Formatters.duration(-5))
    }

    @Test
    fun distanceCarriesTheRidersOwnUnit() {
        assertEquals("40.00 km", Formatters.distance(40.0, UnitSystem.METRIC))
        assertEquals("24.85 mi", Formatters.distance(40.0, UnitSystem.IMPERIAL))
    }

    @Test
    fun speedAndBodyWeightConvertTheSameWay() {
        assertEquals("30.0 km/h", Formatters.speed(30.0, UnitSystem.METRIC))
        assertEquals("18.6 mph", Formatters.speed(30.0, UnitSystem.IMPERIAL))
        assertEquals("72.0 kg", Formatters.bodyWeight(72.0, UnitSystem.METRIC))
        assertEquals("158.7 lb", Formatters.bodyWeight(72.0, UnitSystem.IMPERIAL))
    }

    @Test
    fun unitAgnosticMetricsAreUnaffectedByTheSetting() {
        // 13.6: watts, RPM, BPM and kJ have no imperial form. There is
        // deliberately no calories option either — the power model cannot
        // support a nutrition claim.
        assertEquals("214 W", Formatters.watts(213.6))
        assertEquals("92 RPM", Formatters.rpm(91.8))
        assertEquals("135 BPM", Formatters.bpm(135))
        assertEquals("12 kJ", Formatters.kilojoules(12.45))
    }

    @Test
    fun nothingInTheWattFamilyEverShowsADecimalPlace() {
        // 11.6.12, the owner's call. The stored value keeps its fraction —
        // the board's power really is measured in tenths (14.4.6) — but a
        // rider is never shown one.
        assertEquals("214 W", Formatters.watts(213.6))
        assertEquals("13 kJ", Formatters.kilojoules(12.5))
        assertEquals("255", Formatters.kilojoulesValue(254.9))
        assertEquals("0", Formatters.kilojoulesValue(0.4))
    }

    @Test
    fun anUnknownHeartRateIsADashAndNeverAZero() {
        // Null means "no strap", not "no pulse".
        assertEquals("--", Formatters.bpm(null))
    }

    @Test
    fun aCommaDecimalLocaleDoesNotChangeTheOutput() {
        Locale.setDefault(Locale.FRANCE)

        assertEquals("40.00 km", Formatters.distance(40.0, UnitSystem.METRIC))
        assertEquals("24.85 mi", Formatters.distance(40.0, UnitSystem.IMPERIAL))
        assertEquals("12 kJ", Formatters.kilojoules(12.45))
        assertEquals("1:00:00", Formatters.duration(3600))
    }

    @Test
    fun aNonArabicNumeralLocaleStillProducesDigitsEveryoneCanRead() {
        // hi-IN-u-nu-deva renders 1234 as १२३४ when the locale is implicit.
        Locale.setDefault(Locale.forLanguageTag("hi-IN-u-nu-deva"))

        assertEquals("40.00 km", Formatters.distance(40.0, UnitSystem.METRIC))
        assertEquals("45:00", Formatters.duration(2700))
    }

    /**
     * The defect this exists to stop, twice now: *"1 minutes 42 seconds"* on
     * the ride detail card (21.6.3) and *"1 days ridden in the last 17 weeks"*
     * on the dashboard's ride-days grid the first time a rider with one ride
     * saw it (22.9.4). Both are read aloud by a screen reader as well as
     * printed.
     */
    @Test
    fun aCountAndItsUnitAgree() {
        assertEquals("0 days", Formatters.plural(0, "day"))
        assertEquals("1 day", Formatters.plural(1, "day"))
        assertEquals("2 days", Formatters.plural(2, "day"))
        assertEquals("17 weeks", Formatters.plural(17, "week"))
    }
}
