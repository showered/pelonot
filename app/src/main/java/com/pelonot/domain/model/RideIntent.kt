package com.pelonot.domain.model

/**
 * The rider's stated goal for a session, which scales every prescribed power
 * target: `P_target = FTP × zone% × k`.
 *
 * Previously this was passed around as a bare display string and compared with
 * `when (intentModifier) { "Reach New Milestones" -> ... }`, so a typo silently
 * degraded to a 1.0 multiplier.
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
        displayName = "Reach New Milestones",
        description = "Push targets 5% higher than your zones prescribe",
        multiplier = 1.05
    ),
    JustStayFit(
        id = "just_stay_fit",
        displayName = "Just Stay Fit",
        description = "Ease targets 5% below your zones for a sustainable effort",
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
