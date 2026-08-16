package com.pelonot.domain.model

import com.pelonot.domain.identity.Avatar
import com.pelonot.domain.progress.RiderLevel
import com.pelonot.domain.progress.RidingTotals
import com.pelonot.domain.social.GhostKind
import com.pelonot.domain.social.RaceIdentity
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

    // ── The window (24.3.13, widened to six by 24.3.18c) ────────────
    //
    // Three until the thirty-second sitting. The rule these hold is unchanged
    // and is the one that matters: **the window slides rather than shrinking**,
    // so the card is the same height at the top of the board, at the bottom,
    // and in the middle (11.6.8). Only the number changed, and it changed
    // because the space was measured rather than assumed.

    private fun crowd(size: Int) = (1..size).map { n ->
        ghost("R$n", perSecond = 10.0 - n * 0.1, seconds = 600)
    }.toTypedArray()

    @Test
    fun `the window is centred on you, and slides rather than shrinking`() {
        // Ten on the board and the rider in the middle of them.
        val standings = board(*crowd(9)).standingsAt(100, yourValue = 550.0)!!

        assertEquals(10, standings.fieldSize)
        assertEquals(LiveLeaderboard.WINDOW, standings.window.size)
        assertTrue("the rider must be on their own board", standings.window.any { it.isYou })
    }

    @Test
    fun `leading still shows a full window, and says so`() {
        // 24.3.13 names this the state to design first, because it is the one
        // a rider wants to be in. A card that lost a row at the top would
        // change height mid-ride, which is 11.6.8 all over again.
        val standings = board(*crowd(9)).standingsAt(100, yourValue = 9_000.0)!!

        assertTrue(standings.leading)
        assertEquals(1, standings.yourRank)
        assertEquals(LiveLeaderboard.WINDOW, standings.window.size)
        assertEquals(LiveStanding.YOU, standings.window.first().name)
    }

    @Test
    fun `last still shows a full window`() {
        // The first ten seconds of every race: the whole field starts level and
        // anybody who moved first is ahead of a rider who has not turned a
        // pedal. It must not be a short card that grows rows later.
        val standings = board(*crowd(9)).standingsAt(100, yourValue = 1.0)!!

        assertEquals(10, standings.yourRank)
        assertEquals(LiveLeaderboard.WINDOW, standings.window.size)
        assertEquals(LiveStanding.YOU, standings.window.last().name)
    }

    /** 24.3.18c: the window is what a hand-free glance gets, not the field. */
    @Test
    fun `the whole field is carried beside the window, for scrolling`() {
        val standings = board(*crowd(9)).standingsAt(100, yourValue = 550.0)!!

        assertEquals(10, standings.all.size)
        assertEquals(standings.fieldSize, standings.all.size)
        assertTrue(standings.all.size > standings.window.size)
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

    // ── Modelled watts narrow the board, they do not empty it (24.3.7a) ──

    private fun ghost(name: String, perSecond: Double, seconds: Int, kind: GhostKind) =
        ghost(name, perSecond, seconds).copy(kind = kind)

    @Test
    fun `a modelled ride keeps the generated targets and loses the real rides`() {
        val narrowed = LiveLeaderboard(
            listOf(
                ghost("Ava", perSecond = 1.0, seconds = 600, kind = GhostKind.Human),
                ghost("Your best", perSecond = 0.9, seconds = 600, kind = GhostKind.YourBest),
                ghost("The plan", perSecond = 0.8, seconds = 600, kind = GhostKind.Prescribed),
                ghost("Your usual", perSecond = 0.7, seconds = 600, kind = GhostKind.Usual)
            )
        ).generatedOnly()

        assertEquals(
            listOf("The plan", "Your usual"),
            narrowed.ghosts.map { it.name }
        )
    }

    /**
     * The owner's rule: *"There should ALWAYS be a leaderboard even if it's
     * only CPU ghosts you're up against."* A class nobody has ridden gives one
     * generated row, and one generated row plus you is still a race.
     */
    @Test
    fun `a board of nothing but generated targets still draws`() {
        val standings = LiveLeaderboard(
            listOf(ghost("The plan", perSecond = 1.0, seconds = 600, kind = GhostKind.Prescribed))
        ).generatedOnly().standingsAt(100, yourValue = 50.0)

        assertEquals(2, standings!!.fieldSize)
        assertEquals("The plan", standings.nearest!!.name)
        assertTrue(standings.nearest!!.isGhost)
    }

    /** Every row was somebody's real ride, so there is honestly nothing left. */
    @Test
    fun `a modelled ride against real rides only is no race at all`() {
        val narrowed = LiveLeaderboard(
            listOf(
                ghost("Ava", perSecond = 1.0, seconds = 600, kind = GhostKind.Human),
                ghost("Your best", perSecond = 0.9, seconds = 600, kind = GhostKind.YourBest)
            )
        ).generatedOnly()

        assertTrue(narrowed.isEmpty)
        assertNull(narrowed.standingsAt(100, yourValue = 50.0))
    }

    /**
     * The floor exists to put the first rung above the field. Keeping one set
     * by a housemate's real ride would leave the rider chasing a rung nothing
     * on their board can reach.
     */
    @Test
    fun `the milestone floor comes down with the rides that set it`() {
        val narrowed = LiveLeaderboard(
            ghosts = listOf(
                ghost("Ava", perSecond = 1.0, seconds = 600, kind = GhostKind.Human),
                ghost("The plan", perSecond = 0.2, seconds = 600, kind = GhostKind.Prescribed)
            ),
            pacer = LiveLeaderboard.Pacer(durationSec = 600, floor = 600.0)
        ).generatedOnly()

        // Ava's 600 set the old floor; the plan's 120 is what is left.
        assertEquals(120.0, narrowed.pacer!!.floor, 0.001)
    }

    // ── Who a row is, and who it is not (24.3.19a) ──────────────────
    //
    // The rule the owner's reference picture forced an answer to: **a ghost is
    // not a person, so it gets no face and no level.** Everything here is that
    // one sentence read from each of the four directions a row can come from.

    private fun identity(id: Int, ftp: Int? = 215) = RaceIdentity(
        localUserId = id,
        avatar = Avatar.defaultFor(id),
        level = RiderLevel.of(RidingTotals(rides = 40, durationSec = 40 * 1800L, outputKj = 8000.0)),
        ftpWatts = ftp
    )

    @Test
    fun `a housemate carries their face onto the row and a generated target does not`() {
        val standings = LiveLeaderboard(
            ghosts = listOf(
                ghost("Ava", perSecond = 1.0, seconds = 600, kind = GhostKind.Human)
                    .copy(identity = identity(2)),
                ghost("Class target", perSecond = 0.8, seconds = 600, kind = GhostKind.Prescribed)
            )
        ).standingsAt(300, yourValue = 200.0)!!

        val ava = standings.all.first { it.name == "Ava" }
        val target = standings.all.first { it.name == "Class target" }

        assertEquals(2, ava.identity!!.localUserId)
        assertEquals(215, ava.identity!!.ftpWatts)
        assertNull(target.identity)
    }

    /**
     * The rider's own past rides are the second half of the rule and it is the
     * same half — `Your best` is not a person, and a face repeated four times
     * down a board is decoration (20.2.6a).
     */
    @Test
    fun `the rider's own past rides get no face`() {
        val standings = LiveLeaderboard(
            ghosts = listOf(
                ghost("Your best", perSecond = 0.9, seconds = 600, kind = GhostKind.YourBest)
            ),
            you = identity(1)
        ).standingsAt(300, yourValue = 200.0)!!

        assertNull(standings.all.first { it.name == "Your best" }.identity)
        assertEquals(1, standings.all.first { it.isYou }.identity!!.localUserId)
    }

    /**
     * `RiderScore` rule 4 reaching the board. A guest ride is filed against
     * nobody, so a guest can never leave level 1 and gets no badge anywhere —
     * they still race, and their row is still a name and a number.
     */
    @Test
    fun `a guest's own row carries no identity`() {
        val standings = LiveLeaderboard(
            ghosts = listOf(ghost("Ava", perSecond = 1.0, seconds = 600, kind = GhostKind.Human))
        ).standingsAt(300, yourValue = 200.0)!!

        assertNull(standings.all.first { it.isYou }.identity)
    }

    /**
     * The measured-power gate takes real rides off the board (24.3.7a) and the
     * rider's own row is not a ride — losing their face there would be a second
     * consequence nobody argued for.
     */
    @Test
    fun `narrowing the board to generated targets keeps the rider's own face`() {
        val narrowed = LiveLeaderboard(
            ghosts = listOf(
                ghost("Ava", perSecond = 1.0, seconds = 600, kind = GhostKind.Human)
                    .copy(identity = identity(2)),
                ghost("Class target", perSecond = 0.8, seconds = 600, kind = GhostKind.Prescribed)
            ),
            you = identity(1)
        ).generatedOnly()

        assertEquals(1, narrowed.you!!.localUserId)
        val standings = narrowed.standingsAt(300, yourValue = 200.0)!!
        // Ava went with the gate, and with her the only other face.
        assertTrue(standings.all.none { it.name == "Ava" })
        assertEquals(1, standings.all.count { it.identity != null })
    }
}
