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
    val outputKj: Double
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
         * two metres by somebody at 90 rpm. *"Your best of the last thirty
         * days"* is accurate and unreadable. Beside a row that says **YOU**,
         * *30 days* is unambiguous.
         */
        val label: String
    ) {
        /** Your best ride of this class, ever. */
        YourBestEver("Your best"),

        /** Your best of the last twelve months. */
        YourBestYear("12 months"),

        /** Your best of the last thirty days. */
        YourBestMonth("30 days"),

        /** A housemate's best, from `householdRivals` (24.3.1's query). */
        Housemate(""),

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
