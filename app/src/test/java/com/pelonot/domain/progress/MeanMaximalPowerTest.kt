package com.pelonot.domain.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Personal bests by duration (PLAN 16.3.3). */
class MeanMaximalPowerTest {

    private fun ride(seconds: Int, watts: (Int) -> Double = { 150.0 }) =
        (0 until seconds).map { PowerSample(it, watts(it)) }

    @Test
    fun `a ride shorter than the window has no best for it`() {
        // Null, not zero: "no twenty-minute effort in this ride" and "a
        // twenty-minute effort at 0 W" are different claims about a rider.
        assertNull(MeanMaximalPower.best(ride(600), windowSec = 1_200))
        assertNull(MeanMaximalPower.best(emptyList(), windowSec = 5))
    }

    @Test
    fun `the best window is found wherever it is`() {
        // Twenty seconds at 400 W in the middle of an easy hour.
        val samples = ride(3_600) { if (it in 1_000 until 1_020) 400.0 else 100.0 }

        assertEquals(400.0, MeanMaximalPower.best(samples, 5)!!, 0.001)
        // A minute containing that surge averages out to less than the surge.
        val minute = MeanMaximalPower.best(samples, 60)!!
        assertTrue("$minute", minute in 150.0..200.0)
        // And the whole hour barely notices it: 20 seconds of 400 W lifts a
        // 100 W hour by 1.7 W, which is the arithmetic that makes a curve of
        // these worth reading rather than one number.
        assertEquals(101.67, MeanMaximalPower.best(samples, 3_600)!!, 0.01)
    }

    /**
     * The rule the whole object exists for. A ride with a bottle stop in it has
     * two shorter efforts, not one long one, and averaging across the seconds
     * nobody recorded would hand out a best that never happened.
     */
    @Test
    fun `a window may not span a gap in the recording`() {
        // Nine minutes, four minutes missing, nine minutes — 18 minutes of
        // riding but never ten consecutive.
        val samples = ride(540) + (780 until 1_320).map { PowerSample(it, 150.0) }

        assertNull(MeanMaximalPower.best(samples, 600))
        // And the longest run that *is* continuous still counts.
        assertEquals(150.0, MeanMaximalPower.best(samples, 540)!!, 0.001)
    }

    @Test
    fun `the best of two runs is the better of the two`() {
        val first = (0 until 300).map { PowerSample(it, 200.0) }
        val second = (600 until 900).map { PowerSample(it, 250.0) }

        assertEquals(250.0, MeanMaximalPower.best(first + second, 300)!!, 0.001)
    }

    @Test
    fun `only the windows the ride actually held come back`() {
        val bests = MeanMaximalPower.bests(ride(400))

        assertTrue(bests.containsKey(5))
        assertTrue(bests.containsKey(60))
        assertTrue(bests.containsKey(300))
        // No twenty minutes and no hour in a seven-minute ride.
        assertTrue(bests.keys.none { it >= 1_200 })
    }

    @Test
    fun `out of order samples do not invent a gap`() {
        // Room returns them ordered, but nothing in the type says so, and a
        // shuffled list must not read as 2,700 one-second runs.
        val shuffled = ride(300).shuffled()

        assertEquals(150.0, MeanMaximalPower.best(shuffled, 300)!!, 0.001)
    }

    @Test
    fun `the labels are what a rider would say`() {
        assertEquals("5 seconds", MeanMaximalPower.label(5))
        // Observed on the AVD reading "1 minutes", which is the kind of thing a
        // rider notices before they notice the arithmetic is right.
        assertEquals("1 minute", MeanMaximalPower.label(60))
        assertEquals("20 minutes", MeanMaximalPower.label(1_200))
        assertEquals("1 hour", MeanMaximalPower.label(3_600))
    }
}
