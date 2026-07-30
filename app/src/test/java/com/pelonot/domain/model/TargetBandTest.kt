package com.pelonot.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetBandTest {

    private val cadence = TargetBand(80.0, 100.0)

    @Test
    fun `membership is inclusive at both edges`() {
        assertTrue(79.9 !in cadence)
        assertTrue(80.0 in cadence)
        assertTrue(100.0 in cadence)
        assertTrue(100.1 !in cadence)
    }

    @Test
    fun `classifies a value against the band`() {
        assertEquals(TargetStatus.Below, cadence.statusFor(70.0))
        assertEquals(TargetStatus.OnTarget, cadence.statusFor(90.0))
        assertEquals(TargetStatus.Above, cadence.statusFor(120.0))
    }

    @Test
    fun `an undefined band never reports the rider as off target`() {
        // A free ride prescribes nothing, so nothing can be wrong.
        assertEquals(TargetStatus.Unset, TargetBand.NONE.statusFor(90.0))
        assertFalse(TargetBand.NONE.statusFor(90.0).isOffTarget)
        assertFalse(TargetBand.NONE.isDefined)
    }

    @Test
    fun `the gauge is wider than the band so drift is visible`() {
        // Pinning the marker to an end stop hides exactly the information the
        // rider needs when they are off target.
        assertTrue(cadence.displayMin < cadence.min)
        assertTrue(cadence.displayMax > cadence.max)
        assertEquals(68.0, cadence.displayMin, 0.001)
        assertEquals(112.0, cadence.displayMax, 0.001)
    }

    @Test
    fun `the band sits inside the drawn gauge`() {
        assertTrue(cadence.bandStartFraction > 0f)
        assertTrue(cadence.bandEndFraction < 1f)
        assertTrue(cadence.bandStartFraction < cadence.bandEndFraction)
    }

    @Test
    fun `the midpoint of the band is the midpoint of the gauge`() {
        assertEquals(0.5f, cadence.fractionOf(90.0), 0.001f)
    }

    @Test
    fun `values beyond the gauge clamp to its ends`() {
        assertEquals(0f, cadence.fractionOf(0.0), 0.0001f)
        assertEquals(1f, cadence.fractionOf(400.0), 0.0001f)
    }

    @Test
    fun `the gauge never runs below zero for a low band`() {
        // Power at the bottom of Zone 1 has no negative headroom to show.
        val lowPower = TargetBand(0.0, 20.0)

        assertEquals(0.0, lowPower.displayMin, 0.001)
    }

    @Test
    fun `a degenerate band still has a drawable gauge`() {
        val pinpoint = TargetBand(200.0, 200.0)

        assertFalse(pinpoint.isDefined)
        assertTrue(pinpoint.displayMax > pinpoint.displayMin)
        assertEquals(0.5f, pinpoint.fractionOf(200.0), 0.001f)
    }

    @Test
    fun `an interval's cadence band comes straight from the template`() {
        val interval = Interval(0, 60, cadenceMin = 85, cadenceMax = 95, powerZoneNumber = 3)

        assertEquals(TargetBand(85.0, 95.0), interval.cadenceBand)
    }

    @Test
    fun `an interval's power band is scaled by the rider's intent`() {
        val interval = Interval(0, 60, cadenceMin = 85, cadenceMax = 95, powerZoneNumber = 4)

        val easy = interval.powerBand(ftp = 200.0, intent = RideIntent.JustStayFit)
        val hard = interval.powerBand(ftp = 200.0, intent = RideIntent.ReachNewMilestones)

        // Z4 is 91%-106% of FTP; the intent shifts the whole band.
        assertEquals(200.0 * 0.91 * 0.95, easy.min, 0.001)
        assertEquals(200.0 * 1.06 * 1.05, hard.max, 0.001)
        assertTrue(hard.min > easy.min)
    }
}
