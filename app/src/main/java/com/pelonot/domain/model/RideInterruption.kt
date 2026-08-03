package com.pelonot.domain.model

/**
 * The gap between a ride stopping and the rider coming back to it (8.3d).
 *
 * 8.3a declined to offer *resume* on the grounds that restarting the clock
 * would "splice a gap of unknown length into the record". The length is not
 * unknown — it is arithmetic over three numbers the app already has — and this
 * is the arithmetic. Pure, so the judgement below can be argued with in a test
 * rather than on a bike.
 *
 * **What [unrecordedSec] actually measures is time that was not riding**, which
 * is deliberately broader than "time the app was dead". `workouts` does not
 * persist accumulated pause, so a rider who paused for five minutes and then
 * crashed is indistinguishable here from one who crashed for five minutes — and
 * that is the right answer rather than a limitation. Both are time the ride
 * produced no samples for, which is the only thing the record can honestly
 * claim, and it is the quantity [isResumable] wants: a rider who has been off
 * the bike for half an hour has lost the ride whichever way it happened.
 */
data class RideInterruption(
    /** The last second of riding that reached `workout_metrics`. */
    val lastRecordedSec: Int,
    /** Wall-clock seconds since the ride began that produced no samples. */
    val unrecordedSec: Int
) {
    /**
     * Whether picking this ride up again is still *this* ride.
     *
     * Two conditions, and the first is the easy one: a ride that recorded
     * nothing has nothing to resume, and the keep path (8.3a) already deletes
     * it rather than offering it.
     *
     * The threshold is a judgement and is the owner's to move. It sits at
     * [MAX_RESUMABLE_BREAK_SEC] because past roughly half an hour the warm-up
     * has gone and the legs are cold: continuing the class would be a *new*
     * workout wearing the old one's interval clock, which is the same
     * dishonesty 8.3a was right to worry about, arriving by a slower route. Up
     * to that point the rider really is finishing the ride they started —
     * a tablet that has to reboot is the case this exists for.
     */
    val isResumable: Boolean
        get() = lastRecordedSec > 0 && unrecordedSec <= MAX_RESUMABLE_BREAK_SEC

    companion object {
        /** Half an hour. See [isResumable] for why this number and not another. */
        const val MAX_RESUMABLE_BREAK_SEC = 30 * 60

        /**
         * @param startedAtEpochMs `workouts.timestamp`, the wall-clock start.
         * @param lastRecordedSec the highest `workout_metrics.timestamp_sec`.
         * @param nowEpochMs the current wall clock.
         *
         * Both derived values are floored at zero. The wall clock can be
         * adjusted — or, on a tablet with no network, corrected by minutes the
         * first time it reaches one — and a negative gap would otherwise make a
         * stale ride look freshly interrupted.
         */
        fun between(
            startedAtEpochMs: Long,
            lastRecordedSec: Int,
            nowEpochMs: Long
        ): RideInterruption {
            val wallElapsedSec = ((nowEpochMs - startedAtEpochMs) / 1000L)
                .coerceAtLeast(0L)
            val unrecorded = (wallElapsedSec - lastRecordedSec).coerceAtLeast(0L)
            return RideInterruption(
                lastRecordedSec = lastRecordedSec.coerceAtLeast(0),
                // Int is safe: the coercion above cannot exceed the wall gap,
                // and a ride older than 68 years is not being resumed.
                unrecordedSec = unrecorded.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            )
        }
    }
}
