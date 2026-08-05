package com.pelonot.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The live leaderboard (PLAN 24.3.10–24.3.13).
 *
 * The interesting cases are all about the *window*: which three rows a rider
 * sees, and what happens to them as the board moves underneath.
 */
class LiveLeaderboardTest {

    /** A rival who accumulates [perSecond] every second for [seconds]. */
    private fun ghost(name: String, perSecond: Double, seconds: Int) =
        LiveLeaderboard.Ghost(
            name = name,
            trace = RivalTrace.from(
                (0..seconds).map {
                    MetricSample(it, power = perSecond * 1000, cadence = 90.0, heartRate = null)
                }
            )
        )

    private fun board(vararg ghosts: LiveLeaderboard.Ghost) = LiveLeaderboard(ghosts.toList())

    // ── Nothing to race is nothing drawn (24.1.6) ───────────────────

    @Test
    fun `a board with nobody on it draws nothing at all`() {
        val empty = LiveLeaderboard(emptyList())

        assertTrue(empty.isEmpty)
        assertNull(empty.standingsAt(60, yourValue = 12.0))
    }

    // ── The three rows (24.3.13) ────────────────────────────────────

    @Test
    fun `the window is the row above you, you, and the row below`() {
        // Five on the board and the rider third: they see the one they are
        // chasing, themselves, and the one chasing them — never the whole list.
        val standings = board(
            ghost("A", perSecond = 5.0, seconds = 600),
            ghost("B", perSecond = 4.0, seconds = 600),
            ghost("D", perSecond = 2.0, seconds = 600),
            ghost("E", perSecond = 1.0, seconds = 600)
        ).standingsAt(100, yourValue = 300.0)!!

        assertEquals(5, standings.fieldSize)
        assertEquals(3, standings.yourRank)
        assertEquals(listOf("B", LiveStanding.YOU, "D"), standings.window.map { it.name })
    }

    @Test
    fun `leading still shows three rows, and says so`() {
        // 24.3.13 names this the state to design first, because it is the one
        // a rider wants to be in. The window slides rather than shrinking: a
        // card that lost a row at the top would change height mid-ride, which
        // is 11.6.8 all over again.
        val standings = board(
            ghost("B", perSecond = 4.0, seconds = 600),
            ghost("C", perSecond = 3.0, seconds = 600),
            ghost("D", perSecond = 2.0, seconds = 600)
        ).standingsAt(100, yourValue = 900.0)!!

        assertTrue(standings.leading)
        assertEquals(1, standings.yourRank)
        assertEquals(listOf(LiveStanding.YOU, "B", "C"), standings.window.map { it.name })
    }

    @Test
    fun `last still shows three rows`() {
        // The first ten seconds of every race: the whole field starts level and
        // anybody who moved first is ahead of a rider who has not turned a
        // pedal. It must not be a two-row card that grows a third row later.
        val standings = board(
            ghost("B", perSecond = 4.0, seconds = 600),
            ghost("C", perSecond = 3.0, seconds = 600),
            ghost("D", perSecond = 2.0, seconds = 600)
        ).standingsAt(100, yourValue = 1.0)!!

        assertEquals(4, standings.yourRank)
        assertEquals(listOf("C", "D", LiveStanding.YOU), standings.window.map { it.name })
    }

    @Test
    fun `a field smaller than the window is shown whole`() {
        val standings = board(ghost("B", perSecond = 4.0, seconds = 600))
            .standingsAt(100, yourValue = 100.0)!!

        assertEquals(2, standings.fieldSize)
        assertEquals(2, standings.window.size)
    }

    // ── The gap, and which way round it reads ───────────────────────

    @Test
    fun `a gap on a row is theirs minus yours, so the row above always reads plus`() {
        // The opposite convention to RivalStatus.gap, and deliberately: the
        // subject of a row is the competitor named on it. What keeps it
        // unambiguous is that it agrees with the ranking — above is +, below
        // is −, always.
        val standings = board(
            ghost("Ahead", perSecond = 4.0, seconds = 600),
            ghost("Behind", perSecond = 2.0, seconds = 600)
        ).standingsAt(100, yourValue = 300.0)!!

        val above = standings.window.first { it.name == "Ahead" }
        val below = standings.window.first { it.name == "Behind" }

        assertEquals(100.0, above.gapToYou, 0.001)
        assertTrue(below.gapToYou < 0)
        assertEquals(0.0, standings.window.first { it.isYou }.gapToYou, 0.0)
    }

    // ── A ghost that runs out (24.3.6) ──────────────────────────────

    @Test
    fun `a rival whose ride ended holds their final total and is marked done`() {
        // Never extrapolated forward — the same rule as isStaleAt and the
        // gap-not-a-clamp family. Their number stops; the rider's does not,
        // which is exactly how a shorter ride gets caught.
        val short = board(ghost("Short", perSecond = 5.0, seconds = 100))

        val during = short.standingsAt(50, yourValue = 0.0)!!.window.first { !it.isYou }
        val after = short.standingsAt(500, yourValue = 0.0)!!.window.first { !it.isYou }

        assertFalse(during.finished)
        assertTrue(after.finished)
        assertEquals(after.value, short.ghosts.first().trace.finalValue, 0.001)
    }

    @Test
    fun `catching a finished rival moves you past them`() {
        val short = board(ghost("Short", perSecond = 5.0, seconds = 100))
        val theirTotal = short.ghosts.first().trace.finalValue

        assertEquals(2, short.standingsAt(500, theirTotal - 1)!!.yourRank)
        assertEquals(1, short.standingsAt(500, theirTotal + 1)!!.yourRank)
    }

    // ── Ties, which are the start of every race ─────────────────────

    @Test
    fun `everybody level shares a rank and the rider is not placed below them`() {
        // Second zero, nobody has done anything, and the rider is not behind a
        // ghost they are exactly level with.
        val standings = board(
            ghost("A", perSecond = 5.0, seconds = 600),
            ghost("B", perSecond = 4.0, seconds = 600)
        ).standingsAt(0, yourValue = 0.0)!!

        assertEquals(1, standings.yourRank)
        assertTrue(standings.window.all { it.rank == 1 })
        assertEquals(LiveStanding.YOU, standings.window.first().name)
    }

    // ── 24.3.16: the one row a strip can afford ─────────────────────

    /** Mid-pack: the race is with whoever is immediately ahead. */
    @Test
    fun `the nearest row is the one immediately above you`() {
        val standings = board(
            ghost("A", perSecond = 5.0, seconds = 600),
            ghost("B", perSecond = 3.0, seconds = 600),
            ghost("C", perSecond = 1.0, seconds = 600)
        ).standingsAt(100, yourValue = 200.0)

        // A: 500, B: 300, you: 200, C: 100.
        assertEquals("B", standings!!.nearest!!.name)
        assertTrue(standings.nearest!!.gapToYou > 0)
    }

    /**
     * Leading, and the chip must not go blank at the moment a rider is doing
     * best — it turns round and shows the one chasing them.
     */
    @Test
    fun `leading turns the chip round to the rider being chased`() {
        val standings = board(
            ghost("A", perSecond = 1.0, seconds = 600),
            ghost("B", perSecond = 0.5, seconds = 600)
        ).standingsAt(100, yourValue = 500.0)

        assertTrue(standings!!.leading)
        assertEquals("A", standings.nearest!!.name)
        // Negative: you are ahead, and the sign is what says so on a strip
        // with one row on it.
        assertTrue(standings.nearest!!.gapToYou < 0)
    }

    /** A board with nobody on it draws nothing, on the overlay most of all. */
    @Test
    fun `no race means no chip`() {
        assertEquals(null, board().standingsAt(100, yourValue = 50.0))
    }
}
