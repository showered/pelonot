package com.pelonot.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PLAN 20.5.1. The fault this exists for is not arithmetic — it is that
 * `FtpEstimator` is linear in weight and has nothing to disbelieve, so `68`
 * typed into a field labelled `lb` produced a 65 W FTP and the app said it with
 * a straight face.
 *
 * The two cases worth pinning are the two real failures: a missing digit, and
 * pounds typed into a kilogram field.
 */
class RiderBoundsTest {

    @Test
    fun `an ordinary rider passes in either unit`() {
        assertTrue(RiderBounds.weightIsPlausible(72.0))
        assertTrue(RiderBounds.weightIsPlausible(UnitSystem.IMPERIAL.weightToKg(159.0)))
        assertTrue(RiderBounds.weightIsPlausible(UnitSystem.METRIC.weightToKg(72.0)))
    }

    /**
     * The case that opened the item: 68 in a pounds field is 31 kg, which is
     * *inside* the fence and always was. What the fence catches is the same
     * mistake one digit worse — and the rest of the defence is the sentence
     * under the field naming the unit, which is what a rider actually reads.
     */
    @Test
    fun `an impossible weight is refused rather than corrected`() {
        assertFalse(RiderBounds.weightIsPlausible(6.0))
        assertFalse(RiderBounds.weightIsPlausible(0.5))
        assertFalse(RiderBounds.weightIsPlausible(400.0))

        // Reject, never clamp: nothing here hands back a substitute.
        assertNotNull(RiderBounds.weightProblem(6.0, UnitSystem.METRIC))
    }

    /** Pounds typed into a kilogram field — 250 lb of rider read as 250 kg. */
    @Test
    fun `a unit mix-up at the top is caught`() {
        assertTrue(RiderBounds.weightIsPlausible(113.0))
        assertFalse(RiderBounds.weightIsPlausible(551.0))
    }

    /**
     * Absent is not out of range. The weight is optional at profile creation
     * and an unanswered question is a different claim from a wrong answer —
     * the same distinction `heartRateBpm` and `target_position` are built on.
     */
    @Test
    fun `no answer is not a wrong answer`() {
        assertTrue(RiderBounds.weightIsPlausible(null))
        assertNull(RiderBounds.weightProblem(null, UnitSystem.METRIC))
    }

    /**
     * The message quotes the rider's own unit, because the failure it catches
     * is a rider who does not realise which unit the field is in. A range in
     * kilograms shown to somebody typing pounds explains nothing.
     */
    @Test
    fun `the range is quoted in the unit the rider is typing`() {
        assertEquals(
            "That should be between 25 and 250 kg.",
            RiderBounds.weightProblem(5.0, UnitSystem.METRIC)
        )
        assertEquals(
            "That should be between 55 and 551 lb.",
            RiderBounds.weightProblem(5.0, UnitSystem.IMPERIAL)
        )
    }

    @Test
    fun `the bounds are the ones the whole app uses`() {
        assertTrue(RiderBounds.weightIsPlausible(RiderBounds.MIN_WEIGHT_KG))
        assertTrue(RiderBounds.weightIsPlausible(RiderBounds.MAX_WEIGHT_KG))
        assertFalse(RiderBounds.weightIsPlausible(RiderBounds.MIN_WEIGHT_KG - 0.1))
        assertFalse(RiderBounds.weightIsPlausible(RiderBounds.MAX_WEIGHT_KG + 0.1))
    }
}
