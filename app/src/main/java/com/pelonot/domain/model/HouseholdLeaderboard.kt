package com.pelonot.domain.model

/**
 * Everyone on this tablet who has ridden one class, ranked (PLAN 24.1).
 *
 * The social tier that needs no cloud: rule 3 of the connectivity model says
 * everyone with a profile on this tablet is a household, and a household
 * leaderboard is a Room query. No account, no network, and — deliberately —
 * **no caveat**. A comparison between two riders on the same bike is the
 * fairest this app will ever make: same board, same knob, same calibration,
 * usually the same week. 18.7's honesty disclaimer is for the cross-bike case
 * and printing it here would be noise (24.4.1).
 */
data class HouseholdLeaderboard(
    val classId: String,
    val entries: List<Entry> = emptyList()
) {

    /**
     * **A household of one sees nothing** (24.1.6).
     *
     * A leaderboard with a single row on it is not a comparison, it is the
     * rider's own number with a rosette drawn on it — and the dashboard
     * already tells that story properly (22.1). Empty is also what a household
     * that has only ever ridden simulated rides sees, since none of those may
     * be ranked (24.4.2).
     */
    val isWorthShowing: Boolean get() = entries.size >= 2

    /**
     * @property outputPerKg the number a lighter rider will want, offered
     *   beside the ranking rather than as it (24.1.3). Null when the profile
     *   carries no usable weight, which is better than dividing by a default.
     */
    data class Entry(
        val localUserId: Int,
        val name: String,
        val outputKj: Double,
        val weightKg: Double,
        /** 1-based, and shared by riders on identical output. */
        val rank: Int,
        val isYou: Boolean
    ) {
        val outputPerKg: Double? get() = if (weightKg > 0) outputKj / weightKg else null
    }

    /** What the database knows before anyone has been placed. */
    data class Standing(
        val localUserId: Int,
        val name: String,
        val outputKj: Double,
        val weightKg: Double
    )

    companion object {
        /**
         * Places riders, best first.
         *
         * Sorted here rather than trusted from the query, so the rule lives
         * with the type. Ties share a rank: two riders who did identical work
         * are not separated by whichever row the database happened to return
         * first.
         */
        fun of(
            classId: String,
            standings: List<Standing>,
            youId: Int?
        ): HouseholdLeaderboard {
            var lastOutput = Double.NaN
            var lastRank = 0
            val entries = standings
                .sortedByDescending { it.outputKj }
                .mapIndexed { index, standing ->
                    val rank = if (standing.outputKj == lastOutput) lastRank else index + 1
                    lastOutput = standing.outputKj
                    lastRank = rank
                    Entry(
                        localUserId = standing.localUserId,
                        name = standing.name,
                        outputKj = standing.outputKj,
                        weightKg = standing.weightKg,
                        rank = rank,
                        isYou = standing.localUserId == youId
                    )
                }
            return HouseholdLeaderboard(classId = classId, entries = entries)
        }
    }
}
