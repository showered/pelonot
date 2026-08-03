package com.pelonot.domain.model

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MaxHeartRateTest {

    private fun utc(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month - 1, day)
        }.timeInMillis

    private val now = utc(2026, 8, 3)

    @Test
    fun `Tanaka, not the folk formula`() {
        // 220 - age has no published derivation and is around 10 bpm out at
        // 60, which is a whole zone.
        assertEquals(187, MaxHeartRate.tanaka(30))
        assertEquals(180, MaxHeartRate.tanaka(40))
        assertEquals(166, MaxHeartRate.tanaka(60))
        // The gap against the folk formula, stated so a change to either is
        // visible: 220 - 60 = 160 against 166.
        assertEquals(6, MaxHeartRate.tanaka(60) - (220 - 60))
    }

    @Test
    fun `the rider's own number beats the formula wherever both exist`() {
        val resolved = MaxHeartRate.resolve(
            measuredBpm = 194,
            birthDateMs = utc(1986, 1, 1),
            now = now
        )

        assertEquals(194, resolved?.bpm)
        assertEquals(MaxHeartRate.Source.Measured, resolved?.source)
        assertFalse(resolved!!.isEstimate)
    }

    @Test
    fun `with no number, the date of birth is the fallback and says so`() {
        val resolved = MaxHeartRate.resolve(
            measuredBpm = null,
            birthDateMs = utc(1986, 1, 1),
            now = now
        )

        // 40 years old on 3 Aug 2026.
        assertEquals(MaxHeartRate.tanaka(40), resolved?.bpm)
        assertTrue(resolved!!.isEstimate)
    }

    @Test
    fun `a rider who gave neither gets no zones rather than invented ones`() {
        // 21.3.3. The state has a screen; it is not an oversight.
        assertNull(MaxHeartRate.resolve(measuredBpm = null, birthDateMs = null, now = now))
    }

    @Test
    fun `an implausible typed number is rejected, never clamped`() {
        // Same rule as TelemetryBounds: 1900 silently becoming 220 would put a
        // rider in Recovery for a whole class with nothing looking wrong.
        assertFalse(MaxHeartRate.isPlausible(1900))
        assertFalse(MaxHeartRate.isPlausible(40))
        assertTrue(MaxHeartRate.isPlausible(190))

        // And it falls through to the estimate rather than being used.
        val resolved = MaxHeartRate.resolve(
            measuredBpm = 1900,
            birthDateMs = utc(1986, 1, 1),
            now = now
        )
        assertEquals(MaxHeartRate.tanaka(40), resolved?.bpm)
    }

    @Test
    fun `age comes off a date, so nobody's zones go stale on their birthday`() {
        val born = utc(1990, 8, 3)

        // The day before their birthday they are still 35.
        assertEquals(35, MaxHeartRate.ageInYears(born, utc(2026, 8, 2)))
        // On it, 36.
        assertEquals(36, MaxHeartRate.ageInYears(born, utc(2026, 8, 3)))
    }

    @Test
    fun `an age the formula cannot speak for is refused`() {
        // Extrapolating past its own data, and the likeliest cause of either is
        // a mis-set date picker.
        assertNull(MaxHeartRate.ageInYears(utc(2025, 1, 1), now))
        assertNull(MaxHeartRate.ageInYears(utc(1890, 1, 1), now))
        assertNull(MaxHeartRate.resolve(null, utc(2025, 1, 1), now))
    }
}
