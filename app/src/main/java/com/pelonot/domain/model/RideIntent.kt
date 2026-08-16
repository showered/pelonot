package com.pelonot.domain.model

/**
 * The rider's stated goal for a session, which scales every prescribed power
 * target: `P_target = FTP × zone% × k`.
 *
 * Previously this was passed around as a bare display string and compared with
 * `when (intentModifier) { "Reach New Milestones" -> ... }`, so a typo silently
 * degraded to a 1.0 multiplier.
 *
 * **[description] does not name the multiplier, and that is deliberate**
 * (Phase 26, the owner's call of 5 August). It read "Push targets 5% higher
 * than your zones prescribe" — three pieces of machinery in one line, on the
 * one dialog standing between a rider and starting a class. *"Too geeky, the
 * user doesn't need to see behind the curtain on this one."* The `k` is still
 * exactly what it was; it is simply not the thing the rider is being asked
 * about, which is whether they feel like working hard today.
 *
 * **[displayName] is sentence case, and the words changed with it** (20.4.10).
 * *Reach New Milestones* and *Just Stay Fit* were the last Title Case on the
 * first-run path, on a dialog whose every neighbour is sentence case — *I ride
 * now and then*, *A good workout*, *Keep as a guest ride*. 26.1.5 rewrote both
 * **descriptions** here at the owner's instruction and left the names alone,
 * which is how marketing capitals survived an audit of this very screen.
 *
 * **The case changed and the words did not**, which is the whole scope. The
 * tempting extra step is to rewrite them — *Push a bit harder*, *Keep it
 * steady* — and it is wrong twice over: it is a change to a dialog the owner
 * has already had opinions about, made without asking, and each new name would
 * then say what the [description] under it already says. The redundancy that
 * *is* worth a look is the owner's to judge, not a session's.
 *
 * Nothing stored moves: [id] is what persists and `intent_modifier` is the
 * number, so this is capital letters on a screen and nothing else.
 */
enum class RideIntent(
    /** Stable identifier — safe to persist and to put in an Intent extra. */
    val id: String,
    val displayName: String,
    val description: String,
    val multiplier: Double
) {
    ReachNewMilestones(
        id = "reach_new_milestones",
        displayName = "Reach new milestones",
        description = "A bit harder than your zones ask for",
        multiplier = 1.05
    ),
    JustStayFit(
        id = "just_stay_fit",
        displayName = "Just stay fit",
        description = "A bit easier, for an effort you can hold",
        multiplier = 0.95
    );

    companion object {
        val DEFAULT = JustStayFit

        /** Resolves an [id], falling back to [DEFAULT] for unknown values. */
        fun fromId(id: String?): RideIntent =
            entries.firstOrNull { it.id == id } ?: DEFAULT

        /**
         * Resolves the intent a *stored* ride was ridden at (8.3d).
         *
         * `workouts.intent_modifier` persists the multiplier and not the id, so
         * a ride being resumed has to come back through the number. Compared on
         * a tolerance rather than by equality because it is a `Double` that has
         * been through SQLite.
         *
         * **This works only while the multipliers are distinct**, which they
         * are — 1.05 and 0.95. An intent added with a multiplier another one
         * already uses would make this ambiguous, and the fix then is to
         * persist the id beside the modifier rather than to widen the
         * tolerance. Unknown values fall back to [DEFAULT], matching [fromId].
         */
        fun fromMultiplier(multiplier: Double): RideIntent =
            entries.firstOrNull { kotlin.math.abs(it.multiplier - multiplier) < TOLERANCE }
                ?: DEFAULT

        private const val TOLERANCE = 0.001
    }
}

/**
 * Target power band for [zone] adjusted by the rider's [intent].
 */
fun PowerZone.targetPowerRange(
    ftp: Double,
    intent: RideIntent
): ClosedFloatingPointRange<Double> {
    val base = powerRange(ftp)
    return (base.start * intent.multiplier)..(base.endInclusive * intent.multiplier)
}
