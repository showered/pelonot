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
data class ClassLeaderboard(
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

    /** Whether anybody on this board rode a different bike (18.7). */
    val crossesBikes: Boolean get() = entries.any { it.source == Source.Cloud }

    /**
     * Whether any row will actually carry the other-bike mark.
     *
     * Not the same as [crossesBikes], and the difference is the rider
     * themselves: their own row can come from the cloud — their local rides
     * being simulated, so unranked (24.4.2) — and it is never marked, because
     * "who is this stranger?" is not a question anybody asks about themselves.
     * A caption explaining a glyph that appears nowhere is worse than no
     * caption.
     */
    val marksAnyRider: Boolean
        get() = entries.any { it.source == Source.Cloud && !it.isYou }

    /**
     * Where a rider's number came from, which decides what may be said about
     * the comparison (PLAN 18.7, 24.4.1).
     *
     * [Household] is the fairest comparison this app will ever make: same
     * board, same knob, same calibration, usually the same week. [Cloud] is a
     * different bike, and honest only because both sides are **measured** watts
     * off their own boards — which is enforced in the query rather than hoped
     * for. The distinction is kept per entry rather than per board so a caption
     * can name the rows it applies to instead of disclaiming all of them.
     */
    enum class Source { Household, Cloud }

    /**
     * @property outputPerKg the number a lighter rider will want, offered
     *   beside the ranking rather than as it (24.1.3). Null when the profile
     *   carries no usable weight, which is better than dividing by a default.
     */
    data class Entry(
        /** Null for a rider who is not on this tablet. */
        val localUserId: Int?,
        /** Their cloud account, or null for a housemate with no account. */
        val accountId: String?,
        val name: String,
        val outputKj: Double,
        val weightKg: Double,
        /** 1-based, and shared by riders on identical output. */
        val rank: Int,
        val isYou: Boolean,
        val source: Source = Source.Household
    ) {
        val outputPerKg: Double? get() = if (weightKg > 0) outputKj / weightKg else null
    }

    /** What a query knows before anyone has been placed. */
    data class Standing(
        val localUserId: Int?,
        val accountId: String?,
        val name: String,
        val outputKj: Double,
        val weightKg: Double,
        val source: Source = Source.Household
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
            youId: Int?,
            /** Your cloud account, so a cloud row can be recognised as yours. */
            yourAccountId: String? = null
        ): ClassLeaderboard {
            var lastOutput = Double.NaN
            var lastRank = 0
            val entries = merge(standings)
                .sortedByDescending { it.outputKj }
                .mapIndexed { index, standing ->
                    val rank = if (standing.outputKj == lastOutput) lastRank else index + 1
                    lastOutput = standing.outputKj
                    lastRank = rank
                    Entry(
                        localUserId = standing.localUserId,
                        accountId = standing.accountId,
                        name = standing.name,
                        outputKj = standing.outputKj,
                        weightKg = standing.weightKg,
                        rank = rank,
                        isYou = (standing.localUserId != null && standing.localUserId == youId) ||
                            (standing.accountId != null && standing.accountId == yourAccountId),
                        source = standing.source
                    )
                }
            return ClassLeaderboard(classId = classId, entries = entries)
        }

        /**
         * **A rider who is both a housemate and signed in appears once, as a
         * housemate** (PLAN 18.9).
         *
         * The two boards genuinely overlap: your own profile is in the Room
         * query *and* in the cloud one, and so is any housemate with an
         * account. Rendered naively, the rider on the bike sees themselves
         * twice — once with today's ride and once with whatever the cloud last
         * accepted, which are usually different numbers, so it does not even
         * look like a duplicate. It looks like being beaten by yourself.
         *
         * Household wins for the reason 18.9 gives: it is the same bike and it
         * needs no network, so it is the more current and more trustworthy of
         * the two. The cloud copy of a housemate is dropped entirely rather
         * than merged, because taking the higher of the two would silently
         * rank a rider on a ride they did somewhere else.
         */
        private fun merge(standings: List<Standing>): List<Standing> {
            val householdAccounts = standings
                .filter { it.source == Source.Household }
                .mapNotNull { it.accountId }
                .toSet()

            return standings.filterNot { standing ->
                standing.source == Source.Cloud && standing.accountId in householdAccounts
            }
        }
    }
}
