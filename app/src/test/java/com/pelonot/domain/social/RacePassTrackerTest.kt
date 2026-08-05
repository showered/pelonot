package com.pelonot.domain.social

import com.pelonot.domain.model.LiveStanding
import com.pelonot.domain.model.LiveStandings
import com.pelonot.domain.model.RaceMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * PLAN 24.3.18d — the moment, and the reason it needs a latch.
 *
 * The defect this class exists to make impossible is not a wrong answer, it is
 * a *repeated* one: `standingsAt` runs four times a second, so a celebration
 * derived from "am I above that row now" fires 240 times a minute. Most of
 * these tests are therefore about the second and third calls rather than the
 * first.
 */
class RacePassTrackerTest {

    @Test
    fun `passing your own best fires once and never again`() {
        val tracker = RacePassTracker()

        assertNull("the first sighting arms, it does not report", tracker.onStandings(board(you = 0.0, best = 100.0)))
        assertNull(tracker.onStandings(board(you = 50.0, best = 100.0)))

        val passed = tracker.onStandings(board(you = 101.0, best = 100.0))
        assertEquals("Your best", passed?.name)

        // The rest of the ride is spent ahead of it, and that is not news.
        repeat(20) {
            assertNull(tracker.onStandings(board(you = 120.0, best = 100.0)))
        }
    }

    /**
     * The rider passes, fades, and claws back. One achievement, one moment —
     * congratulating them twice is worse than not at all.
     */
    @Test
    fun `falling back behind does not re-arm the pass`() {
        val tracker = RacePassTracker()
        tracker.onStandings(board(you = 0.0, best = 100.0))

        assertEquals("Your best", tracker.onStandings(board(you = 101.0, best = 100.0))?.name)
        assertNull(tracker.onStandings(board(you = 90.0, best = 100.0)))
        assertNull("re-passing is not a second achievement", tracker.onStandings(board(you = 150.0, best = 100.0)))
    }

    /**
     * **The one that caught the design out.** The ghosts are cumulative traces,
     * not final totals, so every race opens with the whole field reading 0.0 —
     * and the rider's first kilojoule would put them "past their best" two
     * seconds into the class. A ride that has not started cannot be overtaken.
     *
     * And it must stay *armed*, not latched: the real pass comes later, once
     * the ghost's own trace is moving.
     */
    @Test
    fun `a ride that has not started yet cannot be passed`() {
        val tracker = RacePassTracker()
        assertNull(tracker.onStandings(board(you = 0.0, best = 0.0)))
        assertNull(tracker.onStandings(board(you = 1.0, best = 0.0)))
        assertNull(tracker.onStandings(board(you = 8.0, best = 0.0)))

        // The ghost's trace starts moving, the rider is behind, then goes past.
        assertNull(tracker.onStandings(board(you = 8.0, best = 20.0)))
        assertEquals("Your best", tracker.onStandings(board(you = 25.0, best = 20.0))?.name)
    }

    /**
     * A ride resumed after a crash comes back with the rider already ahead of a
     * ghost they passed before the app died (8.3d). That is not news either —
     * the first sighting latches it silently.
     */
    @Test
    fun `a row already behind at the first sighting is latched silently`() {
        val tracker = RacePassTracker()
        assertNull(tracker.onStandings(board(you = 400.0, best = 100.0)))
        assertNull(tracker.onStandings(board(you = 410.0, best = 100.0)))
    }

    /** Level is matching the ride, not beating it, and the board draws it level. */
    @Test
    fun `drawing level is not passing`() {
        val tracker = RacePassTracker()
        tracker.onStandings(board(you = 0.0, best = 100.0))
        assertNull(tracker.onStandings(board(you = 100.0, best = 100.0)))
        assertEquals("Your best", tracker.onStandings(board(you = 100.1, best = 100.0))?.name)
    }

    /**
     * Going past Ava is a good moment and it is not *this* moment; going past
     * *the plan* or *your usual* is a target this app invented, and marking it
     * as a personal best breaks the honesty rule from the inside. Both are why
     * `GhostKind` carries `isPerson` and `isGenerated` separately.
     */
    @Test
    fun `only the rider's own past rides count`() {
        val tracker = RacePassTracker()
        val ahead = LiveStandings(
            metric = RaceMetric.Output,
            window = emptyList(),
            all = listOf(
                you(0.0),
                row("Ava", 100.0, GhostKind.Human),
                row("The plan", 110.0, GhostKind.Prescribed),
                row("Your usual", 120.0, GhostKind.Usual),
                row("300", 130.0, GhostKind.Milestone)
            ),
            yourRank = 5,
            fieldSize = 5
        )
        tracker.onStandings(ahead)

        val past = LiveStandings(
            metric = RaceMetric.Output,
            window = emptyList(),
            all = listOf(
                you(500.0),
                row("Ava", 100.0, GhostKind.Human),
                row("The plan", 110.0, GhostKind.Prescribed),
                row("Your usual", 120.0, GhostKind.Usual),
                row("300", 130.0, GhostKind.Milestone)
            ),
            yourRank = 1,
            fieldSize = 5
        )
        assertNull("no row here is a ride the rider recorded", tracker.onStandings(past))
    }

    /**
     * Two of the rider's own rides a kilojoule apart, both cleared on one tick.
     * One moment per moment — two celebrations stacked on a single frame is a
     * flicker, not an occasion — and it is the *better* ride that is reported.
     */
    @Test
    fun `two rows cleared in one tick report the best and latch the rest`() {
        val tracker = RacePassTracker()
        val start = LiveStandings(
            metric = RaceMetric.Output,
            window = emptyList(),
            all = listOf(
                you(0.0),
                row("Your recent best", 100.0, GhostKind.YourRecentBest),
                row("Your best", 101.0, GhostKind.YourBest)
            ),
            yourRank = 3,
            fieldSize = 3
        )
        tracker.onStandings(start)

        val leapt = LiveStandings(
            metric = RaceMetric.Output,
            window = emptyList(),
            all = listOf(
                you(200.0),
                row("Your recent best", 100.0, GhostKind.YourRecentBest),
                row("Your best", 101.0, GhostKind.YourBest)
            ),
            yourRank = 1,
            fieldSize = 3
        )
        assertEquals("Your best", tracker.onStandings(leapt)?.name)

        // And the one it swallowed is not held over to the next tick.
        assertNull(tracker.onStandings(leapt))
    }

    @Test
    fun `reset arms the tracker again for a new ride`() {
        val tracker = RacePassTracker()
        tracker.onStandings(board(you = 0.0, best = 100.0))
        assertEquals("Your best", tracker.onStandings(board(you = 200.0, best = 100.0))?.name)

        tracker.reset()
        assertNull(tracker.onStandings(board(you = 0.0, best = 100.0)))
        assertEquals("Your best", tracker.onStandings(board(you = 200.0, best = 100.0))?.name)
    }

    @Test
    fun `no board is no event`() {
        assertNull(RacePassTracker().onStandings(null))
    }

    // ---------------------------------------------------------------- helpers

    private fun board(you: Double, best: Double) = LiveStandings(
        metric = RaceMetric.Output,
        window = emptyList(),
        all = listOf(you(you), row("Your best", best, GhostKind.YourBest)),
        yourRank = if (you > best) 1 else 2,
        fieldSize = 2
    )

    private fun you(value: Double) = LiveStanding(
        name = LiveStanding.YOU,
        rank = 1,
        value = value,
        isYou = true,
        finished = false,
        gapToYou = 0.0
    )

    private fun row(name: String, value: Double, kind: GhostKind) = LiveStanding(
        name = name,
        rank = 2,
        value = value,
        isYou = false,
        finished = false,
        gapToYou = value,
        kind = kind
    )
}
