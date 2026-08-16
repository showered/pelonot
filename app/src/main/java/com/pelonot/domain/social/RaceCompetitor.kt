package com.pelonot.domain.social

/**
 * One finished ride that is on the live leaderboard (PLAN 24.3.12).
 *
 * Not a *choice* — this is the difference between the leaderboard and the
 * single rival it supersedes. A rival was picked before the ride from a card
 * of chips; the board is simply everybody who qualifies, assembled at ride
 * start with nothing asked of the rider. The owner's note is what settles
 * that: *"If you start a class that someone else (or yourself) has already
 * created then there should be a live leaderboard"* — the condition is that
 * the ride exists, not that anybody selected it.
 *
 * [kind] is carried rather than folded into [name] because the two are used
 * differently: the name is what a rider reads on a row, and the kind is what
 * decides which of two rows survives when the same ride qualifies twice —
 * see `WorkoutRepository.raceBoardFor`.
 */
data class RaceCompetitor(
    val workoutId: String,
    val name: String,
    val kind: Kind,
    /** The ride's own final total, for ordering the field before it is loaded. */
    val outputKj: Double,
    /**
     * Who this row is, when it is a person (24.3.19a).
     *
     * Null on every row whose [kind] is not a housemate, and that is the rule
     * rather than missing data — see [RaceIdentity]. It is carried beside
     * [kind] rather than derived from it because the two answer different
     * questions: the kind says *what class of row is this*, the identity says
     * *whose face goes on it*, and a housemate whose profile has been deleted
     * is a person with no identity to draw.
     */
    val identity: RaceIdentity? = null
) {
    /**
     * Which of 24.3.12's four kinds of row this is.
     *
     * The order of the constants is the order of preference when one ride
     * qualifies as several — see [Kind.widerThan]. `Friend` is declared and
     * unreachable: it needs Phase 18 and the friend's samples in the cloud,
     * and rule 3 of the connectivity model says the household board is a Room
     * query that never touches the network. It degrades to *absent*, which is
     * the only honest way for a network-shaped row to fail on an offline bike.
     */
    enum class Kind(
        /**
         * What a rider reads on the row, for the kinds that are a *window*
         * onto one person rather than a person. A housemate's row is their
         * name, so the label is theirs and this is empty.
         *
         * Short on purpose, and the reason is the screen rather than taste:
         * these sit in a 360 dp column beside a rank and a number, read from
         * two metres by somebody at 90 rpm.
         *
         * **24.3.12a is settled here, and 24.3.18e is why it had to be.** The
         * owner had said `12 months` was *"no good at all"* and left the
         * naming as an action on themselves; what forced it was ghosts
         * arriving, because a second family of invented name on a board that
         * already read `12 MONTHS / 30 DAYS / Ava` would have been two
         * problems instead of one. The fault was nameable: **every other row
         * on the board is a person and those two were durations.** The brief
         * from 24.3.18e — *"self-explanatory but also personal and not too
         * geeky"* — gives one voice for every non-human row, written from the
         * rider's own point of view. `Your best this year` is four characters
         * longer than `12 months` and is a sentence about a person.
         */
        val label: String
    ) {
        /** Your best ride of this class, ever. */
        YourBestEver("Your best"),

        /** Your best of the last twelve months — `12 months` until 24.3.12a. */
        YourBestYear("Your best this year"),

        /** Your best of the last thirty days — `30 days` until 24.3.12a. */
        YourBestMonth("Your recent best"),

        /** A housemate's best, from `householdRivals` (24.3.1's query). */
        Housemate(""),

        /**
         * A housemate's **most recent** ride, from `householdLatestRides`
         * (24.3.18b).
         *
         * The owner: *"Can we also add more things like 'Tom's last ride' …
         * it's exciting to see activity."* A best is a monument and can be two
         * years old; a last ride is news. Declared **below** [Housemate] so
         * that when a housemate's latest ride is also their best the board
         * keeps the prouder of the two labels rather than the more recent one.
         *
         * The label is empty for the same reason [Housemate]'s is — it is
         * built from the rider's name, in the repository.
         */
        HousemateLatest(""),

        /** Phase 18. Nothing produces one yet. */
        Friend("");

        /**
         * Whether this row's window is the wider of the two, and therefore
         * the one to keep.
         *
         * The case is ordinary rather than exotic: a rider whose best ever
         * ride of a class was three weeks ago has **one** ride qualifying as
         * all three of their own rows. Drawn naively that is a board with the
         * same ride on it three times, level with itself — which 22.5's
         * finding predicts precisely ("a month with one ride in it puts your
         * only ride on the board as a rival to itself"), and which has to
         * read as absent rather than as a rival you are dead level with.
         *
         * The wider label wins because it says more: a ride that is your best
         * ever is not usefully described as your best of the last thirty days.
         */
        fun widerThan(other: Kind): Boolean = ordinal < other.ordinal

        /**
         * How this row is classified on the live board (24.3.18a).
         *
         * Every one of these is a **ride that happened**, so the honest answer
         * for all of them is [GhostKind.Human] — a row is only a *ghost* when
         * this app made the number up. The rider's own three windows carry
         * their own kinds so the board can style them as themselves rather
         * than as opponents (24.3.18d), which is a different question from
         * whether anybody rode them.
         */
        val ghostKind: GhostKind
            get() = when (this) {
                YourBestEver -> GhostKind.YourBest
                YourBestYear -> GhostKind.YourBestThisYear
                YourBestMonth -> GhostKind.YourRecentBest
                Housemate, HousemateLatest, Friend -> GhostKind.Human
            }
    }

    companion object {
        /**
         * One ride, one row, at the widest label it qualifies for (24.3.12).
         *
         * Order is preserved and this only ever removes, so a caller's ranking
         * is untouched.
         */
        fun oneRowPerRide(field: List<RaceCompetitor>): List<RaceCompetitor> {
            val widest = mutableMapOf<String, Kind>()
            field.forEach { competitor ->
                val held = widest[competitor.workoutId]
                if (held == null || competitor.kind.widerThan(held)) {
                    widest[competitor.workoutId] = competitor.kind
                }
            }
            return field.filter { widest[it.workoutId] == it.kind }
        }
    }
}
