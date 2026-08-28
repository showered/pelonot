package com.pelonot.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rider's own rides of one length (PLAN 24.5).
 *
 * The ranking and the window live in the object rather than in the `ORDER BY`
 * for the reason `ClassLeaderboard` gives: a ranking rule that can only be
 * exercised against a database is a ranking rule nobody checks.
 */
class RidesOfThisLengthTest {

    private var n = 0

    private fun ride(kj: Double, title: String = "Endurance", id: String? = null) =
        RidesOfThisLength.Ride(
            workoutId = id ?: "w${n++}",
            classId = title.lowercase(),
            classTitle = title,
            recordedAt = 1_700_000_000_000L + n,
            outputKj = kj
        )

    @Test
    fun `one ride is not a comparison`() {
        // The same rule as 24.1.6, reached from the other direction: a single
        // row is the rider's own number with a rosette drawn on it, and it is
        // what everybody sees the first time they meet this card.
        assertFalse(RidesOfThisLength.of(1800, listOf(ride(200.0))).isWorthShowing)
        assertFalse(RidesOfThisLength(1800).isWorthShowing)
        assertTrue(RidesOfThisLength.of(1800, listOf(ride(200.0), ride(180.0))).isWorthShowing)
    }

    @Test
    fun `best first, whatever order the database returned them in`() {
        val board = RidesOfThisLength.of(
            1800,
            listOf(ride(180.0), ride(246.0), ride(210.0))
        )
        assertEquals(listOf(246.0, 210.0, 180.0), board.entries.map { it.outputKj })
        assertEquals(listOf(1, 2, 3), board.entries.map { it.rank })
    }

    @Test
    fun `two rides on the same output share a rank`() {
        // Not because ties are common, but because separating them would be
        // the app asserting a difference that is not in the data.
        val board = RidesOfThisLength.of(1800, listOf(ride(210.0), ride(210.0), ride(180.0)))
        assertEquals(listOf(1, 1, 3), board.entries.map { it.rank })
    }

    @Test
    fun `this ride is marked and nothing else is`() {
        val mine = ride(190.0, id = "tonight")
        val board = RidesOfThisLength.of(
            1800,
            listOf(ride(246.0), mine, ride(150.0)),
            thisRideId = "tonight"
        )
        assertEquals(listOf(false, true, false), board.entries.map { it.isThisRide })
    }

    @Test
    fun `on class detail there is no this-ride and no row is marked`() {
        // The rider is choosing rather than reviewing, and a highlighted row
        // would be claiming one of these is the occasion on screen.
        val board = RidesOfThisLength.of(1800, listOf(ride(246.0), ride(190.0)))
        assertTrue(board.entries.none { it.isThisRide })
    }

    // ── Rule 3: the class is what makes the comparison honest ────────────

    @Test
    fun `crossing classes is only claimed when the board actually does`() {
        val oneClass = RidesOfThisLength.of(
            1800,
            listOf(ride(246.0, "Sprints"), ride(190.0, "Sprints"))
        )
        assertFalse(oneClass.crossesClasses)

        val several = RidesOfThisLength.of(
            1800,
            listOf(ride(246.0, "Sprints"), ride(148.0, "Recovery"))
        )
        assertTrue(several.crossesClasses)
    }

    @Test
    fun `a harder class outranking an easier one is left standing, and named`() {
        // This is the objection 24.5.3 exists for, and the object's answer is
        // not to reorder anything: the Sprints ride really did more work. What
        // stops it reading as a verdict on the recovery ride is that both rows
        // say which class they were.
        val board = RidesOfThisLength.of(
            1800,
            listOf(ride(148.0, "Recovery"), ride(246.0, "Sprints"))
        )
        assertEquals("Sprints", board.entries.first().classTitle)
        assertEquals("Recovery", board.entries.last().classTitle)
    }

    // ── The window ──────────────────────────────────────────────────────

    @Test
    fun `a short board is drawn whole with nothing hidden`() {
        val board = RidesOfThisLength.of(1800, (1..RidesOfThisLength.MAX_ROWS).map {
            ride(100.0 + it)
        })
        val visible = board.visible
        assertEquals(RidesOfThisLength.MAX_ROWS, visible.rows.size)
        assertEquals(0, visible.hidden)
        assertNull(visible.breakAfter)
    }

    @Test
    fun `a long board keeps the best few and counts the rest`() {
        val board = RidesOfThisLength.of(1800, (1..20).map { ride(100.0 + it) })
        val visible = board.visible
        assertEquals(RidesOfThisLength.MAX_ROWS, visible.rows.size)
        assertEquals(20 - RidesOfThisLength.MAX_ROWS, visible.hidden)
        // Nothing to mark: the rows drawn are contiguous from the top.
        assertNull(visible.breakAfter)
    }

    @Test
    fun `tonight is kept even when it is nowhere near the best`() {
        // The failure this guards is the one that would make the card useless
        // on the night it matters most: a rider who has just ridden their worst
        // half-hour sees the bar they missed and no sign of themselves.
        val rides = (1..20).map { ride(100.0 + it) } + ride(1.0, id = "tonight")
        val board = RidesOfThisLength.of(1800, rides, thisRideId = "tonight")
        val visible = board.visible

        assertEquals(RidesOfThisLength.MAX_ROWS + 1, visible.rows.size)
        assertTrue(visible.rows.last().isThisRide)
        assertEquals(21, visible.rows.last().rank)
        // The gap is drawn rather than implied: rows reading 6 and 21 with
        // nothing between them look like a ranking fault instead of a window.
        assertEquals(RidesOfThisLength.MAX_ROWS - 1, visible.breakAfter)
        assertEquals(20 - RidesOfThisLength.MAX_ROWS, visible.hidden)
    }

    @Test
    fun `tonight inside the window is not drawn twice`() {
        val rides = (1..20).map { ride(100.0 + it) } + ride(999.0, id = "tonight")
        val board = RidesOfThisLength.of(1800, rides, thisRideId = "tonight")
        val visible = board.visible

        assertEquals(RidesOfThisLength.MAX_ROWS, visible.rows.size)
        assertEquals(1, visible.rows.count { it.isThisRide })
        assertNull(visible.breakAfter)
    }

    @Test
    fun `everything not drawn is counted`() {
        // 24.1.8's rule: a board that stops without saying so is a false claim
        // about how much riding there has been.
        val rides = (1..20).map { ride(100.0 + it) } + ride(1.0, id = "tonight")
        val board = RidesOfThisLength.of(1800, rides, thisRideId = "tonight")
        val visible = board.visible
        assertEquals(board.entries.size, visible.rows.size + visible.hidden)
    }
}
