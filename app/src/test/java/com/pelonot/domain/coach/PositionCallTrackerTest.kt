package com.pelonot.domain.coach

import com.pelonot.domain.model.RidePosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The rule behind both the spoken call and the overlay's arrow (25.2.3,
 * 25.3.2): **the change is the message, not the state.**
 *
 * These are written as whole classes rather than as single transitions,
 * because every one of the interesting cases is about the sequence — what
 * `CLB-06` does over twelve intervals, and what a recovery block in between two
 * standing efforts means.
 */
class PositionCallTrackerTest {

    private fun callsAcross(positions: List<RidePosition?>): List<RidePosition?> {
        val tracker = PositionCallTracker()
        return positions.mapIndexed { index, position -> tracker.onInterval(index, position) }
    }

    @Test
    fun `a class that prescribes nothing never calls`() {
        assertEquals(
            listOf(null, null, null, null),
            callsAcross(listOf(null, null, null, null))
        )
    }

    @Test
    fun `the first prescription is a call`() {
        assertEquals(
            listOf(null, RidePosition.Standing),
            callsAcross(listOf(null, RidePosition.Standing))
        )
    }

    @Test
    fun `holding the same position across intervals is not a change`() {
        // A rider who has been standing for two intervals does not need telling
        // again at the boundary between them.
        assertEquals(
            listOf(RidePosition.Standing, null, null),
            callsAcross(
                listOf(RidePosition.Standing, RidePosition.Standing, RidePosition.Standing)
            )
        )
    }

    @Test
    fun `sitting down in between makes the next standing effort a new instruction`() {
        // The case the "compare against the last thing announced" version got
        // wrong: the recovery block prescribes nothing, the rider sits down, and
        // the second effort has to be called again.
        assertEquals(
            listOf(RidePosition.Standing, null, RidePosition.Standing),
            callsAcross(listOf(RidePosition.Standing, null, RidePosition.Standing))
        )
    }

    @Test
    fun `alternating climb and attack calls each change once and not twice`() {
        // CLB-06's shape, and the reason this is keyed on the value: announcing
        // the state would produce twelve calls in one class.
        val class6 = List(6) { listOf(RidePosition.Seated, RidePosition.Standing) }.flatten()
        val calls = callsAcross(class6)
        assertEquals(12, calls.size)
        assertEquals(12, calls.count { it != null })
        // Every one of them alternates, which is the point: they are all changes.
        assertEquals(class6, calls)
    }

    @Test
    fun `a run of the same position inside an alternating class stays silent`() {
        assertEquals(
            listOf(
                RidePosition.Seated,      // called
                null,                     // same
                RidePosition.Standing,    // called
                null,                     // same
                RidePosition.Seated       // called
            ),
            callsAcross(
                listOf(
                    RidePosition.Seated,
                    RidePosition.Seated,
                    RidePosition.Standing,
                    RidePosition.Standing,
                    RidePosition.Seated
                )
            )
        )
    }

    @Test
    fun `ticking repeatedly inside one interval calls once`() {
        // The overlay is recomposed many times a second and the coach ticks at
        // 1 Hz. Neither may produce sixty arrows for one instruction.
        val tracker = PositionCallTracker()
        assertEquals(RidePosition.Standing, tracker.onInterval(0, RidePosition.Standing))
        repeat(60) { assertNull(tracker.onInterval(0, RidePosition.Standing)) }
    }

    @Test
    fun `the index before the first interval is not an interval`() {
        // IntervalState.index is -1 until the class starts, and a free ride
        // never leaves it. Nothing may be called there.
        val tracker = PositionCallTracker()
        assertNull(tracker.onInterval(-1, null))
        assertEquals(RidePosition.Seated, tracker.onInterval(0, RidePosition.Seated))
    }

    @Test
    fun `reset forgets the position as well as the index`() {
        // A second ride of the same class must call its first standing block,
        // not treat it as unchanged from the last ride's final one.
        val tracker = PositionCallTracker()
        tracker.onInterval(0, RidePosition.Standing)
        tracker.reset()
        assertEquals(RidePosition.Standing, tracker.onInterval(0, RidePosition.Standing))
    }
}
