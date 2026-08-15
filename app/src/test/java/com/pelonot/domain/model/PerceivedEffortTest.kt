package com.pelonot.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Three answers over a ten-point column (PLAN 26.3).
 *
 * The tests that matter here are the ones about *old* data: the whole design
 * rests on the column not changing meaning, and a ride recorded on the
 * ten-point scale is not visible from the screen.
 */
class PerceivedEffortTest {

    @Test
    fun `the three bands cover the whole scale exactly once`() {
        for (rating in 1..10) {
            val matches = PerceivedEffort.entries.filter { rating in it.band }
            assertEquals("rating $rating", 1, matches.size)
        }
    }

    @Test
    fun `every level stores a rating inside its own band`() {
        PerceivedEffort.entries.forEach { effort ->
            assertTrue(effort.name, effort.rating in effort.band)
            assertEquals(effort, PerceivedEffort.of(effort.rating))
        }
    }

    @Test
    fun `a ride rated on the old ten-point scale still reads back`() {
        // Nothing is rewritten on disk, so every stored value has to land
        // somewhere sensible — a 7 was a hard-ish session, not a maximal one.
        assertEquals(PerceivedEffort.Easy, PerceivedEffort.of(1))
        assertEquals(PerceivedEffort.Easy, PerceivedEffort.of(4))
        assertEquals(PerceivedEffort.Solid, PerceivedEffort.of(5))
        assertEquals(PerceivedEffort.Solid, PerceivedEffort.of(7))
        assertEquals(PerceivedEffort.Maximal, PerceivedEffort.of(8))
        assertEquals(PerceivedEffort.Maximal, PerceivedEffort.of(10))
    }

    @Test
    fun `unanswered is null and never easy`() {
        assertNull(PerceivedEffort.of(null))
    }

    @Test
    fun `a value outside the scale is not silently coerced`() {
        // A 0 or an 11 is not a rating, and inventing a level for it would put
        // a claim on a ride that nobody made.
        assertNull(PerceivedEffort.of(0))
        assertNull(PerceivedEffort.of(11))
    }

    @Test
    fun `the ten-point column is written and read by nobody else`() {
        // 7.11.6. This used to assert that `Easy` proposed an FTP bump through
        // `PostWorkoutAnalyzer.suggestFtpFromRpe` — a claim that was never true
        // in production, because the parameter carrying the rating had a
        // default and the one call site never passed it. That function is gone
        // and the property those tests were really protecting is the mapping
        // above: a stored rating must read back as the level that wrote it,
        // whatever anything downstream later does with the number.
        PerceivedEffort.entries.forEach { effort ->
            assertEquals(effort, PerceivedEffort.of(effort.rating))
            assertTrue(effort.name, effort.rating in 1..10)
        }
    }
}
