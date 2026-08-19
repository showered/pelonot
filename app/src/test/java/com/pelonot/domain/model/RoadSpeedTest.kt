package com.pelonot.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PLAN 2.5a. The owner's note was a magnitude claim — *"I rode at about 130W for
 * 30 minutes and only clocked something like 5km. It's surely WAY off"* — so the
 * tests that matter here are magnitude tests, not round-trips of the formula
 * against itself.
 */
class RoadSpeedTest {

    @Test
    fun `the equation the curve claims to solve is the one it solves`() {
        // P·η = v·(Crr·m·g + ½ρCdA·v²). Everything else in this file is a
        // consequence of this holding, so it is checked directly rather than
        // trusted: a wrong cube root would still be monotone and plausible.
        for (watts in listOf(40.0, 90.0, 130.0, 250.0, 600.0)) {
            val v = RoadSpeed.metresPerSecond(watts)
            val demanded = v * (0.005 * 84.0 * 9.80665 + 0.5 * 1.225 * 0.40 * v * v)
            assertEquals("at $watts W", watts * 0.97, demanded, 0.001)
        }
    }

    @Test
    fun `the owner's ride lands where a Peloton puts it`() {
        // Half an hour at 130 W. The old model said 5.4 km; theirs says about
        // 13.7. Anything inside a kilometre of that is the same claim.
        val km = RoadSpeed.kilometres(watts = 130.0, seconds = 30 * 60.0)

        assertEquals(13.2, km, 1.0)
    }

    @Test
    fun `no watts is no movement`() {
        // A coasting rider covers no ground, and the curve must not invent a
        // floor for them: a gap in the effort is a gap in the distance.
        assertEquals(0.0, RoadSpeed.metresPerSecond(0.0), 0.0)
        assertEquals(0.0, RoadSpeed.metresPerSecond(-50.0), 0.0)
    }

    @Test
    fun `doubling the watts does not double the speed`() {
        // Drag goes as v³, so it very much should not — and this is the
        // property the old cadence model had no way to express at all.
        val slow = RoadSpeed.kmPerHour(100.0)
        val fast = RoadSpeed.kmPerHour(200.0)

        assertTrue("$fast should exceed $slow", fast > slow)
        assertTrue("but not by double: $fast vs $slow", fast < slow * 1.5)
    }

    @Test
    fun `the speeds are ones a cyclist would recognise`() {
        // A sanity fence rather than a fit. If a future change to the constants
        // puts a 100 W rider at 45 km/h, this is what says so.
        assertEquals(23.7, RoadSpeed.kmPerHour(100.0), 0.5)
        assertEquals(31.1, RoadSpeed.kmPerHour(200.0), 0.5)
        assertEquals(40.2, RoadSpeed.kmPerHour(400.0), 0.5)
    }
}
