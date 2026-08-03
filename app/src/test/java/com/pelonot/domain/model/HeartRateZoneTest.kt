package com.pelonot.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartRateZoneTest {

    private val maxHr = 190

    @Test
    fun `every boundary is checked from both sides`() {
        // 60/70/80/90 percent of 190 = 114 / 133 / 152 / 171.
        assertEquals(HeartRateZone.H1, HeartRateZone.forHeartRate(113, maxHr))
        assertEquals(HeartRateZone.H2, HeartRateZone.forHeartRate(114, maxHr))
        assertEquals(HeartRateZone.H2, HeartRateZone.forHeartRate(132, maxHr))
        assertEquals(HeartRateZone.H3, HeartRateZone.forHeartRate(133, maxHr))
        assertEquals(HeartRateZone.H3, HeartRateZone.forHeartRate(151, maxHr))
        assertEquals(HeartRateZone.H4, HeartRateZone.forHeartRate(152, maxHr))
        assertEquals(HeartRateZone.H4, HeartRateZone.forHeartRate(170, maxHr))
        assertEquals(HeartRateZone.H5, HeartRateZone.forHeartRate(171, maxHr))
    }

    @Test
    fun `no gap between the zones anywhere on the scale`() {
        // The published tables quote "60-70, 70-80" and leave a rider at 70.4%
        // matching nothing. Same fix PowerZone carries.
        for (bpm in 1..260) {
            assertNotNull("$bpm bpm matched no zone", HeartRateZone.forHeartRate(bpm, maxHr))
        }
    }

    @Test
    fun `a rider above their own maximum is still in the top zone`() {
        // The number a rider gives is their best knowledge, not a ceiling the
        // body agrees to. 200 on a 190 max is a hard effort, not an error.
        assertEquals(HeartRateZone.H5, HeartRateZone.forHeartRate(200, maxHr))
    }

    @Test
    fun `an unknown heart rate has no zone, and neither does an unknown maximum`() {
        // 21.2.4. Both halves: this project has twice corrupted a rider's
        // record by treating a missing heart rate as a number, and a zone
        // computed from a maximum nobody supplied is the same mistake with a
        // percentage sign on it.
        assertNull(HeartRateZone.forHeartRate(null, maxHr))
        assertNull(HeartRateZone.forHeartRate(140, null))
        assertNull(HeartRateZone.forHeartRate(null, null))
        assertNull(HeartRateZone.forHeartRate(0, maxHr))
        assertNull(HeartRateZone.forHeartRate(140, 0))
    }

    @Test
    fun `the bpm ranges are contiguous and end at the rider's own maximum`() {
        val ranges = HeartRateZone.entries.map { it.bpmRange(maxHr) }

        assertEquals(0, ranges.first().first)
        ranges.zipWithNext { lower, upper ->
            assertEquals("${lower.last} then ${upper.first}", lower.last + 1, upper.first)
        }
        // H5 is unbounded in the model and printed up to the maximum: "171-∞"
        // helps nobody.
        assertEquals(maxHr, ranges.last().last)
    }

    @Test
    fun `heart rate zones are not power zones wearing another label`() {
        // 21.2.1. Five against seven, and different boundaries — the two must
        // never be presentable as the same statement about a rider.
        assertEquals(5, HeartRateZone.entries.size)
        assertTrue(PowerZone.entries.size > HeartRateZone.entries.size)
    }
}
