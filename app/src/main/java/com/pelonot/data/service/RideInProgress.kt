package com.pelonot.data.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Enough of a running ride to find your way back to it (11.1a.5).
 *
 * These are the four values `WorkoutService.startIntent` was given, kept so the
 * ride screen can be re-entered with the same class, the same intent and the
 * same FTP it was started with. Everything else the screen needs — elapsed
 * time, intervals, totals — comes from the service's own snapshot once it
 * binds.
 */
data class ActiveRide(
    val workoutId: String,
    val classId: String?,
    val intentId: String,
    val ftpWatts: Int
)

/**
 * Whether this process is recording a ride right now, and which one.
 *
 * **Deliberately process-scoped state rather than a database column, because
 * the question is a process-scoped one.** "Is a ride being recorded?" is only
 * ever asked by code running in the same process as [WorkoutService], and the
 * answer that matters is about *this* process: if it died, no ride is being
 * recorded, whatever `workouts` still says. That is exactly the situation the
 * crash-recovery prompt exists for, so a value that dies with the process is
 * not a limitation here — it is the correct semantics, and it cannot go stale
 * the way a persisted flag would after a kill.
 *
 * Two things read it:
 *
 *  - **8.3b** — `WorkoutRepository` excludes the live ride from crash recovery.
 *    A ride in progress is `is_complete = 0` by design (1.12), which made it
 *    indistinguishable from a ride the app died in the middle of; the app would
 *    offer to discard the ride the rider was still on, and discarding it
 *    deleted the row out from under the service.
 *  - **11.1a.5** — the navigation graph routes a cold start back into the ride
 *    instead of stranding the rider on the profile picker.
 */
object RideInProgress {

    private val _active = MutableStateFlow<ActiveRide?>(null)

    /** The ride being recorded, or null. */
    val active: StateFlow<ActiveRide?> = _active.asStateFlow()

    /** The live ride's id, for callers that only need to exclude it. */
    val workoutId: String? get() = _active.value?.workoutId

    fun begin(ride: ActiveRide) {
        _active.value = ride
    }

    /**
     * Called when the ride is finalised — and when the service is destroyed
     * without finalising one, because a ride whose service has gone really has
     * become a crash artifact and *should* be offered for recovery.
     */
    fun end() {
        _active.value = null
    }
}
