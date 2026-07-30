package com.pelonot.domain.model

/**
 * A coaching line shown prominently when a class reaches a moment worth
 * calling out.
 *
 * Typed rather than a bare string so the UI can style each case — the final
 * effort is shouted, a cooldown is calm — and so the copy lives in one place
 * instead of being reassembled at three call sites.
 */
enum class RideCue(val message: String) {
    None(""),

    /** The last interval hard enough to be worth emptying the tank on. */
    FinalPush("Give it everything"),

    /** A class that ends on a recovery segment. */
    CoolDown("Cool down — ride easy")
}

/**
 * Where the rider is in a class at one instant.
 *
 * Produced by [ClassIntervalEngine] and consumed by both the ride screen and
 * the floating HUD, so the two can never disagree about which interval is
 * running.
 *
 * @property next The upcoming interval, or null on the final one. Deliberately
 *   null there: a "next up" preview on the last interval is a promise the class
 *   cannot keep.
 */
data class IntervalState(
    val current: Interval? = null,
    val next: Interval? = null,
    /** Index of [current] within the class, or -1 before the first interval. */
    val index: Int = -1,
    val intervalCount: Int = 0,
    val elapsedInIntervalSec: Int = 0,
    val remainingInIntervalSec: Int = 0,
    val classElapsedSec: Int = 0,
    val classDurationSec: Int = 0,
    val cue: RideCue = RideCue.None,
    /** True once the class timer has run past its final interval. */
    val isComplete: Boolean = false
) {

    /** False for a free ride, which has no prescribed structure at all. */
    val hasClass: Boolean get() = intervalCount > 0

    val classRemainingSec: Int
        get() = (classDurationSec - classElapsedSec).coerceAtLeast(0)

    /** 0f–1f through the whole class. */
    val classProgress: Float
        get() = if (classDurationSec <= 0) 0f
        else (classElapsedSec.toFloat() / classDurationSec).coerceIn(0f, 1f)

    /** 0f–1f through the current interval. */
    val intervalProgress: Float
        get() {
            val duration = current?.durationSec ?: return 0f
            if (duration <= 0) return 0f
            return (elapsedInIntervalSec.toFloat() / duration).coerceIn(0f, 1f)
        }

    /** True while the change-of-effort warning should be showing. */
    val isChangeImminent: Boolean
        get() = next != null && remainingInIntervalSec in 1..WARNING_SEC

    /** True when the rider is being asked to spin easy — drives the big clock. */
    val isRecovering: Boolean get() = current?.isRecovery == true

    val targetZone: PowerZone get() = current?.powerZone ?: PowerZone.Z3

    companion object {
        /** Seconds of notice before an interval changes. */
        const val WARNING_SEC = 5

        /** A ride with no class attached. */
        val NONE = IntervalState()
    }
}
