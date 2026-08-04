package com.pelonot.domain.model

/**
 * Everyone this ride is racing, ranked at the second it is asked about (PLAN
 * 24.3.10–24.3.13).
 *
 * The owner's shape, and it is Peloton's: *"let's do what Peloton does and
 * show a live leaderboard … so if you're on 56 watts and your PB is 65 (at
 * that point in time) you know you need to speed up if you want a new PB."*
 * The score is the class total in kilojoules (24.3.14), so the comparison is
 * cumulative rather than instantaneous — 11.6.7 already settled that the ride
 * screen's numbers change too fast to read, and a board that re-sorted several
 * times a second would reopen it.
 *
 * **This supersedes the single rival, and is built out of it.** Every decision
 * under 24.3.3–24.3.9 survives unchanged: the elapsed-second alignment, the
 * refusal to extrapolate a finished ride forward, the measured-power gate. A
 * rival was always a leaderboard with a `LIMIT 1` on it — what changes here is
 * the limit and the presentation.
 *
 * Built once at ride start and then read by elapsed second. Nothing in here
 * touches the database, and nothing in here is a suspend function: the tick
 * that publishes a snapshot four times a second reads arrays.
 */
data class LiveLeaderboard(
    val ghosts: List<Ghost>,
    val metric: RaceMetric = RaceMetric.Output
) {

    /** One competitor, reduced to a name and a cumulative series. */
    data class Ghost(val name: String, val trace: RivalTrace)

    /**
     * **A board with nobody on it is not drawn** — 24.1.6's rule, and the
     * reason it is a rule: an empty comparison is a message about the people
     * who are not on it. One ghost is enough, because one ghost plus you is a
     * comparison; zero is a rider being shown their own number with the word
     * *leaderboard* over it.
     */
    val isEmpty: Boolean get() = ghosts.isEmpty()

    /**
     * The board as it stands at [second], with [yourValue] as the rider's own
     * cumulative total in [metric]'s units.
     *
     * @return null when there is nobody to race, which is by far the ordinary
     *   case and draws nothing at all.
     */
    fun standingsAt(second: Int, yourValue: Double): LiveStandings? {
        if (ghosts.isEmpty()) return null

        val field = buildList {
            add(Placing(LiveStanding.YOU, yourValue, isYou = true, finished = false))
            ghosts.forEach { ghost ->
                // 24.3.6, and it is the rule this feature keeps having to
                // restate: a rival's ride ends when it ends. Past their last
                // second they hold their final total and say so — never a
                // line extrapolated forward, never a comparison that silently
                // freezes. Same family as `isStaleAt`.
                val at = ghost.trace.valueAt(second)
                add(
                    Placing(
                        name = ghost.name,
                        value = at ?: ghost.trace.finalValue,
                        isYou = false,
                        finished = at == null
                    )
                )
            }
        }

        // Stable, so ghosts hold the order they were loaded in when they are
        // level — and `thenByDescending { isYou }` puts the rider above a
        // ghost they are exactly level with rather than below one. Every ride
        // starts with the whole field on zero, so that tie is not a corner
        // case, it is the first ten seconds of every race.
        val ranked = field.sortedWith(
            compareByDescending<Placing> { it.value }.thenByDescending { it.isYou }
        )

        var lastValue = Double.NaN
        var lastRank = 0
        val standings = ranked.mapIndexed { index, placing ->
            val rank = if (placing.value == lastValue) lastRank else index + 1
            lastValue = placing.value
            lastRank = rank
            LiveStanding(
                name = placing.name,
                rank = rank,
                value = placing.value,
                isYou = placing.isYou,
                finished = placing.finished,
                gapToYou = placing.value - yourValue
            )
        }

        val yourIndex = standings.indexOfFirst { it.isYou }
        return LiveStandings(
            metric = metric,
            window = windowAround(standings, yourIndex),
            yourRank = standings[yourIndex].rank,
            fieldSize = standings.size
        )
    }

    private data class Placing(
        val name: String,
        val value: Double,
        val isYou: Boolean,
        val finished: Boolean
    )

    companion object {
        /**
         * How many rows the ride screen shows (24.3.13).
         *
         * The owner: *"I'm expecting it to show the person above you, the
         * person below you."* Three is what makes 24.3.4's *"not a list"* and
         * the word *leaderboard* stop contradicting each other — the board can
         * have any number of rows on it and the rider sees the three that
         * concern them.
         */
        const val WINDOW = 3

        /**
         * Three consecutive rows containing the rider, centred on them where
         * the field allows it.
         *
         * **The sliding is the point, and it is not a detail.** At the top
         * there is no row above, and that is the state worth designing first
         * because it is the one a rider wants to be in; at the bottom there is
         * no row below, and that is the first ten seconds of every race, since
         * the whole field starts level and a ghost that took an early lead is
         * ahead of a rider who has not turned a pedal. Rendering a short
         * window in either case would make the card change height twice a ride
         * — and 11.6.8 is this project's own finding that a ride screen which
         * resizes under a rider is unreadable at 90 rpm. So the window slides
         * instead of shrinking, and only a field smaller than [WINDOW] gives
         * fewer rows.
         */
        private fun windowAround(
            standings: List<LiveStanding>,
            yourIndex: Int
        ): List<LiveStanding> {
            if (standings.size <= WINDOW) return standings
            val start = (yourIndex - 1).coerceIn(0, standings.size - WINDOW)
            return standings.subList(start, start + WINDOW)
        }
    }
}

/**
 * What the ride screen draws: a window onto the board, and where the rider is
 * on the whole of it (PLAN 24.3.13).
 *
 * [yourRank] and [fieldSize] are carried beside [window] rather than derived
 * from it because they are the part the window hides. A rider looking at three
 * rows cannot tell whether there are two more below or twenty.
 */
data class LiveStandings(
    val metric: RaceMetric,
    /** Up to [LiveLeaderboard.WINDOW] rows, best first, always including you. */
    val window: List<LiveStanding>,
    val yourRank: Int,
    /** Everybody on the board, the rider included. */
    val fieldSize: Int
) {
    /** True when nobody is ahead — the state 24.3.13 says to design first. */
    val leading: Boolean get() = yourRank == 1
}

/** One row (PLAN 24.3.12). */
data class LiveStanding(
    val name: String,
    /** 1-based, and shared by competitors on identical totals. */
    val rank: Int,
    /** Their cumulative total at this second, in the board's own metric. */
    val value: Double,
    val isYou: Boolean,
    /** Their ride has run out and this total is final (24.3.6). */
    val finished: Boolean,
    /**
     * **Their total minus yours** — positive means they are ahead of you.
     *
     * The opposite sign convention to [RivalStatus.gap], and deliberately so:
     * there the subject of the sentence was the rider, so *"+18"* meant *you
     * are 18 up*. Here the subject of every row is the competitor named on it,
     * so *"+12"* means *they are 12 up on you*. What keeps that unambiguous is
     * the ranking rather than the wording — the row above yours always carries
     * a `+` and the row below always carries a `−`, so the sign agrees with
     * the position it is drawn in.
     */
    val gapToYou: Double
) {
    companion object {
        /** The rider's own row. Not a name, so it can never collide with one. */
        const val YOU = "You"
    }
}
