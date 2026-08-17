package com.pelonot.domain.progress

import com.pelonot.domain.chart.TimeInHeartRateZone
import com.pelonot.domain.model.HeartRateZone
import com.pelonot.domain.model.PerceivedEffort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 21.6.1 — which of the three answers the heart points at, and when it declines.
 *
 * Every case is built as a map of seconds by zone rather than through
 * `RideChartBuilder`, because what this reads is exactly that map and a builder
 * in the way would make a failure ambiguous between the two.
 *
 * The gates are the interesting half. There are four ways to say nothing and
 * only three things to say, which is the shape 21.6.4 asks for: heart rate is a
 * fair signal about a ride and a poor one about a minute of it.
 */
class SuggestedEffortTest {

    private fun heart(
        unrecorded: Int = 0,
        vararg seconds: Pair<HeartRateZone, Int>
    ) = TimeInHeartRateZone(
        secondsByZone = seconds.toMap(),
        secondsUnrecorded = unrecorded
    )

    // ---- the three answers ----

    /**
     * The owner's first example, in the heart's own terms: *"an endurance class
     * ridden mostly in HR zones 4–5 was hard."*
     */
    @Test
    fun `a ride spent mostly at threshold and above suggests everything I had`() {
        val suggestion = SuggestedEffort.of(
            heart(
                seconds = arrayOf(
                    HeartRateZone.H2 to 300,
                    HeartRateZone.H3 to 300,
                    HeartRateZone.H4 to 700,
                    HeartRateZone.H5 to 500
                )
            )
        )

        // 1200 of 1800 at H4 and above — two thirds.
        assertEquals(PerceivedEffort.Maximal, suggestion)
    }

    /** And the second: *"a threshold class ridden in zone 2 was easy."* */
    @Test
    fun `a ride spent low with nothing above tempo suggests comfortable`() {
        val suggestion = SuggestedEffort.of(
            heart(
                seconds = arrayOf(
                    HeartRateZone.H1 to 900,
                    HeartRateZone.H2 to 600,
                    HeartRateZone.H3 to 300
                )
            )
        )

        assertEquals(PerceivedEffort.Easy, suggestion)
    }

    /**
     * The middle answer is what is left, and it is deliberately the wide one:
     * a ride that is neither unambiguously flat out nor unambiguously gentle
     * costs least when the guess is wrong.
     */
    @Test
    fun `a ride spent around tempo suggests a good workout`() {
        val suggestion = SuggestedEffort.of(
            heart(
                seconds = arrayOf(
                    HeartRateZone.H2 to 500,
                    HeartRateZone.H3 to 900,
                    HeartRateZone.H4 to 400
                )
            )
        )

        assertEquals(PerceivedEffort.Solid, suggestion)
    }

    /**
     * Cardiac drift is the reason [PerceivedEffort.Maximal] needs half the
     * ride: a long steady effort finishing in H4 is not *nothing left at the
     * end*, and this is the ride that would be told it was.
     */
    @Test
    fun `a long steady ride drifting into threshold at the end is not maximal`() {
        val suggestion = SuggestedEffort.of(
            heart(
                seconds = arrayOf(
                    HeartRateZone.H2 to 900,
                    HeartRateZone.H3 to 1200,
                    HeartRateZone.H4 to 1500
                )
            )
        )

        // 1500 of 3600 is 42% — a real effort, and not the top answer.
        assertEquals(PerceivedEffort.Solid, suggestion)
    }

    /**
     * *Comfortable* wants the ride to be genuinely low, not merely short of
     * threshold. An hour at tempo is a workout.
     */
    @Test
    fun `a ride spent entirely at tempo is not comfortable`() {
        val suggestion = SuggestedEffort.of(
            heart(seconds = arrayOf(HeartRateZone.H3 to 2400))
        )

        assertEquals(PerceivedEffort.Solid, suggestion)
    }

    // ---- the four ways to say nothing ----

    /** No strap, or a rider the app has no maximum for (21.2.4). */
    @Test
    fun `a ride with no heart rate at all suggests nothing`() {
        assertNull(SuggestedEffort.of(TimeInHeartRateZone()))
    }

    /** Ten minutes is the floor, and it is shared with `EffortAgainstPlan`. */
    @Test
    fun `a ride shorter than ten minutes of heart rate suggests nothing`() {
        assertNull(
            SuggestedEffort.of(heart(seconds = arrayOf(HeartRateZone.H5 to 599)))
        )
    }

    /**
     * The one this shares with 21.4.1's coverage caption: a strap that heard
     * eleven minutes of forty describes eleven minutes, and *everything I had*
     * off a quarter of a ride is the failure that costs something.
     */
    @Test
    fun `a strap that heard less than half the ride suggests nothing`() {
        assertNull(
            SuggestedEffort.of(
                heart(
                    unrecorded = 1800,
                    seconds = arrayOf(HeartRateZone.H5 to 900)
                )
            )
        )
    }

    /** And it is the coverage that decides it, not the length. */
    @Test
    fun `the same reading covering most of the ride does suggest`() {
        assertEquals(
            PerceivedEffort.Maximal,
            SuggestedEffort.of(
                heart(
                    unrecorded = 100,
                    seconds = arrayOf(HeartRateZone.H5 to 900)
                )
            )
        )
    }

    /**
     * The rule that keeps this a suggestion: nothing here writes, and nothing
     * here reads what the rider said. The screen drops the mark once there is
     * an answer (`EffortQuestion`), which is a different rule in a different
     * file — this one only has to be incapable of knowing.
     */
    @Test
    fun `the suggestion is a function of the heart alone`() {
        val reading = heart(seconds = arrayOf(HeartRateZone.H3 to 1800))

        assertEquals(SuggestedEffort.of(reading), SuggestedEffort.of(reading))
    }
}
