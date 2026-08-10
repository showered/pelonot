package com.pelonot.domain.model

import com.pelonot.core.Formatters
import com.pelonot.domain.social.GhostKind
import com.pelonot.domain.social.GhostRider

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
    val metric: RaceMetric = RaceMetric.Output,
    /**
     * The milestone ladder, or null (24.3.18b, candidate 1).
     *
     * Not one of [ghosts] because it is the only row on the board that has to
     * **move when the rider does**: the owner's *"no matter how high you go
     * there should always be a target ahead of you"* cannot be satisfied by a
     * fixed total, since a rider who beats it is back to an empty sky.
     */
    val pacer: Pacer? = null
) {

    /** One competitor, reduced to a name and a cumulative series. */
    data class Ghost(
        val name: String,
        val trace: RivalTrace,
        /** What this row is (24.3.18a). [GhostKind.Human] for a real ride. */
        val kind: GhostKind = GhostKind.Human
    )

    /**
     * A round number that stays ahead of the rider (24.3.18b).
     *
     * It is a *pace* rather than a trace: the rung is chosen at each second
     * from the greater of [floor] and the rider's own projected finish, so the
     * ladder rises as they do. The row's own name changes with the rung, which
     * is the point — passing 300 and finding 350 in front of you is the
     * feature, not a glitch.
     *
     * @property floor the best total already on the board, so the first rung
     *   is above the field rather than above nothing.
     */
    data class Pacer(
        val durationSec: Int,
        val floor: Double,
        val stepKj: Double = GhostRider.MILESTONE_STEP_KJ
    ) {
        /**
         * The target total at [second], given what the rider has done so far.
         *
         * Projection is held off until [GhostRider.LADDER_SETTLES_AFTER_SEC]:
         * dividing by a handful of elapsed seconds turns a warm-up surge into
         * a finishing total nobody has ridden, and the target would leap and
         * then sag for the rest of the class.
         */
        fun targetAt(second: Int, yourValue: Double): Double {
            val projected = if (second >= GhostRider.LADDER_SETTLES_AFTER_SEC && second > 0) {
                yourValue * durationSec / second
            } else {
                0.0
            }
            return GhostRider.nextMilestone(maxOf(floor, projected, yourValue), stepKj)
        }
    }

    /**
     * **A board with nobody on it is not drawn** — 24.1.6's rule, and the
     * reason it is a rule: an empty comparison is a message about the people
     * who are not on it. One ghost is enough, because one ghost plus you is a
     * comparison; zero is a rider being shown their own number with the word
     * *leaderboard* over it.
     */
    /**
     * **A ghost may carry a board on its own here, and only here** (24.3.18a).
     *
     * 24.1.6's rule — a board of one row is a rider's own number with the word
     * *leaderboard* over it — is about the *static* board, where ghosts never
     * appear at all. On the live board the opposite is true and it is the
     * whole reason this feature exists: with 72 classes and a four-person
     * household, the ordinary case is a class nobody has ridden, and *the
     * plan* (24.3.18b) is a real target on the very first attempt at one.
     *
     * What stays true is that a generated row is never mistaken for a person:
     * that is [Ghost.kind]'s job, not this one's.
     */
    val isEmpty: Boolean get() = ghosts.isEmpty() && pacer == null

    /**
     * The board with every **real ride** taken off it, leaving only what this
     * app generated (PLAN 24.3.7a).
     *
     * What the measured-power gate is actually for is a *comparison between a
     * modelled number and a measured one* — a rider on `PowerModel`'s guess
     * finishing above a housemate who really rode it, or below one they would
     * have beaten. That argument is about **rows that are somebody's ride**,
     * and 24.3.7 applied it to the whole board, which took *the plan* and the
     * milestone ladder down with it for no reason anybody could state: those
     * are numbers this app computed from the rider's own FTP, so there is no
     * measurement on the other side of the comparison to misrepresent.
     *
     * The owner's rule, in the thirty-sixth sitting: *"There should ALWAYS be
     * a leaderboard even if it's only CPU ghosts you're up against."*
     *
     * The rider's **own** past rides go too, and that is the same rule rather
     * than an exception to it: `Your best` is a real ride of theirs, recorded
     * with measured watts, and racing it on modelled ones is exactly the
     * false comparison 24.3.7 exists to prevent — the fact that both rides are
     * the same person's makes it more misleading rather than less.
     *
     * [Pacer.floor] is recomputed from what survives, because the floor's job
     * is to put the first rung *above the field*: keeping a floor set by a
     * housemate's real ride would leave a rider chasing a rung nothing on
     * their board can reach.
     *
     * Nothing here touches what is written down. `power_is_measured` still
     * records what actually happened, sample by sample, so the ride is still
     * excluded afterwards from every static board, every FTP proposal and
     * every calibration fit.
     */
    fun generatedOnly(): LiveLeaderboard {
        val kept = ghosts.filter { it.kind.isGenerated }
        return copy(
            ghosts = kept,
            pacer = pacer?.copy(floor = kept.maxOfOrNull { it.trace.finalValue } ?: 0.0)
        )
    }

    /**
     * The board as it stands at [second], with [yourValue] as the rider's own
     * cumulative total in [metric]'s units.
     *
     * @return null when there is nobody to race, which is by far the ordinary
     *   case and draws nothing at all.
     */
    fun standingsAt(second: Int, yourValue: Double): LiveStandings? {
        if (isEmpty) return null

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
                        finished = at == null,
                        kind = ghost.kind
                    )
                )
            }
            // The ladder is placed last so that when it ties with a ghost the
            // stable sort leaves it below — a round number and a real target
            // level with each other should read as the real one being ahead.
            pacer?.let { ladder ->
                val target = ladder.targetAt(second, yourValue)
                add(
                    Placing(
                        // The rung itself is the name — a bare number, which
                        // is the board's own convention since 24.3.17b took
                        // the units off every row.
                        name = Formatters.kilojoulesValue(target),
                        // Paced to finish on the rung, so it is beatable all
                        // the way rather than an unreachable line from second
                        // one: the rider is chasing a *pace*, which is what
                        // "on for 300" means to somebody riding.
                        value = if (ladder.durationSec > 0) {
                            target * second.coerceAtMost(ladder.durationSec) / ladder.durationSec
                        } else {
                            target
                        },
                        isYou = false,
                        finished = false,
                        kind = GhostKind.Milestone
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
                gapToYou = placing.value - yourValue,
                kind = placing.kind
            )
        }

        val yourIndex = standings.indexOfFirst { it.isYou }
        return LiveStandings(
            metric = metric,
            window = windowAround(standings, yourIndex),
            all = standings,
            yourRank = standings[yourIndex].rank,
            fieldSize = standings.size
        )
    }

    private data class Placing(
        val name: String,
        val value: Double,
        val isYou: Boolean,
        val finished: Boolean,
        val kind: GhostKind = GhostKind.Human
    )

    companion object {
        /**
         * How many rows the ride screen shows (24.3.13).
         *
         * The owner: *"I'm expecting it to show the person above you, the
         * person below you."* It is what makes 24.3.4's *"not a list"* and the
         * word *leaderboard* stop contradicting each other — the board can
         * have any number of rows on it and the rider sees the ones that
         * concern them.
         *
         * **Three until the thirty-second sitting, and six now** (24.3.18c).
         * The owner: *"There is space, as far as I remember, on the screen for
         * more than 3 items … it's exciting to overtake and be overtaken so
         * the more the merrier within reason."* Measured on the tablet AVD
         * rather than estimated: rows are 66 px apart, the card starts at
         * y ≈ 200, and *View in Overlay Mode* caps it at y ≈ 672 — six rows
         * fit with nothing else moved, and a seventh collides with the button.
         * The rest of the field is reachable by scrolling; the six are what a
         * rider gets without taking a hand off the bars.
         */
        const val WINDOW = 6

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
    /**
     * The whole field, best first — what scrolling reaches (24.3.18c).
     *
     * The owner asked for the rest to be scrollable rather than hidden. The
     * distinction [window] keeps is still the important one: those are the
     * rows a rider gets **without taking a hand off the bars**, and everything
     * here beyond them costs a deliberate reach.
     */
    val all: List<LiveStanding> = window,
    val yourRank: Int,
    /** Everybody on the board, the rider included. */
    val fieldSize: Int
) {
    /** True when nobody is ahead — the state 24.3.13 says to design first. */
    val leading: Boolean get() = yourRank == 1

    /**
     * The one row a strip can afford (24.3.16).
     *
     * **The competitor immediately above the rider, or — when the rider is
     * leading — the one immediately below.** Both are the same question asked
     * from the two ends of it: *who is the race with right now*. It is the
     * board's window narrowed to one, so it slides for exactly the reason the
     * window does, and a rider who is leading gets the person chasing them
     * rather than an empty chip.
     *
     * Null when there is nobody to race, which is by far the ordinary case and
     * draws nothing at all — 24.1.6's rule, and it costs more on the overlay
     * than anywhere else in the app.
     */
    val nearest: LiveStanding?
        get() {
            val you = window.indexOfFirst { it.isYou }
            if (you < 0) return null
            return window.getOrNull(you - 1) ?: window.getOrNull(you + 1)
        }
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
    val gapToYou: Double,
    /**
     * What this row is (24.3.18a) — a person, or a target the app generated.
     *
     * Carried rather than worked out from [name], so nothing on the drawing
     * side has to pattern-match a string to decide whether to mark a row as
     * invented. A rider must never come away thinking a housemate did 300 kJ
     * when 300 was a number this app made up.
     */
    val kind: GhostKind = GhostKind.Human
) {
    /** Whether this row was generated rather than ridden. */
    val isGhost: Boolean get() = kind.isGhost

    companion object {
        /** The rider's own row. Not a name, so it can never collide with one. */
        const val YOU = "You"
    }
}
