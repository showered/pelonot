package com.pelonot.domain.social

import kotlin.math.max

/** A real rider's best finished class ride at the current class's authored length. */
data class DurationFinishTarget(
    val localUserId: Int?,
    val accountId: String?,
    val name: String,
    val bestKj: Double,
    val isYou: Boolean
)

/** A final score to chase, distinct from a past ride's score at this second. */
data class FinishChase(
    val target: DurationFinishTarget,
    val remainingKj: Double,
    val remainingSec: Int,
    val durationSec: Int
) {
    val passed: Boolean get() = remainingKj <= 0.0
}

object DurationFinishTargets {
    /** Local identity wins, but the higher verified final total survives a cross-bike copy. */
    fun merge(
        local: List<DurationFinishTarget>,
        cloud: List<DurationFinishTarget>
    ): List<DurationFinishTarget> {
        val byAccount = cloud.filter { it.accountId != null }.associateBy { it.accountId }
        val localAccounts = local.mapNotNull { it.accountId }.toSet()
        return local.map { rider ->
            val other = byAccount[rider.accountId]
            if (other == null) rider else rider.copy(bestKj = max(rider.bestKj, other.bestKj))
        } + cloud.filterNot { it.accountId in localAccounts }
    }

    /** Keep your lifetime best and the nearest other rider's final target visible. */
    fun chases(
        targets: List<DurationFinishTarget>,
        currentKj: Double,
        elapsedSec: Int,
        durationSec: Int
    ): List<FinishChase> {
        if (durationSec <= 0 || elapsedSec >= durationSec) return emptyList()
        val usable = targets.filter { it.bestKj > 0.0 }
        val own = usable.filter { it.isYou }.maxByOrNull { it.bestKj }
        val other = usable.filterNot { it.isYou }
            .minWithOrNull(compareBy<DurationFinishTarget> {
                if (it.bestKj > currentKj) it.bestKj - currentKj else Double.POSITIVE_INFINITY
            }.thenByDescending { it.bestKj })
        return listOfNotNull(own, other).map { target ->
            FinishChase(target, target.bestKj - currentKj,
                (durationSec - elapsedSec).coerceAtLeast(0), durationSec)
        }
    }
}
