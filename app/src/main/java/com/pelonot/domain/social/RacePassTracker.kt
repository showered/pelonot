package com.pelonot.domain.social

import com.pelonot.domain.model.LiveStanding
import com.pelonot.domain.model.LiveStandings

/**
 * The moment a rider goes past one of their own past rides (PLAN 24.3.18d).
 *
 * **The owner:** *"Your own PB should have some kind of UI to make it really
 * exciting. If you're ahead of your PB then that should stand out."*
 *
 * Half of that was already built — the rider's own rows are drawn in the accent
 * dimmed, so a row that is also them never reads as an opponent. What was
 * missing is the *event*, and the event is the hard half, because
 * `LiveLeaderboard.standingsAt` runs four times a second: anything derived from
 * *am I above that row now* is true 240 times a minute and would either fire a
 * celebration continuously or, worse, flicker on and off while the rider sat a
 * kilojoule either side of it.
 *
 * So this is [com.pelonot.domain.coach.PositionCallTracker]'s shape, which
 * 24.3.18d names directly: **an event with a latch, not a state read every
 * frame.** Pass the standings in on every tick, get a pass back at most once
 * per competitor, ever.
 *
 * Three rules that are easy to get subtly wrong and so live here once:
 *
 * - **Only the rider's own past rides count.** Going past Ava is a nice moment
 *   and it is not this one; going past *the plan* or *your usual* is a
 *   generated target and marking it as a personal best would be the honesty
 *   rule (24.3.18's two flags) broken from the inside. The test is
 *   `!kind.isPerson && !kind.isGenerated`, which is exactly the set of rows
 *   that are a real ride of the rider's own — and it is why those two flags are
 *   separate rather than one.
 * - **A pass is one-way and permanent.** Falling back behind a row does not
 *   re-arm it. A rider who passes their best, fades, and claws back would
 *   otherwise be congratulated twice for one achievement, and the second time
 *   is worse than no time.
 * - **It only fires on a transition.** A row the rider was already ahead of at
 *   the first tick is latched silently — that is the whole field at second
 *   zero, where everybody is level on nothing, and firing there would open
 *   every ride with a celebration.
 *
 * Not thread-safe, and does not need to be: one ride, one caller, same as
 * `PositionCallTracker`.
 */
class RacePassTracker {

    /** Rows already passed, or already ahead of at the first sighting. */
    private val settled = mutableSetOf<String>()

    private var started = false

    fun reset() {
        settled.clear()
        started = false
    }

    /**
     * Advance to [standings] and return the row just overtaken, or null.
     *
     * Null is overwhelmingly the ordinary answer: a pass happens a handful of
     * times in a class and this is asked several times a second.
     *
     * When more than one row falls in a single tick — possible on a coarse
     * clock, or when two of the rider's own rides are a kilojoule apart — the
     * **best** of them is returned and the rest are latched silently. One
     * moment per moment: two celebrations stacked on one tick is a flicker, not
     * an occasion.
     */
    fun onStandings(standings: LiveStandings?): LiveStanding? {
        if (standings == null) return null
        val you = standings.all.firstOrNull { it.isYou } ?: return null

        val own = standings.all.filter { it.isYourOwnPastRide }

        var passed: LiveStanding? = null
        for (row in own) {
            if (row.name in settled) continue
            // **You cannot overtake a ride that has not started.** Every race
            // opens with the whole field on nothing — the ghosts are cumulative
            // traces, not final totals, so they all read 0.0 at second zero —
            // and the rider's first kilojoule would otherwise put them "past
            // their best" two seconds into the class. Left armed rather than
            // latched, so the comparison resumes for real once the ghost's own
            // trace starts moving.
            if (row.value <= 0.0) continue
            // Strictly greater: level is not past. A tie is the rider matching
            // the ride, not beating it, and the board itself draws them level.
            if (you.value <= row.value) continue
            settled += row.name
            // The first sighting **latches without reporting**, and only for
            // rows the rider is already past. Latching the whole field here
            // instead — the obvious implementation, and the one written first —
            // silently disarms every row the rider has yet to catch, so the
            // pass that matters can never fire at all.
            if (!started) continue
            if (passed == null || row.value > passed.value) passed = row
        }
        started = true
        return passed
    }
}

/**
 * True when this row is a real ride the rider themselves recorded (24.3.18d).
 *
 * The two flags on [GhostKind] are what makes this expressible, and they are
 * separate for exactly this reason: the rider's own best is **neither a person
 * nor invented**, so a single flag would have to either mark a real ride of
 * theirs as fictional or let a generated target count as one of their rides —
 * and the second is precisely what the honesty rule exists to prevent.
 */
val LiveStanding.isYourOwnPastRide: Boolean
    get() = !isYou && !kind.isPerson && !kind.isGenerated
