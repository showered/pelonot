package com.pelonot.domain.coach

import com.pelonot.domain.model.RidePosition

/**
 * When standing or sitting down is worth saying (PLAN 25.2.3, 25.3.2).
 *
 * **The change is the message, not the state.** A rider who has been standing
 * for two minutes does not need telling; a rider who has to stand *now* does.
 * So this answers one question — *is this interval boundary a call?* — and it
 * answers it the same way for every surface that asks.
 *
 * Two rules are easy to get subtly wrong, which is why they live here once
 * rather than at each call site:
 *
 * - **It is keyed on the value, not on the interval index.** `CLB-06`
 *   alternates climb and attack six times, and announcing the state rather than
 *   the change would call "stay seated" twelve times in one class.
 * - **It compares against the interval just left, not against the last thing
 *   announced.** A rider sits down during the recovery between two standing
 *   efforts, so the second effort has to be called again — and it is the
 *   *absence* of a prescription in between that makes it a new instruction.
 *
 * Absent is not a third value and never produces a call of its own: a class
 * that stops prescribing a position is handing the choice back, silently.
 *
 * Not thread-safe, and does not need to be — one ride, one caller.
 */
class PositionCallTracker {

    private var lastIndex = UNSET
    private var lastPosition: RidePosition? = null

    fun reset() {
        lastIndex = UNSET
        lastPosition = null
    }

    /**
     * Advance to [index], and return the position to call out there — or null
     * when there is nothing to say.
     *
     * Idempotent within an interval: calling twice for the same [index] returns
     * null the second time, so a caller that ticks at 1 Hz gets one call rather
     * than sixty.
     */
    fun onInterval(index: Int, position: RidePosition?): RidePosition? {
        if (index == lastIndex) return null
        lastIndex = index
        val call = position?.takeIf { it != lastPosition }
        lastPosition = position
        return call
    }

    private companion object {
        const val UNSET = -1
    }
}
