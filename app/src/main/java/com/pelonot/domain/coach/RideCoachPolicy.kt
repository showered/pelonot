package com.pelonot.domain.coach

import com.pelonot.domain.model.IntervalState
import com.pelonot.domain.model.PowerZone
import com.pelonot.domain.model.RideCue
import com.pelonot.domain.model.RidePosition
import com.pelonot.domain.model.TargetBand
import com.pelonot.domain.model.TargetStatus

/** One thing worth telling the rider, decided by [RideCoachPolicy]. */
sealed interface RideAlert {

    /** What to say out loud, or null for a haptic-only nudge. */
    val speech: String?

    /** How hard to buzz. The rider is on a vibrating machine already. */
    val haptic: HapticStrength

    /** A new interval has started. */
    data class IntervalChange(
        val zone: PowerZone,
        val cadenceMin: Int,
        val cadenceMax: Int,
        /**
         * Set only when the position the class asks for has *changed* (25.2.3).
         *
         * Null covers both "this interval prescribes nothing" and "it
         * prescribes what the last one did" — in neither case is there anything
         * to say, and `CLB-06` alternates climb and attack six times, so
         * announcing the state rather than the change would double the length
         * of every interval call in the class.
         */
        val positionChange: RidePosition? = null
    ) : RideAlert {
        // First in the sentence: it is the only part of this that has to be
        // acted on the instant it is heard. The zone and the cadence are
        // something to settle into.
        override val speech: String
            get() = (positionChange?.let { "${it.instruction}. " } ?: "") +
                "Zone ${zone.number}, ${zone.displayName}. " +
                "$cadenceMin to $cadenceMax R P M."
        override val haptic get() = HapticStrength.Firm
    }

    /** The countdown into the next effort. */
    data class ChangeWarning(
        val secondsRemaining: Int,
        val nextZone: PowerZone
    ) : RideAlert {
        // Only the first tick speaks. Counting "five, four, three…" out loud
        // over the rider's own video is intolerable; the buzz carries the rest.
        override val speech: String?
            get() = if (secondsRemaining == IntervalState.WARNING_SEC) {
                "Zone ${nextZone.number} in five"
            } else {
                null
            }
        override val haptic get() = HapticStrength.Light
    }

    /** A moment in the class worth calling out. */
    data class Cue(val cue: RideCue) : RideAlert {
        override val speech: String get() = cue.message
        override val haptic
            get() = if (cue == RideCue.FinalPush) HapticStrength.Firm else HapticStrength.Light
    }

    /** The rider has been away from the prescription long enough to mean it. */
    data class OffTarget(val advice: String) : RideAlert {
        override val speech: String get() = advice
        override val haptic get() = HapticStrength.Light
    }

    /** The class timer has run out. */
    data object ClassComplete : RideAlert {
        override val speech: String get() = "Class complete. Well ridden."
        override val haptic get() = HapticStrength.Firm
    }
}

enum class HapticStrength { Light, Firm }

/** One tick's worth of everything the coach needs to decide. */
data class CoachInput(
    val elapsedSec: Int,
    val isPaused: Boolean,
    val interval: IntervalState,
    val cadence: Double,
    val power: Double,
    val cadenceTarget: TargetBand,
    val powerTarget: TargetBand
)

/**
 * Decides when the ride should speak or buzz.
 *
 * Pure and stateful-but-testable: no Android imports, no TTS, no vibrator. The
 * playback side lives in `RideCoach`, which does nothing but obey this.
 *
 * The whole point is restraint. A rider watching a film with the HUD docked at
 * the edge of the screen will tolerate a handful of well-chosen cues per class
 * and nothing else, so every rule here is debounced, and being off target has
 * to *persist* before it is worth mentioning — power wanders by 30 W between
 * pedal strokes, and calling that out would make the app unusable.
 */
class RideCoachPolicy(
    private val offTargetGraceSec: Int = DEFAULT_OFF_TARGET_GRACE_SEC,
    private val offTargetRepeatSec: Int = DEFAULT_OFF_TARGET_REPEAT_SEC
) {

    private var announcedIndex = UNSET

    /**
     * Shared with the overlay's cue (25.3.2), so the voice and the arrow can
     * never disagree about what counts as a change of position.
     */
    private val positions = PositionCallTracker()
    private var cuedIndex = UNSET
    private var warnedAtSecond = UNSET
    private var offTargetSince = UNSET
    private var lastOffTargetAt = UNSET
    private var announcedComplete = false

    fun reset() {
        announcedIndex = UNSET
        positions.reset()
        cuedIndex = UNSET
        warnedAtSecond = UNSET
        offTargetSince = UNSET
        lastOffTargetAt = UNSET
        announcedComplete = false
    }

    fun onTick(input: CoachInput): List<RideAlert> {
        if (input.isPaused) {
            // Nothing to say while stopped, and the off-target clock must not
            // keep running or resuming would immediately nag.
            offTargetSince = UNSET
            return emptyList()
        }

        val alerts = mutableListOf<RideAlert>()
        val interval = input.interval

        if (interval.isComplete) {
            if (!announcedComplete) {
                announcedComplete = true
                alerts += RideAlert.ClassComplete
            }
            return alerts
        }

        val current = interval.current
        if (current != null && interval.index != announcedIndex) {
            announcedIndex = interval.index
            warnedAtSecond = UNSET
            offTargetSince = UNSET
            val positionChange = positions.onInterval(interval.index, current.position)
            alerts += RideAlert.IntervalChange(
                zone = current.powerZone,
                cadenceMin = current.cadenceMin,
                cadenceMax = current.cadenceMax,
                positionChange = positionChange
            )
        }

        if (current != null && interval.cue != RideCue.None && interval.index != cuedIndex) {
            cuedIndex = interval.index
            alerts += RideAlert.Cue(interval.cue)
        }

        val nextZone = interval.next?.powerZone
        if (interval.isChangeImminent && nextZone != null &&
            interval.remainingInIntervalSec != warnedAtSecond
        ) {
            warnedAtSecond = interval.remainingInIntervalSec
            alerts += RideAlert.ChangeWarning(interval.remainingInIntervalSec, nextZone)
        }

        offTargetAdvice(input)?.let { alerts += it }

        return alerts
    }

    /**
     * Sustained drift from the prescription, at most once every
     * [offTargetRepeatSec].
     *
     * Cadence is checked before power because it is the thing a rider can act
     * on directly: legs are immediate, whereas power is the product of cadence
     * and a resistance knob and takes a few seconds to settle.
     */
    private fun offTargetAdvice(input: CoachInput): RideAlert.OffTarget? {
        // A stationary bike is not "below target" — it is a rider taking a
        // drink, and telling them to pedal harder is noise.
        if (input.cadence <= STATIONARY_RPM) {
            offTargetSince = UNSET
            return null
        }

        val advice = adviceFor(input)
        if (advice == null) {
            offTargetSince = UNSET
            return null
        }

        if (offTargetSince == UNSET) offTargetSince = input.elapsedSec
        if (input.elapsedSec - offTargetSince < offTargetGraceSec) return null

        val sinceLast = if (lastOffTargetAt == UNSET) Int.MAX_VALUE
        else input.elapsedSec - lastOffTargetAt
        if (sinceLast < offTargetRepeatSec) return null

        lastOffTargetAt = input.elapsedSec
        offTargetSince = input.elapsedSec
        return RideAlert.OffTarget(advice)
    }

    private fun adviceFor(input: CoachInput): String? {
        when (input.cadenceTarget.statusFor(input.cadence)) {
            TargetStatus.Below -> return "Pick up the cadence"
            TargetStatus.Above -> return "Ease the cadence back"
            else -> Unit
        }
        return when (input.powerTarget.statusFor(input.power)) {
            TargetStatus.Below -> "Add resistance"
            TargetStatus.Above -> "Back off the resistance"
            else -> null
        }
    }

    private companion object {
        const val UNSET = -1

        /** Below this the rider is not pedalling at all. */
        const val STATIONARY_RPM = 1.0

        /** Drift has to persist this long before it is worth mentioning. */
        const val DEFAULT_OFF_TARGET_GRACE_SEC = 12

        /** And this long before mentioning it again. */
        const val DEFAULT_OFF_TARGET_REPEAT_SEC = 45
    }
}
