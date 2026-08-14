package com.pelonot.domain.chart

import com.pelonot.domain.model.HeartRateZone
import com.pelonot.domain.model.Interval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 21.6.3 — the class says what it asked for, the strap says what was done.
 *
 * The cases that matter are the owner's two: an endurance class ridden with the
 * heart up high, and a threshold class ridden with it down. Everything else here
 * is a reason to say nothing at all, and there are more of those than there are
 * verdicts.
 *
 * A ride of a class `n` seconds long is built with `n + 1` samples, second 0 to
 * second `n`, for the reason `prescribedPlan` gives: a ride that stops at second
 * `n` is `n` seconds long, and a prescription totalling `n + 1` beside a
 * duration of `n` reads as an error in the same sentence.
 */
class EffortAgainstPlanTest {

    /** A ride whose heart rate is a function of the second. */
    private fun ride(seconds: Int, heartRate: (Int) -> Int?) =
        (0..seconds).map { sec -> ChartSample(sec, 150.0, 85.0, heartRate(sec)) }

    /** An easy class: [seconds] of Z2, nothing hard in it. */
    private fun enduranceClass(seconds: Int) =
        listOf(Interval(0, seconds, cadenceMin = 80, cadenceMax = 90, powerZoneNumber = 2))

    /** Half warm-up, half threshold — 50% of it prescribed hard. */
    private fun thresholdClass(seconds: Int) = listOf(
        Interval(0, seconds / 2, cadenceMin = 80, cadenceMax = 90, powerZoneNumber = 2),
        Interval(seconds / 2, seconds, cadenceMin = 80, cadenceMax = 90, powerZoneNumber = 4)
    )

    private fun charts(
        seconds: Int,
        intervals: List<Interval>,
        heartRate: (Int) -> Int?,
        maxHrBpm: Int? = 190
    ) = RideChartBuilder.build(
        samples = ride(seconds, heartRate),
        ftpWatts = 200,
        intervals = intervals,
        maxHrBpm = maxHrBpm
    )

    /**
     * The owner's own example: *"if an endurance ride the rider spent most of
     * the time in the top 1 or 2 zones, then they clearly found that ride more
     * difficult than it should have been."*
     */
    @Test
    fun `an easy class ridden with the heart up high reads as harder than asked`() {
        // 190 max, so 160 is H4 (152-170) and the whole class is prescribed Z2.
        val effort = charts(1800, enduranceClass(1800), { 160 }).effortAgainstPlan!!

        assertEquals(EffortAgainstPlan.Verdict.Harder, effort.verdict)
        assertEquals(1800, effort.prescribedSeconds)
        assertEquals(0, effort.prescribedHardSeconds)
        assertEquals(effort.heartSeconds, effort.heartHardSeconds)

        val said = RideChartSummaries.effortAgainstPlan(effort)
        assertTrue(said, said.startsWith("Harder than the class asked"))
        assertTrue(said, said.contains("in your top two heart-rate zones"))
        // Nothing was prescribed, so there is no number to be "against".
        assertTrue(said, said.contains("prescribed no hard riding at all"))
    }

    @Test
    fun `a hard class ridden with the heart down reads as easier than asked`() {
        // 120 bpm is H2 for a maximum of 190, and half the class is Z4.
        val effort = charts(1800, thresholdClass(1800), { 120 }).effortAgainstPlan!!

        assertEquals(EffortAgainstPlan.Verdict.Easier, effort.verdict)
        assertEquals(900, effort.prescribedHardSeconds)
        assertEquals(0, effort.heartHardSeconds)

        val said = RideChartSummaries.effortAgainstPlan(effort)
        assertTrue(said, said.startsWith("Easier than the class asked"))
        assertTrue(said, said.contains("no time in your top two heart-rate zones"))
        assertTrue(said, said.contains("against the 15 minutes it prescribed"))
    }

    @Test
    fun `a hard class ridden hard says so without a verdict either way`() {
        // Half the class prescribed hard, and the heart up high for half of it.
        val effort = charts(1800, thresholdClass(1800), { sec -> if (sec >= 900) 160 else 120 })
            .effortAgainstPlan!!

        assertEquals(EffortAgainstPlan.Verdict.AsAsked, effort.verdict)
        assertEquals(901, effort.heartHardSeconds)
        assertEquals(900, effort.prescribedHardSeconds)
        assertTrue(
            RideChartSummaries.effortAgainstPlan(effort)
                .startsWith("About what the class asked")
        )
    }

    /**
     * The tolerance is twenty points and is deliberately blunt (21.6.4): heart
     * rate lags effort by 30-60 seconds and drifts up across a long ride, so a
     * few minutes either way is the same ride on a warmer evening.
     */
    @Test
    fun `a small gap is not a verdict`() {
        // Prescribed 50% hard; heart up high for 60% of the ride. Ten points.
        val effort = charts(1800, thresholdClass(1800), { sec -> if (sec >= 720) 160 else 120 })
            .effortAgainstPlan!!

        assertEquals(1081, effort.heartHardSeconds)
        assertEquals(EffortAgainstPlan.Verdict.AsAsked, effort.verdict)
    }

    // Everything below is a reason to say nothing.

    @Test
    fun `a free ride was not asked for anything`() {
        assertNull(charts(1800, emptyList(), { 160 }).effortAgainstPlan)
    }

    @Test
    fun `no strap means no comparison`() {
        assertNull(charts(1800, enduranceClass(1800), { null }).effortAgainstPlan)
    }

    /**
     * 21.2.4. Without a maximum there are no heart-rate zones at all, so there
     * is nothing to hold against the class — and a default maximum would be a
     * guess about a body.
     */
    @Test
    fun `no maximum heart rate means no comparison`() {
        val charts = charts(1800, enduranceClass(1800), { 160 }, maxHrBpm = null)

        assertEquals(0, charts.timeInHeartRateZone.totalSeconds)
        assertNull(charts.effortAgainstPlan)
    }

    /**
     * A whole-ride claim needs a whole ride. Under ten minutes the 30-60 second
     * lag is a large part of the window being described.
     */
    @Test
    fun `a short ride says nothing`() {
        assertNull(charts(540, enduranceClass(540), { 160 }).effortAgainstPlan)
    }

    /**
     * The trap the coverage caption on the card exists for (21.4.1), arriving as
     * a verdict instead of a percentage: "100% of it up high" is arithmetically
     * true of eleven minutes the strap heard out of forty, and reads as the
     * shape of the whole ride.
     */
    @Test
    fun `a strap that heard a quarter of the ride describes a quarter of the ride`() {
        // 40 minutes, strap reporting for the last 11 of them, all of it H4.
        val charts = charts(2400, enduranceClass(2400), { sec -> if (sec >= 1740) 160 else null })

        assertEquals(661, charts.timeInHeartRateZone.totalSeconds)
        assertTrue(charts.timeInHeartRateZone.isPartial)
        assertNull(charts.effortAgainstPlan)
    }

    @Test
    fun `a strap that heard most of the ride still counts`() {
        // Same ride, strap on from minute 8: 32 minutes of 40, all H4.
        val charts = charts(2400, enduranceClass(2400), { sec -> if (sec >= 480) 160 else null })

        val effort = charts.effortAgainstPlan!!
        assertEquals(EffortAgainstPlan.Verdict.Harder, effort.verdict)
        // Out of the time a heart rate was reported, never out of the ride.
        assertEquals(1921, effort.heartSeconds)
        assertEquals(1921, effort.heartHardSeconds)
    }

    /**
     * 23.4.3, and it is the reason this reads the prescription rather than the
     * compliance: a condensed ride keeps the blocks it was asked to ride and
     * loses the seconds that would say whether it obeyed them, while the heart's
     * own counts were written down before the trim.
     */
    @Test
    fun `a condensed ride can still be held against its class`() {
        val stored = RideDistributions(
            secondsByHrZone = mapOf(HeartRateZone.H4.name to 1800),
            secondsWithoutHr = 0,
            maxHrBpm = 190
        )
        val charts = RideChartBuilder.build(
            samples = ride(1800) { 160 }.filter { it.timestampSec % 10 == 0 },
            ftpWatts = 200,
            intervals = enduranceClass(1800),
            maxHrBpm = 190,
            detailSec = 10,
            stored = stored
        )

        // The compliance is withdrawn...
        assertEquals(0, charts.prescribed.secondsRidden)
        // ...and the comparison is not, because it never used it.
        val effort = charts.effortAgainstPlan!!
        assertEquals(EffortAgainstPlan.Verdict.Harder, effort.verdict)
        assertEquals(1800, effort.heartHardSeconds)
    }

    @Test
    fun `a class the rider abandoned is judged on the part they rode`() {
        // A 30-minute class stopped at 15: only the prescribed blocks that were
        // reached count, and the second half of a threshold class is the hard
        // half, so what was ridden prescribed nothing hard at all.
        val effort = charts(900, thresholdClass(1800), { 160 }).effortAgainstPlan!!

        assertEquals(900, effort.prescribedSeconds)
        assertEquals(0, effort.prescribedHardSeconds)
        assertEquals(EffortAgainstPlan.Verdict.Harder, effort.verdict)
    }

    /**
     * The wording, on numbers chosen rather than clipped, because the sentence
     * is the part a rider actually meets.
     */
    @Test
    fun `the sentence names each scale in its own terms`() {
        val said = RideChartSummaries.effortAgainstPlan(
            EffortAgainstPlan(
                verdict = EffortAgainstPlan.Verdict.Harder,
                prescribedHardSeconds = 480,
                prescribedSeconds = 2400,
                heartHardSeconds = 1440,
                heartSeconds = 2400
            )
        )

        assertEquals(
            "Harder than the class asked — 24 minutes in your top two " +
                "heart-rate zones, against the 8 minutes it prescribed.",
            said
        )
        // H4 and Z4 are not the same zone (`HeartRateZone`), so neither side is
        // described in the other's language.
        assertTrue(said, !said.contains("Threshold"))
    }

    @Test
    fun `nothing to compare is silence rather than a hedge`() {
        assertEquals("", RideChartSummaries.effortAgainstPlan(null))
    }
}
