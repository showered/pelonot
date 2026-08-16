package com.pelonot.domain.progress

import com.pelonot.data.service.PostWorkoutAnalyzer
import com.pelonot.domain.model.PerceivedEffort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that let the app say a rider's FTP has gone **down** (PLAN 7.11).
 *
 * Almost every test here is a *refusal* — the interesting behaviour of this
 * object is the proposals it declines to make, because the failure mode is not
 * a missed prompt but the app telling somebody their fitness has dropped on
 * evidence that does not say so.
 */
class FtpReductionRuleTest {

    /**
     * A ride that came in short with the rider unmistakably working: the
     * default case every test below varies one thing away from.
     *
     * 180 W over twenty minutes is an implied FTP of 171, which is 14.5% below
     * a current 200 — well past the 5% bar. The heart rate is 160 of a maximum
     * of 190, or 84%.
     */
    private fun shortRide(
        id: String = "ride",
        at: Long = 1_000,
        peakWatts: Double = 180.0,
        avgHr: Double? = 160.0,
        maxHr: Int? = 190,
        rpe: Int? = null
    ) = FtpEvidenceRide(
        workoutId = id,
        recordedAt = at,
        peak20MinWatts = peakWatts,
        avgHr = avgHr,
        rideMaxHrBpm = maxHr,
        rpeRating = rpe
    )

    private fun threeShortRides() = listOf(
        shortRide(id = "c", at = 3_000),
        shortRide(id = "b", at = 2_000),
        shortRide(id = "a", at = 1_000)
    )

    // ---- the constants are shared with the upward path, not copied ----

    @Test
    fun `the twenty-minute correction is the same constant the upward path uses`() {
        // Two copies of one number that can disagree is how a rider gets raised
        // on one arithmetic and lowered on another.
        assertEquals(
            PostWorkoutAnalyzer.FTP_FROM_20_MIN,
            FtpReductionRule.FTP_FROM_20_MIN,
            0.0
        )
    }

    @Test
    fun `working hard is the same line the upward check reads from the other side`() {
        assertEquals(
            PostWorkoutAnalyzer.HR_THRESHOLD_FRACTION,
            FtpReductionRule.WORKING_HR_FRACTION,
            0.0
        )
    }

    @Test
    fun `the evidence window is the twenty minutes an FTP is estimated from`() {
        assertEquals(
            PostWorkoutAnalyzer.TWENTY_MINUTES_SEC,
            FtpReductionRule.EVIDENCE_WINDOW_SEC
        )
    }

    @Test
    fun `lowering an FTP asks for more than raising one`() {
        // 7.11's asymmetry, as an assertion rather than a comment: 5% down
        // against 2% up. A rider offered a number they did not earn declines
        // it; a rider told their fitness has dropped has been told something.
        val gainMargin = PostWorkoutAnalyzer.MIN_MEANINGFUL_GAIN - 1.0
        val lossMargin = 1.0 - FtpReductionRule.MIN_MEANINGFUL_LOSS
        assertTrue("a drop must clear a wider bar than a gain", lossMargin > gainMargin)
    }

    // ---- the proposal ----

    @Test
    fun `three working rides all short of the bar propose the best of them`() {
        val rides = listOf(
            shortRide(id = "c", at = 3_000, peakWatts = 180.0),
            shortRide(id = "b", at = 2_000, peakWatts = 184.0),
            shortRide(id = "a", at = 1_000, peakWatts = 176.0)
        )

        val reduction = FtpReductionRule.evaluate(rides, currentFtp = 200)

        assertNotNull(reduction)
        // 184 × 0.95 = 174.8 → 175. The *best* of the three, not the newest and
        // not the mean: whatever is offered has to be something the rider has
        // actually ridden.
        assertEquals(175, reduction!!.proposedFtp)
        assertEquals(200, reduction.currentFtp)
        assertEquals(25, reduction.dropWatts)
        assertEquals("b", reduction.strongestRide.workoutId)
    }

    @Test
    fun `the evidence it offers is the rides it read, and only those`() {
        val rides = threeShortRides() + shortRide(id = "older", at = 500)
        val reduction = FtpReductionRule.evaluate(rides, currentFtp = 200)

        assertNotNull(reduction)
        assertEquals(
            listOf("c", "b", "a"),
            reduction!!.evidence.map { it.workoutId }
        )
    }

    // ---- the refusals ----

    @Test
    fun `two short rides are not a trend`() {
        val rides = threeShortRides().take(2)
        assertNull(FtpReductionRule.evaluate(rides, currentFtp = 200))
    }

    @Test
    fun `one good ride among them ends it`() {
        // The newest ride met the number. Whatever the two before it said, the
        // rider has just produced the watts.
        val rides = listOf(
            shortRide(id = "c", at = 3_000, peakWatts = 215.0),
            shortRide(id = "b", at = 2_000),
            shortRide(id = "a", at = 1_000)
        )
        assertNull(FtpReductionRule.evaluate(rides, currentFtp = 200))
    }

    @Test
    fun `a good ride in the middle of the window ends it too`() {
        val rides = listOf(
            shortRide(id = "d", at = 4_000),
            shortRide(id = "c", at = 3_000, peakWatts = 215.0),
            shortRide(id = "b", at = 2_000),
            shortRide(id = "a", at = 1_000)
        )
        assertNull(FtpReductionRule.evaluate(rides, currentFtp = 200))
    }

    @Test
    fun `a shortfall inside the bar is not a shortfall`() {
        // 200 W peak → 190 implied, 5% below a current 200 exactly. The bar is
        // "at or below", so this one passes; 201 W does not.
        assertNotNull(
            FtpReductionRule.evaluate(
                List(3) { shortRide(id = "r$it", at = it.toLong(), peakWatts = 200.0) },
                currentFtp = 200
            )
        )
        assertNull(
            FtpReductionRule.evaluate(
                List(3) { shortRide(id = "r$it", at = it.toLong(), peakWatts = 201.0) },
                currentFtp = 200
            )
        )
    }

    @Test
    fun `a rider with no FTP cannot be told it has dropped`() {
        assertNull(FtpReductionRule.evaluate(threeShortRides(), currentFtp = 0))
    }

    // ---- was the rider working ----

    @Test
    fun `an easy ride is silent rather than counted`() {
        // The ordinary result of a recovery spin is a twenty-minute peak below
        // the rider's FTP, and it says nothing about their fitness. This is the
        // whole reason the upward path is safe off one ride and this is not.
        val spin = shortRide(id = "spin", at = 2_500, avgHr = 110.0)
        assertFalse(spin.riderWasWorking)

        val rides = listOf(
            shortRide(id = "c", at = 3_000),
            spin,
            shortRide(id = "b", at = 2_000),
            shortRide(id = "a", at = 1_000)
        )

        // Skipped, not counted against: the three working rides either side of
        // it still form the window.
        val reduction = FtpReductionRule.evaluate(rides, currentFtp = 200)
        assertNotNull(reduction)
        assertEquals(listOf("c", "b", "a"), reduction!!.evidence.map { it.workoutId })
    }

    @Test
    fun `three easy rides propose nothing at all`() {
        val rides = List(3) { shortRide(id = "r$it", at = it.toLong(), avgHr = 110.0) }
        assertNull(FtpReductionRule.evaluate(rides, currentFtp = 200))
    }

    @Test
    fun `the rider's own comfortable overrules a high trace`() {
        // 160 of 190 is 84% and would otherwise count. The rider says they had
        // more in them, and they are the authority on that.
        val ride = shortRide(rpe = PerceivedEffort.Easy.rating)
        assertFalse(ride.riderWasWorking)
    }

    @Test
    fun `a good workout at a high heart rate still counts`() {
        assertTrue(shortRide(rpe = PerceivedEffort.Solid.rating).riderWasWorking)
        assertTrue(shortRide(rpe = PerceivedEffort.Maximal.rating).riderWasWorking)
    }

    // ---- the rider with no strap (7.11.3) ----

    @Test
    fun `with no heart rate only everything I had counts`() {
        val noHr = { rpe: Int? -> shortRide(avgHr = null, maxHr = null, rpe = rpe) }
        assertTrue(noHr(PerceivedEffort.Maximal.rating).riderWasWorking)
        assertFalse(noHr(PerceivedEffort.Solid.rating).riderWasWorking)
        assertFalse(noHr(PerceivedEffort.Easy.rating).riderWasWorking)
        assertFalse(noHr(null).riderWasWorking)
    }

    @Test
    fun `a rider with no maximum heart rate falls back to their own answer`() {
        // 21.1's nullable gate: a rider who has given neither a maximum nor a
        // birth date gets no heart-rate signal anywhere in the app, and this is
        // no exception. Their rides can still be evidence, on the strongest
        // self-report only.
        val rides = List(3) {
            shortRide(
                id = "r$it",
                at = it.toLong(),
                avgHr = 168.0,
                maxHr = null,
                rpe = PerceivedEffort.Maximal.rating
            )
        }
        assertNotNull(FtpReductionRule.evaluate(rides, currentFtp = 200))
    }

    @Test
    fun `a rider who rates every ride maximal still needs the watts to be short`() {
        // 7.11.2. RPE is permission to read the shortfall, never the claim
        // itself — so the ceiling on what a serial 9-rater can do is nothing.
        val rides = List(3) {
            shortRide(
                id = "r$it",
                at = it.toLong(),
                peakWatts = 220.0,
                avgHr = null,
                maxHr = null,
                rpe = PerceivedEffort.Maximal.rating
            )
        }
        assertNull(FtpReductionRule.evaluate(rides, currentFtp = 200))
    }

    @Test
    fun `an unanswered ride with no strap says nothing`() {
        assertFalse(shortRide(avgHr = null, maxHr = null).riderWasWorking)
    }

    @Test
    fun `a maximum of zero is not a denominator`() {
        // Same family as every other absence here: a nonsense maximum must not
        // make every heart rate count as 100% of it.
        assertFalse(shortRide(maxHr = 0).riderWasWorking)
    }

    @Test
    fun `the implied FTP is the same arithmetic the breakthrough uses`() {
        assertEquals(190.0, shortRide(peakWatts = 200.0).impliedFtp, 0.0001)
    }
}
