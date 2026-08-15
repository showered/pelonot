package com.pelonot.domain.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three properties that make a level a level (26.4).
 *
 * The owner's note asked for something "a bit like 'lvl' in video games", and
 * the reason that is not the FTP with a nicer badge is that a game level only
 * goes up, is earned by playing, and is comparable without a unit. The first of
 * those is the one a test can hold, and it is the one the FTP fails.
 */
class RiderLevelTest {

    /** A typical ride: thirty minutes, 200 kJ. Worth 20 + 30 + 20 = 70 points. */
    private fun rides(n: Int) = RidingTotals(
        rides = n,
        durationSec = n * 30L * 60,
        outputKj = n * 200.0
    )

    @Test
    fun `a rider who has never ridden is at the start, not at an achievement`() {
        val level = RiderLevel.of(RidingTotals())
        assertEquals(RiderLevel.FIRST_LEVEL, level.level)
        assertEquals(0, level.points)
        assertEquals(0f, level.progress, 0.0001f)
        assertTrue(level.isUnstarted)
    }

    @Test
    fun `the first finished ride is the one ride that moves the number`() {
        assertEquals(2, RiderLevel.of(rides(1)).level)
        assertFalse(RiderLevel.of(rides(1)).isUnstarted)
    }

    @Test
    fun `the curve slows down, which is what stops it being a score`() {
        // Level 3 at four rides, 4 at nine, 5 at sixteen: the square law.
        assertEquals(3, RiderLevel.of(rides(4)).level)
        assertEquals(4, RiderLevel.of(rides(9)).level)
        assertEquals(5, RiderLevel.of(rides(16)).level)
        assertEquals(11, RiderLevel.of(rides(100)).level)
    }

    @Test
    fun `a year of riding once a week is a plausible number and not an inflated one`() {
        // 22.5.2's rider: one ride a week, every week, for a year.
        assertEquals(8, RiderLevel.of(rides(52)).level)
        // Three years of the same.
        assertEquals(13, RiderLevel.of(rides(156)).level)
    }

    @Test
    fun `it never goes down as riding accumulates`() {
        var last = 0
        var lastPoints = -1
        for (n in 0..500) {
            val level = RiderLevel.of(rides(n))
            assertTrue("level fell at $n rides", level.level >= last)
            assertTrue("points fell at $n rides", level.points > lastPoints)
            last = level.level
            lastPoints = level.points
        }
    }

    @Test
    fun `showing up is worth more than any one long ride`() {
        // Ten twenty-minute rides against one three-and-a-half-hour ride, at
        // the same output. The rider who came back ten times is further on.
        val consistent = RidingTotals(rides = 10, durationSec = 10 * 20L * 60, outputKj = 1400.0)
        val single = RidingTotals(rides = 1, durationSec = 200L * 60, outputKj = 1400.0)
        assertTrue(RiderLevel.of(consistent).points > RiderLevel.of(single).points)
    }

    @Test
    fun `twice the watts is nothing like twice the level`() {
        // The fairness-between-bodies caveat, made a measurement: identical
        // time on the bike, double the output.
        val ordinary = rides(52)
        val powerful = ordinary.copy(outputKj = ordinary.outputKj * 2)
        val gain = RiderLevel.of(powerful).level - RiderLevel.of(ordinary).level
        assertTrue("a level ladder that rewards mass is the FTP again", gain <= 1)
    }

    @Test
    fun `progress runs from the level's own floor to the next one`() {
        val atFloor = RiderLevel.of(rides(4))
        assertEquals(RiderLevel.pointsToReach(atFloor.level), atFloor.points)
        assertEquals(0f, atFloor.progress, 0.0001f)

        val partWay = RiderLevel.of(rides(6))
        assertTrue(partWay.progress > 0f)
        assertTrue(partWay.progress < 1f)
        assertEquals(partWay.level, atFloor.level)
    }

    @Test
    fun `a level's floor is exactly the points that reach it`() {
        for (level in 1..40) {
            val exactly = RiderLevel.of(pointsAsTotals(RiderLevel.pointsToReach(level)))
            assertEquals("level $level", level, exactly.level)
        }
    }

    /** Points expressed purely as minutes, so a case can name a points figure directly. */
    private fun pointsAsTotals(points: Int) = RidingTotals(
        rides = 1,
        durationSec = ((points - RiderLevel.POINTS_PER_RIDE) * 60).toLong().coerceAtLeast(0),
        outputKj = 0.0
    )

    @Test
    fun `nonsense on the row cannot produce a nonsense level`() {
        // A negative duration is not a thing the recorder writes, but the level
        // is drawn beside a rider's name and must not be the place it shows up.
        val level = RiderLevel.of(RidingTotals(rides = 3, durationSec = -1_000, outputKj = -50.0))
        assertTrue(level.points >= 0)
        assertTrue(level.level >= RiderLevel.FIRST_LEVEL)
    }
}
