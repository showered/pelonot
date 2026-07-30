package com.pelonot.domain.model

/**
 * Advances a class through its prescribed intervals.
 *
 * Deliberately a pure function of elapsed time rather than a self-driving
 * timer: the ride already has one authoritative clock in `WorkoutService`
 * (`SystemClock.elapsedRealtime()` minus paused time), and a second timer
 * ticking alongside it would drift away from it over a 45-minute class and
 * disagree about which interval is running. [stateAt] is called from the
 * service's existing ticker, so pausing a ride pauses the class for free.
 *
 * It is also why this class has no Android imports and is unit-tested on the
 * JVM.
 */
class ClassIntervalEngine(intervals: List<Interval>) {

    /** Sorted, because a hand-authored template need not be in order. */
    val intervals: List<Interval> = intervals.sortedBy { it.startSec }

    /** The class's own length, from its final interval's end. */
    val durationSec: Int = this.intervals.maxOfOrNull { it.endSec } ?: 0

    /**
     * The last interval hard enough to deserve "give it everything".
     *
     * Not simply the last interval: most well-built classes end on a cooldown,
     * and telling a rider to empty the tank during a Zone 1 spin-down is worse
     * than saying nothing.
     */
    private val finalHardIndex: Int =
        this.intervals.indexOfLast { it.powerZoneNumber >= HARD_ZONE }

    fun stateAt(elapsedSec: Int): IntervalState {
        if (intervals.isEmpty()) return IntervalState.NONE

        val elapsed = elapsedSec.coerceAtLeast(0)
        val lastIndex = intervals.lastIndex

        if (elapsed >= durationSec) {
            // Past the end: hold the final interval so the UI has something to
            // render for the instant between the last tick and the ride being
            // finalised, and flag completion for the service to act on.
            val last = intervals[lastIndex]
            return IntervalState(
                current = last,
                next = null,
                index = lastIndex,
                intervalCount = intervals.size,
                elapsedInIntervalSec = last.durationSec,
                remainingInIntervalSec = 0,
                classElapsedSec = durationSec,
                classDurationSec = durationSec,
                cue = cueFor(lastIndex, last),
                isComplete = true
            )
        }

        // indexOfLast rather than a containment test, so a template with a gap
        // between two segments holds the previous one instead of blanking the
        // HUD until the next starts.
        val index = intervals.indexOfLast { it.startSec <= elapsed }
        if (index < 0) {
            // The class does not start at t=0. Nothing prescribed yet.
            return IntervalState(
                current = null,
                next = intervals.first(),
                index = -1,
                intervalCount = intervals.size,
                remainingInIntervalSec = intervals.first().startSec - elapsed,
                classElapsedSec = elapsed,
                classDurationSec = durationSec
            )
        }

        val current = intervals[index]
        return IntervalState(
            current = current,
            next = intervals.getOrNull(index + 1),
            index = index,
            intervalCount = intervals.size,
            elapsedInIntervalSec = (elapsed - current.startSec).coerceAtLeast(0),
            remainingInIntervalSec = (current.endSec - elapsed).coerceAtLeast(0),
            classElapsedSec = elapsed,
            classDurationSec = durationSec,
            cue = cueFor(index, current)
        )
    }

    private fun cueFor(index: Int, interval: Interval): RideCue = when {
        index == finalHardIndex -> RideCue.FinalPush
        index == intervals.lastIndex && interval.isRecovery -> RideCue.CoolDown
        else -> RideCue.None
    }

    companion object {
        /** Zone 4 and above is "hard" for the purposes of the closing cue. */
        const val HARD_ZONE = 4
    }
}
