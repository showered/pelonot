package com.pelonot.data.service

import com.pelonot.domain.model.RideIntent

/**
 * An in-flight or just-finished workout.
 *
 * Fully immutable. The previous version declared `var elapsedSeconds` on a
 * data class, incremented it in place and then published `session.copy()` to
 * force a `StateFlow` emission. Because the mutation had already been applied
 * to the instance the flow was holding, `copy()` produced an equal object and
 * `StateFlow`'s equality-based conflation dropped the update — the HUD timer
 * updated only when some other field happened to differ.
 *
 * @property elapsedSeconds Ride time excluding paused periods.
 */
data class WorkoutSession(
    val workoutId: String,
    val userId: Int?,
    val classId: String?,
    val startedAtEpochMs: Long,
    val elapsedSeconds: Int = 0,
    val intent: RideIntent = RideIntent.DEFAULT,
    val ftpWatts: Int = DEFAULT_FTP,
    val totalOutputKj: Double = 0.0,
    val distanceKm: Double = 0.0,
    val avgPower: Double = 0.0,
    val avgCadence: Double = 0.0,
    val avgHeartRate: Int? = null,
    val sampleCount: Int = 0
) {
    /** True for a guest ride, which the user is asked to save or discard. */
    val isGuestRide: Boolean get() = userId == null

    companion object {
        const val DEFAULT_FTP = 150
    }
}

/** Lifecycle states for a workout. */
enum class WorkoutState {
    Idle,
    Active,
    Paused,

    /** Finished and persisted; the summary screen can read final figures. */
    Completed
}
