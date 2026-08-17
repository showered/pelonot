package com.pelonot.domain.progress

import com.pelonot.domain.chart.TimeInZone
import com.pelonot.domain.model.PowerZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A month by zone (PLAN 21.4.3).
 *
 * The arithmetic is a sum, so what is actually worth testing is the two claims
 * the card makes underneath it: **which seconds count as easy and hard**, and
 * **what happens to a ride nobody could count**. The second is the one with
 * teeth — a ride that contributes nothing must be visible as a ride that
 * contributed nothing, not disappear out of the denominator.
 */
class RidingIntensityTest {

    private fun ride(
        vararg seconds: Pair<PowerZone, Int>,
        stopped: Int = 0
    ) = TimeInZone(secondsByZone = seconds.toMap(), secondsStopped = stopped)

    @Test
    fun `seconds add across rides, zone by zone`() {
        val intensity = RidingIntensity.of(
            listOf(
                ride(PowerZone.Z2 to 600, PowerZone.Z4 to 120),
                ride(PowerZone.Z2 to 300, PowerZone.Z5 to 60)
            )
        )

        assertEquals(900, intensity.timeInZone.secondsByZone[PowerZone.Z2])
        assertEquals(120, intensity.timeInZone.secondsByZone[PowerZone.Z4])
        assertEquals(60, intensity.timeInZone.secondsByZone[PowerZone.Z5])
        assertEquals(1080, intensity.timeInZone.totalSeconds)
        assertEquals(2, intensity.ridesCounted)
        assertTrue(intensity.isComplete)
    }

    @Test
    fun `easy is Z1 and Z2, hard is Z4 and above, and Z3 is neither`() {
        val intensity = RidingIntensity.of(
            listOf(
                ride(
                    PowerZone.Z1 to 100,
                    PowerZone.Z2 to 300,
                    PowerZone.Z3 to 200,
                    PowerZone.Z4 to 300,
                    PowerZone.Z7 to 100
                )
            )
        )

        assertEquals(400, intensity.easySeconds)
        assertEquals(400, intensity.hardSeconds)
        // The middle is in the bar and in neither number, which is the point:
        // the two fractions do not have to add to one.
        assertEquals(0.4f, intensity.easyFraction, 0.001f)
        assertEquals(0.4f, intensity.hardFraction, 0.001f)
    }

    @Test
    fun `a ride nobody could count stays in the denominator`() {
        val intensity = RidingIntensity.of(
            listOf(
                ride(PowerZone.Z2 to 600),
                null,
                null
            )
        )

        assertEquals(1, intensity.ridesCounted)
        assertEquals(3, intensity.ridesInWindow)
        assertEquals(2, intensity.ridesUncounted)
        assertFalse(intensity.isComplete)
        // And it is said, rather than left to be inferred from a short bar.
        assertTrue(
            RidingIntensitySummary.caption(intensity).contains("from 1 of 3 rides")
        )
    }

    @Test
    fun `stopped seconds are carried, and never divided into a zone`() {
        val intensity = RidingIntensity.of(
            listOf(
                ride(PowerZone.Z2 to 600, stopped = 60),
                ride(PowerZone.Z2 to 300, stopped = 40)
            )
        )

        assertEquals(900, intensity.timeInZone.totalSeconds)
        assertEquals(100, intensity.timeInZone.secondsStopped)
        assertEquals(1000, intensity.timeInZone.recordedSeconds)
        assertTrue(RidingIntensitySummary.caption(intensity).contains("pedalling for"))
    }

    @Test
    fun `a window of rides with nothing behind them says so instead of drawing zero`() {
        val intensity = RidingIntensity.of(listOf(null, null))

        assertFalse(intensity.hasAnything)
        assertEquals(0, intensity.ridesCounted)
        assertEquals(
            "None of these rides kept a per-second record to count.",
            RidingIntensitySummary.mix(intensity)
        )
    }

    @Test
    fun `the sentence is spelled out, and reads as a quantity rather than a clock`() {
        val intensity = RidingIntensity.of(
            listOf(ride(PowerZone.Z2 to 4 * 3600, PowerZone.Z4 to 720))
        )

        val mix = RidingIntensitySummary.mix(intensity)
        assertTrue(mix, mix.startsWith("4 hours 12 minutes ridden"))
        assertTrue(mix, mix.contains("95% easy (Z1–Z2)"))
        assertTrue(mix, mix.contains("5% hard (Z4 and above)"))
        // 21.4.4: an observation, and never a target to have missed.
        assertFalse(mix, mix.contains("80"))
        assertFalse(mix.lowercase(), mix.lowercase().contains("should"))
    }

    @Test
    fun `under an hour is minutes alone, and one of anything is singular`() {
        val hour = RidingIntensity.of(listOf(ride(PowerZone.Z2 to 3600)))
        assertTrue(
            RidingIntensitySummary.mix(hour).startsWith("1 hour ridden")
        )

        val minute = RidingIntensity.of(listOf(ride(PowerZone.Z2 to 60)))
        assertTrue(
            RidingIntensitySummary.mix(minute).startsWith("1 minute ridden")
        )
    }

    @Test
    fun `a complete window says the window and nothing else`() {
        val intensity = RidingIntensity.of(listOf(ride(PowerZone.Z2 to 600)))

        assertEquals(
            "Time in zone across the last 30 days",
            RidingIntensitySummary.caption(intensity)
        )
    }
}
