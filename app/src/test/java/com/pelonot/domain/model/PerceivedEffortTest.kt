package com.pelonot.domain.model

import com.pelonot.data.service.PostWorkoutAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Three answers over a ten-point column (PLAN 26.3).
 *
 * The tests that matter here are the ones about *old* data and about the one
 * consumer that reads the number rather than displaying it: the whole design
 * rests on the column not changing meaning, and neither of those is visible
 * from the screen.
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
    fun `comfortable still proposes an FTP bump on a hard class`() {
        // The one consumer that reads the number rather than showing it
        // (`EASY_RPE_THRESHOLD` is 4). If `Easy` had stored 5, a hard class
        // that felt comfortable would silently stop being evidence of
        // improvement — no screen would look any different.
        val analyzer = PostWorkoutAnalyzer()
        val proposal = analyzer.suggestFtpFromRpe(
            rpe = PerceivedEffort.Easy.rating,
            isHardClass = true,
            currentFtp = 200.0
        )

        assertNotNull(proposal)
        assertTrue(proposal!! > 200.0)
    }

    @Test
    fun `a good workout and everything I had do not propose one`() {
        val analyzer = PostWorkoutAnalyzer()
        assertNull(
            analyzer.suggestFtpFromRpe(
                rpe = PerceivedEffort.Solid.rating,
                isHardClass = true,
                currentFtp = 200.0
            )
        )
        assertNull(
            analyzer.suggestFtpFromRpe(
                rpe = PerceivedEffort.Maximal.rating,
                isHardClass = true,
                currentFtp = 200.0
            )
        )
    }
}
