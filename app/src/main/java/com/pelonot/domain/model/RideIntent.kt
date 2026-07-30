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
