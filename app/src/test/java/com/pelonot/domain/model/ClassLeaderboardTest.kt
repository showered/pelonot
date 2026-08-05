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
class ClassLeaderboardTest {

    private fun standing(
        id: Int,
        name: String,
        kj: Double,
        kg: Double = 70.0,
        accountId: String? = null
    ) = ClassLeaderboard.Standing(
        localUserId = id,
        accountId = accountId,
        name = name,
        outputKj = kj,
        weightKg = kg,
        source = ClassLeaderboard.Source.Household
    )

    /** A rider on another bike: an account, and no local profile (18.5). */
    private fun cloudStanding(accountId: String, name: String, kj: Double, kg: Double = 70.0) =
        ClassLeaderboard.Standing(
            localUserId = null,
            accountId = accountId,
            name = name,
            outputKj = kj,
            weightKg = kg,
            source = ClassLeaderboard.Source.Cloud
        )

    /**
     * **A rider who is both a housemate and signed in appears once** (18.9).
     *
     * The worst version of getting this wrong is not a duplicate row — it is
     * that the two numbers differ, because the cloud holds whatever it last
     * accepted and the tablet holds today's ride. So it does not read as a bug.
     * It reads as being beaten by yourself.
     */
    @Test
    fun `a housemate with an account is not also a cloud rider`() {
        val board = ClassLeaderboard.of(
            classId = "TH-01",
            standings = listOf(
                standing(1, "Simon", 240.0, accountId = "acct-simon"),
                cloudStanding("acct-simon", "Simon", 180.0),
                cloudStanding("acct-priya", "Priya", 200.0)
            ),
            youId = 1
        )

        assertEquals(listOf("Simon", "Priya"), board.entries.map { it.name })
        // The household number, not the cloud one, and not the higher one:
        // taking the best of the two would rank a rider on a ride they did
        // somewhere else.
        assertEquals(240.0, board.entries.first().outputKj, 0.001)
        assertEquals(ClassLeaderboard.Source.Household, board.entries.first().source)
    }

    @Test
    fun `a board of only housemates does not claim to cross bikes`() {
        val board = ClassLeaderboard.of(
            classId = "TH-01",
            standings = listOf(standing(1, "Sam", 180.0), standing(2, "Alex", 240.0)),
            youId = 1
        )
        assertFalse(board.crossesBikes)
    }

    @Test
    fun `one rider from elsewhere makes it a cross-bike board`() {
        val board = ClassLeaderboard.of(
            classId = "TH-01",
            standings = listOf(standing(1, "Sam", 180.0), cloudStanding("acct-x", "Kim", 240.0)),
            youId = 1
        )
        assertTrue(board.crossesBikes)
        assertEquals(ClassLeaderboard.Source.Cloud, board.entries.first().source)
    }

    /**
     * On a second bike the rider's local profile id is different, so "is this
     * me?" cannot be answered by the local id alone — which is the whole
     * argument of 14.2.1 arriving somewhere it can be seen.
     */
    @Test
    fun `you are recognised on a cloud row by your account`() {
        val board = ClassLeaderboard.of(
            classId = "TH-01",
            standings = listOf(
                cloudStanding("acct-me", "Simon", 240.0),
                cloudStanding("acct-other", "Kim", 180.0)
            ),
            youId = 99,
            yourAccountId = "acct-me"
        )
        assertTrue(board.entries.first { it.name == "Simon" }.isYou)
        assertFalse(board.entries.first { it.name == "Kim" }.isYou)
    }

    /**
     * A housemate with no account has a null `accountId`, and so may a cloud
     * row in a malformed response. Two nulls must not be treated as the same
     * rider — which is what a naive `accountId ==` comparison would do.
     */
    @Test
    fun `housemates without accounts are not merged with each other`() {
        val board = ClassLeaderboard.of(
            classId = "TH-01",
            standings = listOf(
                standing(1, "Sam", 180.0),
                standing(2, "Alex", 240.0),
                cloudStanding("acct-x", "Kim", 200.0)
            ),
            youId = 1
        )
        assertEquals(3, board.entries.size)
    }

    @Test
    fun `riders are placed best first`() {
        val board = ClassLeaderboard.of(
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
        val board = ClassLeaderboard.of(
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
        val board = ClassLeaderboard.of(
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
            ClassLeaderboard.of("TH-01", listOf(standing(1, "Sam", 180.0)), youId = 1)
                .isWorthShowing
        )
        assertFalse(ClassLeaderboard.of("TH-01", emptyList(), youId = 1).isWorthShowing)
        assertTrue(
            ClassLeaderboard.of(
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
        val board = ClassLeaderboard.of(
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
        val board = ClassLeaderboard.of(
            classId = "TH-01",
            standings = listOf(standing(1, "Sam", 240.0, kg = 0.0), standing(2, "Alex", 210.0)),
            youId = null
        )

        assertEquals(null, board.entries.first { it.name == "Sam" }.outputPerKg)
    }

    // ======================================================================
    // 24.1.8 — the podium and your own neighbourhood
    // ======================================================================

    private fun boardOf(size: Int, youAt: Int?): ClassLeaderboard =
        ClassLeaderboard.of(
            classId = "TH-01",
            standings = (1..size).map { standing(it, "Rider $it", (300 - it).toDouble()) },
            youId = youAt
        )

    /** Six is the ceiling, and a board at the ceiling is still a plain list. */
    @Test
    fun `a board that fits is drawn whole`() {
        val visible = boardOf(ClassLeaderboard.MAX_ROWS, youAt = 6).visible

        assertEquals(ClassLeaderboard.MAX_ROWS, visible.rows.size)
        assertEquals(0, visible.hidden)
        assertEquals(null, visible.breakAfter)
    }

    /**
     * The shape the item asks for: the top three, the rider and the row either
     * side of them, and a break where the board skips.
     */
    @Test
    fun `a long board keeps the podium and your own neighbourhood`() {
        val visible = boardOf(12, youAt = 9).visible

        assertEquals(
            listOf("Rider 1", "Rider 2", "Rider 3", "Rider 8", "Rider 9", "Rider 10"),
            visible.rows.map { it.name }
        )
        assertEquals(6, visible.hidden)
        // Between the podium and the neighbourhood, so the jump from 3rd to
        // 8th cannot read as a ranking bug.
        assertEquals(2, visible.breakAfter)
    }

    /**
     * The same rule and the same failure mode as the live board's sliding
     * window (24.3.13): being last must not remove you from your own board.
     */
    @Test
    fun `the rider is on the board even when they are last`() {
        val visible = boardOf(12, youAt = 12).visible

        assertTrue(visible.rows.last().isYou)
        assertEquals(listOf("Rider 1", "Rider 2", "Rider 3", "Rider 11", "Rider 12"), visible.rows.map { it.name })
    }

    /** Fourth means the two groups touch, so there is nothing to break. */
    @Test
    fun `a rider just below the podium gets a contiguous board`() {
        val visible = boardOf(12, youAt = 4).visible

        assertEquals(listOf("Rider 1", "Rider 2", "Rider 3", "Rider 4", "Rider 5"), visible.rows.map { it.name })
        assertEquals(null, visible.breakAfter)
    }

    /**
     * Class detail before the rider has ever ridden the class: there is no
     * neighbourhood, and the podium alone is the honest answer.
     */
    @Test
    fun `a board the rider is not on shows the podium and says how many are hidden`() {
        val visible = boardOf(12, youAt = null).visible

        assertEquals(ClassLeaderboard.PODIUM, visible.rows.size)
        assertEquals(9, visible.hidden)
        assertEquals(null, visible.breakAfter)
    }

    /**
     * `marksAnyRider` asked of the rows that are drawn. A caption explaining a
     * glyph that has been windowed away is the same fault one step along.
     */
    @Test
    fun `the other-bike caption follows the rows that survive the window`() {
        val standings = (1..12).map {
            ClassLeaderboard.Standing(
                localUserId = if (it == 12) null else it,
                accountId = if (it == 12) "cloud-rider" else null,
                name = "Rider $it",
                outputKj = (300 - it).toDouble(),
                weightKg = 70.0,
                source = if (it == 12) {
                    ClassLeaderboard.Source.Cloud
                } else {
                    ClassLeaderboard.Source.Household
                }
            )
        }
        val board = ClassLeaderboard.of("TH-01", standings, youId = 5)

        // The cloud rider is last and is windowed away.
        assertTrue(board.marksAnyRider)
        assertFalse(board.visible.marksAnyRider)
    }
}
