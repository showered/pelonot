package com.pelonot.data.service

import com.pelonot.data.sensor.PowerModel
import com.pelonot.domain.model.GovernedBy
import com.pelonot.domain.model.Interval
import com.pelonot.domain.model.IntervalState
import com.pelonot.domain.model.LiveStandings
import com.pelonot.domain.model.RideIntent
import com.pelonot.domain.model.RivalStatus
import com.pelonot.domain.model.TargetBand
import com.pelonot.domain.model.TargetEmphasis
import com.pelonot.domain.model.cadenceBand
import com.pelonot.domain.model.powerBand

/**
 * Everything about a ride that is *not* a live sensor value, published as one
 * object by [WorkoutService].
 *
 * The floating HUD and the in-app ride screen both render from this, so the two
 * can never disagree about which interval is running or whether the ride is
 * paused. Telemetry stays on its own high-rate flow
 * ([com.pelonot.data.sensor.SensorRepository.sensorReading]) rather than being
 * folded in here — a snapshot that changed on every sensor packet would
 * recompose the entire HUD several times a second for a number that has not
 * moved.
 */
data class RideSnapshot(
    val state: WorkoutState = WorkoutState.Idle,
    val elapsedSeconds: Int = 0,
    val interval: IntervalState = IntervalState.NONE,
    /**
     * The whole class, for the timeline strip. [IntervalState] deliberately
     * carries only the current and next segments; drawing "how much of this is
     * left, and does it get harder?" needs all of them. Set once at ride start,
     * so the identity check `StateFlow` performs on every update stays cheap.
     */
    val intervals: List<Interval> = emptyList(),
    val classTitle: String? = null,
    val ftpWatts: Int = WorkoutSession.DEFAULT_FTP,
    val intent: RideIntent = RideIntent.DEFAULT,
    val totalOutputKj: Double = 0.0,
    val distanceKm: Double = 0.0,
    /**
     * Whether the bike is still reporting (2.4.5).
     *
     * It rides on the snapshot rather than on the telemetry flow because it is
     * a fact about the *absence* of telemetry: a flow that has stopped emitting
     * cannot announce that it has stopped, and the service's tick is the only
     * thing in the app that notices time passing without a reading. Both the
     * ride screen and the strip render from here, so they cannot disagree about
     * whether the numbers beside this are live.
     */
    val telemetryLive: Boolean = true,
    /**
     * Whether this pause is one the app called rather than the rider (19.1.2).
     *
     * Both surfaces say so: a ride that stops on its own and does not explain
     * itself is indistinguishable from one that has frozen.
     */
    val autoPaused: Boolean = false,
    /**
     * The live ghost, or null when this ride is not racing anybody (24.3.4).
     *
     * Null is by far the ordinary case: a rival has to be chosen before the
     * ride, both sides have to be measured power, and most classes have no
     * measured ride behind them at all. **Nothing renders it on the overlay**
     * — 24.1.5 and 18.6 both say nothing social goes on the strip, and the
     * ghost is a full ride screen feature for the reason 19.4 gives: the
     * overlay has half a second of attention and it belongs to the next sixty
     * seconds of pedalling.
     */
    val rival: RivalStatus? = null,
    /**
     * The live leaderboard, or null when there is nobody to race (24.3.10).
     *
     * Supersedes [rival], and is null for the same three reasons that one is
     * — nobody has ridden this class, or the rides that exist are not measured
     * power, or this ride's own watts turned out modelled. **Never both**: the
     * board and the single gap are two presentations of one race and drawing
     * them together would put two answers to the same question on one screen.
     *
     * **Nothing renders it on the overlay.** 24.1.5 and 18.6 both say nothing
     * social goes on the strip, and a leaderboard is a stronger case for that
     * rule than the single gap was: the overlay has half a second of attention
     * and it belongs to the next sixty seconds of pedalling.
     */
    val standings: LiveStandings? = null
) {
    val isPaused: Boolean get() = state == WorkoutState.Paused
    val isRunning: Boolean get() = state == WorkoutState.Active || state == WorkoutState.Paused

    /** The cadence the current interval asks for, if any. */
    val cadenceTarget: TargetBand
        get() = interval.current?.cadenceBand ?: TargetBand.NONE

    /** The power band the current interval asks for, scaled by [intent]. */
    val powerTarget: TargetBand
        get() = interval.current?.powerBand(ftpWatts.toDouble(), intent) ?: TargetBand.NONE

    /**
     * Which metric the current block is actually asking for (PLAN 11.7.2).
     *
     * Power outside a class, which is not a claim about a free ride so much as
     * the harmless default: with no class running there is no band on any tile
     * for this to weight.
     */
    val governedBy: GovernedBy
        get() = interval.current?.governedBy ?: GovernedBy.Power

    /** How hard the cadence tile asserts its band. */
    val cadenceEmphasis: TargetEmphasis get() = emphasisFor(GovernedBy.Cadence)

    /** How hard the power tile asserts its band. */
    val powerEmphasis: TargetEmphasis get() = emphasisFor(GovernedBy.Power)

    private fun emphasisFor(axis: GovernedBy): TargetEmphasis = when {
        !interval.hasClass -> TargetEmphasis.None
        governedBy == axis -> TargetEmphasis.Instruction
        else -> TargetEmphasis.Context
    }

    /**
     * The resistance that would produce [powerTarget] at the middle of
     * [cadenceTarget].
     *
     * **Nothing renders this today** (PLAN 11.7.3). It was the third of the
     * three targets the rider was being asked to hit at once, and it is the
     * one with no class behind it: no class in the library prescribes
     * resistance, so this band is `PowerModel` inverted — and that curve
     * scores RMSE 137 W and 66% median absolute error against 310 samples off
     * the real board. Of the three numbers competing for a rider's attention
     * mid-effort it was the derived guess, presented with equal authority.
     *
     * Kept rather than deleted because 11.7.3 says exactly when it comes back:
     * a bike riding its own auto-calibrated curve (2.2a) has a resistance band
     * worth reading, and it is a suggestion the wording should admit to.
     *
     * Empty when the target is unreachable at that cadence, because then the
     * answer is "change your legs", which is a different instruction and must
     * not be disguised as a clamped percentage.
     */
    val resistanceTarget: TargetBand
        get() {
            val cadence = cadenceTarget
            val power = powerTarget
            if (!cadence.isDefined || !power.isDefined) return TargetBand.NONE

            val midCadence = (cadence.min + cadence.max) / 2.0
            // Zone 1's power floor is 0 W, which has no resistance solution and
            // does not need one: the bottom of the band is simply the bottom of
            // the knob. Dropping the whole band over that would leave the
            // easiest intervals — the ones a rider is most likely to overcook —
            // with no guidance at all.
            val low = if (power.min <= 0.0) 0.0 else PowerModel.resistanceForWatts(power.min, midCadence)
            val high = PowerModel.resistanceForWatts(power.max, midCadence)
            if (low == null || high == null) return TargetBand.NONE

            return TargetBand(low, high)
        }

    companion object {
        val IDLE = RideSnapshot()
    }
}
