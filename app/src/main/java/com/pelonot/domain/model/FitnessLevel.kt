package com.pelonot.domain.model

/**
 * How a rider describes their own riding (PLAN 20.3.2, Route B).
 *
 * The one question the app asks that it cannot get from anywhere else. Date of
 * birth and weight are already collected for other reasons — 21.1.1's
 * heart-rate zones and `profiles.weight_kg` — so this is the *whole* additional
 * cost of estimating an FTP rather than defaulting one, which is what 20.3.9
 * changed about the arithmetic.
 *
 * **Three answers, not ten** (26.3). The temptation is a six-rung ladder from
 * *never* to *racer*, and it is wrong twice over: a rider cannot tell rung
 * three from rung four about themselves, and the estimate is not precise enough
 * for the difference to survive. Three answers a person can pick without
 * thinking, feeding a number the app says out loud is a guess, beats six that
 * imply an accuracy nothing here has.
 *
 * **No units and no watts in the labels**, which is CLAUDE.md's standing rule:
 * this screen is a rider being asked a question about themselves, not a rider
 * reading a measurement. The watts appear on the next step, once, with a
 * caption saying where they came from (20.3.4).
 *
 * @property wattsPerKg Functional threshold power per kilogram at the age this
 *   is calibrated for, before [FtpEstimator]'s age adjustment. **Deliberately
 *   below the published mid-range for each description** — see
 *   [FtpEstimator] for why the bias has a direction.
 */
enum class FitnessLevel(
    /** Stable identifier — safe to persist. Same reasoning as [RideIntent]. */
    val id: String,
    val displayName: String,
    val description: String,
    /**
     * The same answer as [displayName], phrased to sit inside a sentence the
     * app is saying back — *"…and that you're **riding now and then**."*
     *
     * A separate string rather than a transformation of [displayName], because
     * the transformation was tried and it is what produced the first version's
     * copy defect: `displayName.lowercase()` turned *"I ride now and then"*
     * into *"i ride now and then"*, and the screen quoted it at the rider with
     * a lower-case "I". Text a person wrote does not survive being case-folded
     * by a machine; two strings is the cheap answer and the one that cannot
     * regress.
     */
    val estimateBasis: String,
    val wattsPerKg: Double
) {
    NewToThis(
        id = "new_to_this",
        displayName = "I'm new to this",
        description = "You haven't ridden much, or you're coming back after a break",
        estimateBasis = "new to this",
        wattsPerKg = 1.6
    ),
    Occasional(
        id = "occasional",
        displayName = "I ride now and then",
        description = "A few times a month, and you finish comfortably",
        estimateBasis = "riding now and then",
        wattsPerKg = 2.1
    ),
    Regular(
        id = "regular",
        displayName = "I ride regularly",
        description = "Most weeks, and you're used to working hard",
        estimateBasis = "riding regularly",
        wattsPerKg = 2.7
    );

    companion object {
        /**
         * The answer assumed when the rider did not give one.
         *
         * The middle rung rather than the lowest, because this is what a
         * profile created before the question existed gets, and those riders
         * have been riding on `UserEntity.DEFAULT_FTP` — 150 W, which is
         * [Occasional] at about 70 kg. Choosing [NewToThis] here would move
         * every existing rider's zones on upgrade, which is a migration
         * deciding something about a rider it was never told.
         */
        val DEFAULT = Occasional

        /** Resolves a stored [id]; unknown and absent both mean *not given*. */
        fun fromId(id: String?): FitnessLevel? =
            entries.firstOrNull { it.id == id }
    }
}
