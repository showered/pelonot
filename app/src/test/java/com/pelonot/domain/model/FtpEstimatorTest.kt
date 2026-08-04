package com.pelonot.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PLAN 20.3.2. The estimate replaces a text box asking a rider for a number
 * they cannot know, so what these tests hold in place is mostly *shape* rather
 * than exact watts: monotonic in the things it should be monotonic in, bounded
 * where a bound was argued for, and absent rather than invented when an input
 * is missing.
 *
 * The exception is [estimateIsBelowPublishedMidRange], which pins the one
 * coefficient decision the class is designed around.
 */
class FtpEstimatorTest {

    private val thirtyYearOld = 30

    @Test
    fun `no weight means no estimate`() {
        assertNull(FtpEstimator.estimate(null, FitnessLevel.Regular, thirtyYearOld))
        assertNull(FtpEstimator.estimate(0.0, FitnessLevel.Regular, thirtyYearOld))
        assertNull(FtpEstimator.estimate(-70.0, FitnessLevel.Regular, thirtyYearOld))
    }

    @Test
    fun `no self-assessment means no estimate`() {
        assertNull(FtpEstimator.estimate(70.0, null, thirtyYearOld))
    }

    @Test
    fun `no age still estimates`() {
        // 20.3.9 makes date of birth the shared input, but it is nullable for
        // the rider who would rather not say and the estimate must survive that.
        assertNotNull(FtpEstimator.estimate(70.0, FitnessLevel.Occasional, null))
    }

    @Test
    fun `heavier rider gets a higher estimate at the same description`() {
        val light = FtpEstimator.estimate(55.0, FitnessLevel.Occasional, thirtyYearOld)!!
        val heavy = FtpEstimator.estimate(90.0, FitnessLevel.Occasional, thirtyYearOld)!!
        assertTrue("$heavy should exceed $light", heavy > light)
    }

    @Test
    fun `a fitter description gets a higher estimate at the same weight`() {
        val values = FitnessLevel.entries.map {
            FtpEstimator.estimate(75.0, it, thirtyYearOld)!!
        }
        assertEquals(values.sortedBy { it }, values)
        assertTrue("the three answers must be distinguishable", values.toSet().size == 3)
    }

    @Test
    fun `age does not raise the estimate below the peak window`() {
        val teenager = FtpEstimator.estimate(70.0, FitnessLevel.Regular, 16)!!
        val thirtyFive = FtpEstimator.estimate(70.0, FitnessLevel.Regular, 35)!!
        assertEquals(thirtyFive, teenager)
    }

    @Test
    fun `age lowers the estimate beyond the peak window`() {
        val young = FtpEstimator.estimate(70.0, FitnessLevel.Regular, 30)!!
        val older = FtpEstimator.estimate(70.0, FitnessLevel.Regular, 70)!!
        assertTrue("$older should be below $young", older < young)
    }

    @Test
    fun `the age decline is bounded`() {
        // Otherwise the arithmetic runs to zero, and a 100-year-old is a rider
        // rather than a division by nothing.
        assertEquals(
            FtpEstimator.MINIMUM_AGE_FACTOR,
            FtpEstimator.ageFactor(500),
            0.0001
        )
    }

    @Test
    fun `a very light rider is floored`() {
        val tiny = FtpEstimator.estimate(30.0, FitnessLevel.NewToThis, 80)!!
        assertEquals(FtpEstimator.MINIMUM_W, tiny)
    }

    @Test
    fun `estimates are rounded to five watts`() {
        // False precision is the failure this guards: three questions do not
        // produce a number worth stating to the watt.
        val weights = (45..120 step 1).map { it.toDouble() }
        for (weight in weights) {
            for (level in FitnessLevel.entries) {
                val watts = FtpEstimator.estimate(weight, level, 42)!!
                assertEquals(
                    "$weight kg / $level gave $watts, not a multiple of 5",
                    0,
                    watts % FtpEstimator.ROUNDING_W
                )
            }
        }
    }

    /**
     * The one coefficient assertion, and the reason is the class's own:
     * `PostWorkoutAnalyzer` can only ever propose an FTP *upward*, so an
     * estimate that starts high is never corrected. Published FTP-per-kilogram
     * tables put a regularly-riding adult around 3.0–3.5 W/kg; every value here
     * must sit below that band, not merely inside it.
     *
     * If a future change wants richer coefficients it has to argue against the
     * asymmetry rather than around it, and this test is where that argument
     * gets made.
     */
    @Test
    fun estimateIsBelowPublishedMidRange() {
        val publishedMidRange = mapOf(
            FitnessLevel.NewToThis to 2.0,
            FitnessLevel.Occasional to 2.5,
            FitnessLevel.Regular to 3.0
        )
        for ((level, published) in publishedMidRange) {
            assertTrue(
                "${level.id} is ${level.wattsPerKg} W/kg, which is not below $published",
                level.wattsPerKg < published
            )
        }
    }

    @Test
    fun `a seventy kilogram occasional rider lands near the old single default`() {
        // Not a requirement, but a sanity check with real meaning: 150 W was
        // what every rider got before this existed, and the middle answer at an
        // average weight should not be a wild departure from it — otherwise the
        // coefficients are wrong rather than the old default.
        val watts = FtpEstimator.estimate(70.0, FitnessLevel.Occasional, 35)!!
        assertTrue("$watts is not near 150", watts in 130..165)
    }

    @Test
    fun `age is whole years and a future date is not an age`() {
        val now = 1_754_000_000_000L
        val yearMs = 31_556_952_000L
        assertEquals(40, FtpEstimator.ageYearsAt(now - 40 * yearMs, now))
        assertNull(FtpEstimator.ageYearsAt(now + yearMs, now))
        assertNull(FtpEstimator.ageYearsAt(null, now))
    }

    @Test
    fun `an implausible date of birth is not an age`() {
        val now = 1_754_000_000_000L
        // Epoch zero is 1970 here, but a date picker can reach 1800 and a
        // 200-year-old is a mistyped year rather than a rider.
        assertNull(FtpEstimator.ageYearsAt(now - 200L * 31_556_952_000L, now))
    }
}
