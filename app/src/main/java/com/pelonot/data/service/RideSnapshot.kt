package com.pelonot.data.service

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
    val distanceKm: Double = 0.0
) {
    val isPaused: Boolean get() = state == WorkoutState.Paused
    val isRunning: Boolean get() = state == WorkoutState.Active || state == WorkoutState.Paused

    /** The cadence the current interval asks for, if any. */
    val cadenceTarget: TargetBand
        get() = interval.current?.cadenceBand ?: TargetBand.NONE

    /** The power band the current interval asks for, scaled by [intent]. */
    val powerTarget: TargetBand
        get() = interval.current?.powerBand(ftpWatts.toDouble(), intent) ?: TargetBand.NONE

    companion object {
        val IDLE = RideSnapshot()
    }
}
