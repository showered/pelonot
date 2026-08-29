package com.pelonot.domain.alerts

/**
 * The families of thing worth telling a rider about (PLAN 27.2).
 *
 * **Every one of these is an absolute the rider actually produced** — watts,
 * kilojoules, seconds, weeks — and that is 27.1.3 rather than a coincidence of
 * what was easy. Anything expressed *relative* to a moving number is a
 * different claim the day that number moves: "your best-ever Zone 5 time" is
 * redrawn by an FTP change the rider accepted last night, so it is the alert
 * this phase deliberately does not build.
 *
 * The order of the constants is the order they are ranked in when a ride
 * produces more than one (27.1.5) — see [AlertKind.rank].
 */
enum class AlertKind {
    /**
     * A run of consecutive weeks with a ride in them, at one of
     * [AlertRules.STREAK_MILESTONES].
     *
     * **Weeks, never days** (22.5): on the owner's own stated assumption of one
     * ride a week, a perfect year of Sundays is a day-streak of 1. Counting in
     * days would make the feature built to reward consistency blind to the most
     * consistent rider this app can have.
     */
    WeeklyStreak,

    /** The rider's best total output on a class they had ridden before. */
    ClassOutput,

    /** The rider's best mean power over one of `MeanMaximalPower.WINDOWS`. */
    PowerWindow,

    /** The most output the rider has ever put into a single ride. */
    RideOutput,

    /** The longest ride the rider has ever done. */
    RideDuration;

    /**
     * Where this family sits when a ride produces several (27.1.5).
     *
     * Smaller is louder. **The order is a judgement and it is written down
     * rather than left to the enum's accident**, because "the largest" in
     * 27.1.5 has no arithmetic meaning across four different units — 8 weeks
     * and 412 kJ cannot be compared, so they are ranked by what they claim:
     *
     * 1. A **streak** is the rarest by construction, because it can only fire
     *    at a milestone, and it is the only one about the rider's life rather
     *    than about one ride.
     * 2. A **class** record is the only genuinely comparable one — same
     *    intervals, same prescription, same length — which is 27.2.1's own
     *    argument and the reason 24.1 ranks per class.
     * 3. A **power window** is comparable across every ride but is the number a
     *    rider is least likely to have been chasing.
     * 4. **Most output** mostly measures how long the class was (16.3.3's
     *    complaint), so it ranks under a window of the same history.
     * 5. **Longest ride** is last because riding for longer is a choice rather
     *    than a performance.
     */
    val rank: Int get() = ordinal
}

/**
 * One thing that happened, ready to be written down or read back (27.1.1).
 *
 * [previousValue] is null for a [AlertKind.WeeklyStreak], which is a threshold
 * crossing rather than something beaten, and non-null for every record — the
 * old number is what makes the new one mean anything on 27.4's list.
 */
data class RiderAlert(
    val kind: AlertKind,
    /**
     * What makes this alert unique within its family: a class id, a window
     * length in seconds, a milestone in weeks, or the empty string for the
     * families that have exactly one subject.
     *
     * It is half of the ledger's uniqueness key — with the ride, it is what
     * stops the same alert being written twice by a second finalise (27.1.1).
     */
    val subjectKey: String,
    /** The absolute the rider produced: watts, kilojoules, seconds or weeks. */
    val value: Double,
    /** What it beat, or null for a threshold crossing. */
    val previousValue: Double? = null,
    /**
     * The class's title at the moment it fired, for [AlertKind.ClassOutput].
     *
     * Carried on the row rather than joined back to `class_templates`, because
     * a class the bundle has since retired (23.2.6) still has to be nameable in
     * a rider's own list of things they have done.
     */
    val subjectTitle: String? = null
) {
    /**
     * How this family ranks against another (27.1.5), most worth saying first.
     *
     * Within [AlertKind.PowerWindow] the **longer** window wins: beating your
     * best twenty minutes is a bigger claim than beating your best five
     * seconds, and a ride that does both should say the first.
     */
    val ordering: Pair<Int, Int> get() = kind.rank to when (kind) {
        AlertKind.PowerWindow -> -(subjectKey.toIntOrNull() ?: 0)
        else -> 0
    }
}
