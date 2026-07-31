package com.pelonot.data.service

import com.pelonot.data.sensor.PowerModel
import com.pelonot.domain.model.Interval
import com.pelonot.domain.model.IntervalState
import com.pelonot.domain.model.RideIntent
import com.pelonot.domain.model.TargetBand
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
    val telemetryLive: Boolean = true
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
     * The resistance that would produce [powerTarget] at the middle of
     * [cadenceTarget] — the actual instruction, rather than two numbers the
     * rider has to combine in their head at 90 rpm.
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
