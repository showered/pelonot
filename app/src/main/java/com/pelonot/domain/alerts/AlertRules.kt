package com.pelonot.domain.alerts

/**
 * Everything one finished ride is judged against, gathered before any of it is
 * decided (PLAN 27.2).
 *
 * A data class rather than four repository calls inside the rule, so the whole
 * of Phase 27's judgement is a pure function that can be tested on the JVM —
 * the same split `FtpReductionRule` and `StreakCalculator` already use, and for
 * the same reason: what counts as a record is the part that is easy to get
 * subtly wrong and impossible to notice.
 *
 * **Every "previous" field excludes the ride being judged.** The ride is
 * already `is_complete = 1` by the time this runs — [AlertRules.detect] is
 * called from the finalise, which is the one place all three finalise paths go
 * through — so a query that did not exclude it would find the new ride as its
 * own record to beat and nothing would ever fire.
 */
data class AlertEvidence(
    val workoutId: String,
    val classId: String?,
    val classTitle: String?,
    val durationSec: Int,
    val totalOutputKj: Double,

    /**
     * Whether this ride's watts were measured all the way through
     * (`PowerProvenance.isTrustworthyAsMeasured`).
     *
     * **The honesty gate, and it is the ride's answer rather than its samples'**
     * (23.4.12, 27.1.2). A personal best off a simulated ride is a fiction the
     * app then congratulates the rider for, and `PowerModel` is 137 W out at
     * RMSE. `Unknown` and `Mixed` fail it exactly as they do for the FTP
     * proposal and the household board.
     *
     * The consequence is that **no power alert can ever fire on the emulator**,
     * which is a verification cost this phase pays deliberately.
     */
    val powerIsMeasured: Boolean,

    /** This ride's own mean-maximal efforts, keyed by window length. */
    val ridesBests: Map<Int, Double> = emptyMap(),

    /**
     * The best this rider had held at each window before today.
     *
     * Read from `workout_power_bests`, **never re-scanned from samples**, which
     * is the whole of 27.1.7: 23.4 condenses old rides, so a scan would find a
     * record falling because the ride holding it had been trimmed and would
     * congratulate the rider for beating it.
     */
    val previousBests: Map<Int, Double> = emptyMap(),

    /** Completed measured rides before this one, whatever class. */
    val priorMeasuredRides: Int = 0,
    /** Completed rides before this one, whatever class and whatever provenance. */
    val priorRides: Int = 0,

    /** Measured rides of *this* class before this one. */
    val priorClassRides: Int = 0,
    /** The best output over those, or null when there were none. */
    val bestClassOutputKj: Double? = null,

    /** The most output in any single measured ride before this one. */
    val bestOutputKj: Double? = null,
    /** The longest ride before this one, in seconds. */
    val longestRideSec: Int? = null,

    /** The rider's run of consecutive weeks, counting this ride. */
    val weeklyStreak: Int = 0,
    /** The same run counted without this ride, which is what makes it a crossing. */
    val previousWeeklyStreak: Int = 0
)

/**
 * What a finished ride is worth telling the rider (PLAN 27.1, 27.2).
 *
 * Pure, no Android imports, and the clock is not one of its inputs: everything
 * here is a comparison between numbers that are already on disk.
 *
 * **The floors are the design, not tuning** (27.1.4). A new rider's every ride
 * is their fastest, their longest and their best, so an unguarded version fires
 * six times on ride one and the rider learns inside a week that the alerts mean
 * nothing. Two guards, both chosen deliberately here rather than adjusted
 * later:
 *
 * - **A prior-count floor**, so a claim about a rider's whole history is only
 *   made once they have one. [MIN_PRIOR_RIDES] for the history-wide families;
 *   [MIN_PRIOR_CLASS_RIDES] for a class, which is lower because a class is
 *   already the narrower claim and because a library of 72 means a third ride
 *   of one of them is rare on its own.
 * - **A margin**, so noise cannot be a record. [RECORD_MARGIN] of two percent
 *   is about 5 W on a 250 W twenty minutes — inside a rider's day-to-day
 *   variation is not an improvement, and saying it is turns the alert into a
 *   caption.
 *
 * And [detect] returns **everything** that qualified, ranked. 27.1.5's "at most
 * one per ride" is a rule about what the rider is *told*, not about what is
 * written down: the other two belong on 27.4's list, and dropping them here
 * would mean a rider could never see them at all.
 */
object AlertRules {

    /** Prior rides needed before a claim about the rider's whole history. */
    const val MIN_PRIOR_RIDES = 5

    /** Prior rides of the same class needed before a class record. */
    const val MIN_PRIOR_CLASS_RIDES = 2

    /** How much better a record has to be before it is one. */
    const val RECORD_MARGIN = 0.02

    /**
     * The runs of weeks worth saying out loud.
     *
     * Four is the first, because three is not yet a habit and one is flattery;
     * after a year it repeats annually rather than stopping, since a rider who
     * has ridden every week for two years should hear so. Nothing between 52
     * and 104 fires, which is the point — an alert that arrives every month
     * for ever is the failure 27.1.4 describes.
     */
    val STREAK_MILESTONES = listOf(4, 8, 13, 26, 52)

    /** Whole years, after [STREAK_MILESTONES] has run out. */
    private const val WEEKS_IN_YEAR = 52

    /**
     * Everything this ride earned, most worth saying first (27.1.5).
     *
     * Empty is the ordinary answer and the feature depends on it being so.
     */
    fun detect(evidence: AlertEvidence): List<RiderAlert> =
        buildList {
            streak(evidence)?.let(::add)
            classRecord(evidence)?.let(::add)
            addAll(powerWindows(evidence))
            outputRecord(evidence)?.let(::add)
            durationRecord(evidence)?.let(::add)
        }.sortedWith(compareBy({ it.ordering.first }, { it.ordering.second }))

    /** The one the rider is told on the summary, or null (27.3.1). */
    fun headline(alerts: List<RiderAlert>): RiderAlert? = alerts.firstOrNull()

    private fun streak(evidence: AlertEvidence): RiderAlert? {
        val crossed = milestonesUpTo(evidence.weeklyStreak)
            .lastOrNull { it > evidence.previousWeeklyStreak }
            ?: return null
        return RiderAlert(
            kind = AlertKind.WeeklyStreak,
            subjectKey = crossed.toString(),
            value = crossed.toDouble()
        )
    }

    /**
     * Every milestone at or below [weeks].
     *
     * The ladder continues in whole years so that a rider who has kept it up
     * for two hears about it, and a rider who missed the moment their streak
     * passed 26 — the app was off, the ride was a guest ride — is told at the
     * next crossing rather than never.
     */
    private fun milestonesUpTo(weeks: Int): List<Int> {
        if (weeks <= 0) return emptyList()
        val named = STREAK_MILESTONES.filter { it <= weeks }
        val years = generateSequence(2) { it + 1 }
            .map { it * WEEKS_IN_YEAR }
            .takeWhile { it <= weeks }
            .toList()
        return named + years
    }

    private fun classRecord(evidence: AlertEvidence): RiderAlert? {
        if (!evidence.powerIsMeasured) return null
        val classId = evidence.classId ?: return null
        if (evidence.priorClassRides < MIN_PRIOR_CLASS_RIDES) return null
        val previous = evidence.bestClassOutputKj ?: return null
        if (!beats(evidence.totalOutputKj, previous)) return null
        return RiderAlert(
            kind = AlertKind.ClassOutput,
            subjectKey = classId,
            value = evidence.totalOutputKj,
            previousValue = previous,
            subjectTitle = evidence.classTitle
        )
    }

    private fun powerWindows(evidence: AlertEvidence): List<RiderAlert> {
        if (!evidence.powerIsMeasured) return emptyList()
        if (evidence.priorMeasuredRides < MIN_PRIOR_RIDES) return emptyList()
        return evidence.ridesBests.mapNotNull { (window, watts) ->
            // A window this rider has never held before is not a record: it is
            // the first time they rode that long, and the honest place for it
            // is the personal-bests screen rather than a congratulation for
            // beating a number that does not exist.
            val previous = evidence.previousBests[window] ?: return@mapNotNull null
            if (!beats(watts, previous)) return@mapNotNull null
            RiderAlert(
                kind = AlertKind.PowerWindow,
                subjectKey = window.toString(),
                value = watts,
                previousValue = previous
            )
        }
    }

    private fun outputRecord(evidence: AlertEvidence): RiderAlert? {
        if (!evidence.powerIsMeasured) return null
        if (evidence.priorMeasuredRides < MIN_PRIOR_RIDES) return null
        val previous = evidence.bestOutputKj ?: return null
        if (!beats(evidence.totalOutputKj, previous)) return null
        return RiderAlert(
            kind = AlertKind.RideOutput,
            subjectKey = "",
            value = evidence.totalOutputKj,
            previousValue = previous
        )
    }

    /**
     * Not gated on provenance, and that is correct rather than an oversight:
     * how long a ride lasted is a clock reading, and the clock is the same on a
     * simulated ride as on a real one.
     */
    private fun durationRecord(evidence: AlertEvidence): RiderAlert? {
        if (evidence.priorRides < MIN_PRIOR_RIDES) return null
        val previous = evidence.longestRideSec ?: return null
        if (!beats(evidence.durationSec.toDouble(), previous.toDouble())) return null
        return RiderAlert(
            kind = AlertKind.RideDuration,
            subjectKey = "",
            value = evidence.durationSec.toDouble(),
            previousValue = previous.toDouble()
        )
    }

    private fun beats(now: Double, previous: Double): Boolean =
        previous > 0.0 && now >= previous * (1.0 + RECORD_MARGIN)
}
