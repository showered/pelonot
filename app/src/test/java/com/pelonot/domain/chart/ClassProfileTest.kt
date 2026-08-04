package com.pelonot.domain.chart

import com.pelonot.domain.model.Interval
import com.pelonot.domain.model.PowerZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 22.7.2 — the shape of a class, and the sentence that says it.
 *
 * The fixtures are two real classes out of the bundled library rather than
 * invented ones: `The Long Climb 30`, whose whole point is a single long block,
 * and a `4×2` repeat set, whose whole point is that it is not. Those are the
 * two cases the summary has to tell apart, and they are also the two shapes the
 * profile drawing has to make look different from across the room.
 */
class ClassProfileTest {

    private fun interval(startSec: Int, endSec: Int, zone: Int) = Interval(
        startSec = startSec,
        endSec = endSec,
        cadenceMin = 80,
        cadenceMax = 90,
        powerZoneNumber = zone
    )

    /** `The Long Climb 30`: warm-up, tempo, one 15-minute block, cool-down. */
    private val longClimb = listOf(
        interval(0, 180, 1),
        interval(180, 360, 2),
        interval(360, 480, 3),
        interval(480, 660, 3),
        interval(660, 1560, 4),
        interval(1560, 1680, 2),
        interval(1680, 1800, 1)
    )

    @Test
    fun `one long block is one effort, not the two blocks it is made of`() {
        val profile = ClassProfile.of(longClimb)

        assertEquals(PowerZone.Z4, profile.hardest)
        assertEquals(listOf(900), profile.efforts)
        assertEquals("30 min · one 15 min effort at Lactate Threshold", profile.summary)
    }

    @Test
    fun `adjacent blocks at the hardest zone are one effort`() {
        // The same fifteen minutes, split in two to change the cadence — which
        // is a thing the class library actually does. Calling it two efforts
        // would describe a workout with a rest in it that nobody gets.
        val split = listOf(
            interval(0, 180, 1),
            interval(180, 630, 4),
            interval(630, 1080, 4),
            interval(1080, 1200, 1)
        )

        assertEquals(listOf(900), ClassProfile.of(split).efforts)
    }

    @Test
    fun `repeats are counted and read the way they are said out loud`() {
        // 4 × 2 min at Z5 with 2 min recoveries.
        val blocks = mutableListOf(interval(0, 300, 1))
        var t = 300
        repeat(4) {
            blocks += interval(t, t + 120, 5)
            blocks += interval(t + 120, t + 240, 1)
            t += 240
        }
        val profile = ClassProfile.of(blocks)

        assertEquals(listOf(120, 120, 120, 120), profile.efforts)
        assertEquals("21 min · 4 × 2 min at VO2 Max", profile.summary)
    }

    @Test
    fun `efforts of different lengths are counted rather than multiplied`() {
        // A pyramid: 1, 2 and 3 minutes at the top zone. "3 × 2 min" would be a
        // lie about all three of them.
        val blocks = listOf(
            interval(0, 60, 5),
            interval(60, 120, 1),
            interval(120, 240, 5),
            interval(240, 300, 1),
            interval(300, 480, 5)
        )

        assertEquals("8 min · 3 efforts at VO2 Max", ClassProfile.of(blocks).summary)
    }

    @Test
    fun `blocks tile the whole width exactly once`() {
        val profile = ClassProfile.of(longClimb)

        assertEquals(0f, profile.blocks.first().startFraction, 0.0001f)
        val last = profile.blocks.last()
        assertEquals(1f, last.startFraction + last.widthFraction, 0.0001f)
        // No gaps and no overlaps: every block starts where the one before it
        // ended, or the profile is drawing a class that is not this one.
        profile.blocks.zipWithNext { a, b ->
            assertEquals(a.startFraction + a.widthFraction, b.startFraction, 0.0001f)
        }
    }

    @Test
    fun `zone one still draws a bar`() {
        // Strictly proportional, Z1 would be a seventh of the plot and a
        // warm-up would read as an empty left-hand edge rather than as riding.
        val heights = ClassProfile.of(longClimb).blocks.associate { it.zone to it.heightFraction }

        assertTrue("zone 1 must be visible", heights.getValue(PowerZone.Z1) > 0.1f)
        assertTrue(heights.getValue(PowerZone.Z1) < heights.getValue(PowerZone.Z2))
        assertTrue(heights.getValue(PowerZone.Z4) <= 1f)
    }

    @Test
    fun `zone seven fills the plot and the ordering never inverts`() {
        val all = (1..7).map { interval((it - 1) * 60, it * 60, it) }
        val heights = ClassProfile.of(all).blocks.map { it.heightFraction }

        assertEquals(1f, heights.last(), 0.0001f)
        assertEquals(heights.sorted(), heights)
    }

    @Test
    fun `a class with no intervals has no shape and does not divide by zero`() {
        val profile = ClassProfile.of(emptyList())

        assertEquals(0, profile.totalSec)
        assertNull(profile.hardest)
        assertNull(profile.shape)
        assertTrue(profile.blocks.isEmpty())
    }

    @Test
    fun `intervals arriving out of order are put back in time order`() {
        val shuffled = longClimb.reversed()
        val profile = ClassProfile.of(shuffled)

        assertEquals(longClimb.map { it.startSec }, profile.blocks.map { it.startSec })
    }
}
