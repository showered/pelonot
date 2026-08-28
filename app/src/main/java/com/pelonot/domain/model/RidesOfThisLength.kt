package com.pelonot.domain.model

/**
 * The rider's **own** rides of one length, best first (PLAN 24.5).
 *
 * The owner's note: *"I've done 3 rides at 30 mins but I can't see my previous
 * 30-min time on the leaderboard. This should be the case, even if the rides
 * were on different classes."*
 *
 * **Three rules on [ClassLeaderboard] each defeat that on their own**, which is
 * why this is a different object rather than a mode on that one. That board
 * draws nothing for a household of one, keeps one row per *rider* rather than
 * per ride, and is keyed on a class id. Every one of those is a rule about
 * comparing **people**, and they are all correct for what they were written
 * for. This compares **occasions**, and the rider is the constant.
 *
 * Four things follow, and the third is the one that keeps this honest.
 *
 * 1. **A household of one is the case this exists for.** The bike with the most
 *    riding on it is the bike whose leaderboard never draws, and a rider alone
 *    is the ordinary state of a bike in a spare room. 24.1.6 stays exactly as
 *    it is on the board it was written for.
 * 2. **One row per ride**, which is the thing 24.1.1 rules out and was right to
 *    rule out: a list of somebody's six attempts is a personal history, and a
 *    personal history is precisely what is being asked for here.
 * 3. **Every row carries the class it was**, because without it this is
 *    misleading. Two thirty-minute classes are not the same effort — a recovery
 *    ride and a Sprints class are both thirty minutes — so a bare column of
 *    output would quietly report the recovery ride as a bad ride. 16.3.3 chose
 *    mean-maximal power over exactly this framing, and 27.2.1 says a class is
 *    the only thing that makes two rides genuinely comparable. Both are right,
 *    and the answer is not to hide the note's ask but to show the second fact
 *    beside the first — the same move 24.1.3 makes when kJ and kJ/kg disagree.
 * 4. **Measured watts only** (24.4.2). A simulated ride's power is
 *    `PowerModel`'s output at RMSE 137 W against the real board, and ranking
 *    one against a real ride is as wrong for one rider across occasions as it
 *    is between two riders. Enforced in the query, like every other board.
 *
 * **This is not 16.3.3 and must not grow into it** (24.5.5). Mean-maximal power
 * is a claim about the rider and is comparable across every ride they have ever
 * done; this is comparable across the rides that happened to be this long. They
 * answer different questions on different screens.
 *
 * @property classDurationSec the **class's** authored length, not how long the
 *   rider pedalled. A class stopped early still belongs to the length it
 *   prescribed, and a free ride has no length to be measured against at all.
 */
data class RidesOfThisLength(
    val classDurationSec: Int,
    val entries: List<Entry> = emptyList()
) {

    /**
     * **One ride is not a comparison** — the same rule as 24.1.6, reached from
     * the other direction.
     *
     * A rider who has done this length once gets their own number with a
     * rosette drawn on it, which the summary screen already shows them
     * properly. It is also what a rider sees the *first* time they meet this
     * card, so it has to be silent rather than apologetic.
     */
    val isWorthShowing: Boolean get() = entries.size >= 2

    /** Whether more than one class is represented — see [Entry.classTitle]. */
    val crossesClasses: Boolean get() = entries.map { it.classId }.distinct().size > 1

    /**
     * The rows actually drawn: **your best few, and this ride wherever it came**.
     *
     * The window is 24.1.8's problem again and deliberately not its answer.
     * There, the rows that matter are the podium and the people either side of
     * you, because a position means nothing without who it is between. Here
     * every row is you, so there is no neighbourhood to preserve — what a rider
     * wants is the bar they are trying to clear and where today landed against
     * it. So: the best [MAX_ROWS], plus the highlighted ride if the window has
     * dropped it, carrying its real rank.
     *
     * Whatever is not drawn is **counted**, for 24.1.8's reason: a board that
     * stops at six without saying so is a false claim about how much riding
     * there has been.
     */
    val visible: Visible
        get() {
            if (entries.size <= MAX_ROWS) return Visible(entries, hidden = 0, breakAfter = null)

            val top = entries.take(MAX_ROWS)
            val highlighted = entries.indexOfFirst { it.isThisRide }
            if (highlighted < 0 || highlighted < MAX_ROWS) {
                return Visible(top, hidden = entries.size - top.size, breakAfter = null)
            }
            return Visible(
                rows = top + entries[highlighted],
                hidden = entries.size - top.size - 1,
                // The gap is drawn rather than implied: two adjacent rows
                // reading 6 and 19 with nothing between them look like a
                // ranking fault instead of a window.
                breakAfter = top.lastIndex
            )
        }

    /**
     * @property rows in rank order, best first.
     * @property hidden how many of [entries] are not drawn at all.
     * @property breakAfter index within [rows] after which the board skips
     *   ranks, or null when what is drawn is contiguous.
     */
    data class Visible(
        val rows: List<Entry>,
        val hidden: Int,
        val breakAfter: Int?
    )

    /**
     * @property classTitle what the ride was, which is rule 3 and never
     *   optional. Every row on this board came off a class, because a free ride
     *   has no prescribed length to be filed under.
     * @property isThisRide the ride the screen is about, when there is one. On
     *   class detail there is none and no row is marked, which is correct: the
     *   rider is choosing rather than reviewing.
     * @property rank 1-based, and shared by rides on identical output.
     */
    data class Entry(
        val workoutId: String,
        val classId: String,
        val classTitle: String,
        val recordedAt: Long,
        val outputKj: Double,
        val rank: Int,
        val isThisRide: Boolean
    )

    companion object {

        /** Six, the same ceiling the household board settled on (24.1.8). */
        const val MAX_ROWS = 6

        /**
         * Rank and mark a rider's rides, best first.
         *
         * The ordering lives here rather than in the `ORDER BY` for the reason
         * [ClassLeaderboard.of] gives: a ranking rule that can only be exercised
         * against a database is a ranking rule nobody checks.
         */
        fun of(
            classDurationSec: Int,
            rides: List<Ride>,
            thisRideId: String? = null
        ): RidesOfThisLength {
            val sorted = rides.sortedByDescending { it.outputKj }
            var lastOutput = Double.NaN
            var lastRank = 0
            val entries = sorted.mapIndexed { index, ride ->
                // Shared rank on identical output, so two rides that scored the
                // same are not silently separated by whichever row SQL
                // happened to return first.
                val rank = if (ride.outputKj == lastOutput) lastRank else index + 1
                lastOutput = ride.outputKj
                lastRank = rank
                Entry(
                    workoutId = ride.workoutId,
                    classId = ride.classId,
                    classTitle = ride.classTitle,
                    recordedAt = ride.recordedAt,
                    outputKj = ride.outputKj,
                    rank = rank,
                    isThisRide = ride.workoutId == thisRideId
                )
            }
            return RidesOfThisLength(classDurationSec = classDurationSec, entries = entries)
        }
    }

    /** One of the rider's rides, as the database hands it over. */
    data class Ride(
        val workoutId: String,
        val classId: String,
        val classTitle: String,
        val recordedAt: Long,
        val outputKj: Double
    )
}
