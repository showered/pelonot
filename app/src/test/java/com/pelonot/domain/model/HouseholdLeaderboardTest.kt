package com.pelonot.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The household board's rules (PLAN 24.1), tested without a database.
 *
 * The exclusions — guests, simulated rides, rides with no samples — belong to
 * the query and are covered by `WorkoutDaoTest`. What is here is everything
 * that decides how riders are *placed*, and the one rule about when a board is
 * not worth drawing at all.
 */
class HouseholdLeaderboardTest {

    private fun standing(id: Int, name: String, kj: Double, kg: Double = 70.0) =
        HouseholdLeaderboard.Standing(
            localUserId = id,
            name = name,
            outputKj = kj,
            weightKg = kg
        )

    @Test
    fun `riders are placed best first`() {
        val board = HouseholdLeaderboard.of(
            classId = "TH-01",
            standings = listOf(
                standing(1, "Sam", 180.0),
                standing(2, "Alex", 240.0),
                standing(3, "Jo", 210.0)
            ),
            youId = 1
        )

        assertEquals(listOf("Alex", "Jo", "Sam"), board.entries.map { it.name })
        assertEquals(listOf(1, 2, 3), board.entries.map { it.rank })
    }

    /**
     * Two riders who did identical work are not separated by whichever row the
     * database happened to return first.
     */
    @Test
    fun `riders on identical output share a rank`() {
        val board = HouseholdLeaderboard.of(
            classId = "TH-01",
            standings = listOf(
                standing(1, "Sam", 200.0),
                standing(2, "Alex", 200.0),
                standing(3, "Jo", 150.0)
            ),
            youId = null
        )

        assertEquals(listOf(1, 1, 3), board.entries.map { it.rank })
    }

    @Test
    fun `the rider looking at it is marked, and nobody else is`() {
        val board = HouseholdLeaderboard.of(
            classId = "TH-01",
            standings = listOf(standing(1, "Sam", 180.0), standing(2, "Alex", 240.0)),
            youId = 1
        )

        assertEquals("Sam", board.entries.single { it.isYou }.name)
    }

    /**
     * 24.1.6. A leaderboard with one row on it is the rider's own number with
     * a rosette drawn on it, and the dashboard already tells that story.
     */
    @Test
    fun `a household of one is not a leaderboard`() {
        assertFalse(
            HouseholdLeaderboard.of("TH-01", listOf(standing(1, "Sam", 180.0)), youId = 1)
                .isWorthShowing
        )
        assertFalse(HouseholdLeaderboard.of("TH-01", emptyList(), youId = 1).isWorthShowing)
        assertTrue(
            HouseholdLeaderboard.of(
                "TH-01",
                listOf(standing(1, "Sam", 180.0), standing(2, "Alex", 240.0)),
                youId = 1
            ).isWorthShowing
        )
    }

    /**
     * 24.1.3. Output per kilogram is offered *beside* the ranking and never as
     * it — raw output is the work actually done, and the lighter rider is the
     * one who wants the other number.
     */
    @Test
    fun `output per kilogram can reverse the order without changing it`() {
        val board = HouseholdLeaderboard.of(
            classId = "TH-01",
            standings = listOf(
                standing(1, "Sam", 240.0, kg = 95.0),
                standing(2, "Alex", 210.0, kg = 60.0)
            ),
            youId = null
        )

        // Sam did more work and is first.
        assertEquals("Sam", board.entries.first().name)
        // Alex did more per kilogram, and the card shows it beside the rank.
        assertTrue(board.entries[1].outputPerKg!! > board.entries[0].outputPerKg!!)
    }

    /** Better than dividing by a default weight and printing a made-up number. */
    @Test
    fun `a profile with no usable weight has no per-kilogram figure`() {
        val board = HouseholdLeaderboard.of(
            classId = "TH-01",
            standings = listOf(standing(1, "Sam", 240.0, kg = 0.0), standing(2, "Alex", 210.0)),
            youId = null
        )

        assertEquals(null, board.entries.first { it.name == "Sam" }.outputPerKg)
    }
}
