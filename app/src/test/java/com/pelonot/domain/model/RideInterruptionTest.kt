package com.pelonot.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 8.3d — the gap 8.3a called "of unknown length". */
class RideInterruptionTest {

    private val start = 1_700_000_000_000L

    private fun at(minutes: Long) = start + minutes * 60_000L

    @Test
    fun `unrecorded time is the wall gap less the seconds that recorded`() {
        // Ten minutes of wall clock, six minutes of it recorded.
        val interruption = RideInterruption.between(
            startedAtEpochMs = start,
            lastRecordedSec = 360,
            nowEpochMs = at(10)
        )

        assertEquals(360, interruption.lastRecordedSec)
        assertEquals(240, interruption.unrecordedSec)
    }

    @Test
    fun `a ride still being ridden has nothing unrecorded`() {
        val interruption = RideInterruption.between(
            startedAtEpochMs = start,
            lastRecordedSec = 600,
            nowEpochMs = at(10)
        )

        assertEquals(0, interruption.unrecordedSec)
        assertTrue(interruption.isResumable)
    }

    @Test
    fun `a short interruption is resumable`() {
        val interruption = RideInterruption.between(
            startedAtEpochMs = start,
            lastRecordedSec = 300,
            nowEpochMs = at(7)
        )

        assertEquals(120, interruption.unrecordedSec)
        assertTrue(interruption.isResumable)
    }

    @Test
    fun `a ride abandoned yesterday is not resumable`() {
        val interruption = RideInterruption.between(
            startedAtEpochMs = start,
            lastRecordedSec = 300,
            nowEpochMs = start + 24 * 60 * 60_000L
        )

        assertFalse(interruption.isResumable)
    }

    @Test
    fun `the boundary is inclusive so a tablet that took exactly the limit still counts`() {
        val exactly = RideInterruption(
            lastRecordedSec = 300,
            unrecordedSec = RideInterruption.MAX_RESUMABLE_BREAK_SEC
        )
        val oneSecondMore = exactly.copy(
            unrecordedSec = RideInterruption.MAX_RESUMABLE_BREAK_SEC + 1
        )

        assertTrue(exactly.isResumable)
        assertFalse(oneSecondMore.isResumable)
    }

    @Test
    fun `a ride that recorded nothing is not resumable however fresh it is`() {
        // The keep path deletes this one rather than offering it; resume must
        // not offer it either, or the rider is handed an empty ride to finish.
        val interruption = RideInterruption.between(
            startedAtEpochMs = start,
            lastRecordedSec = 0,
            nowEpochMs = at(1)
        )

        assertFalse(interruption.isResumable)
    }

    @Test
    fun `a wall clock that moved backwards floors to zero rather than going negative`() {
        // The tablet corrected its clock backwards after the ride started, so
        // "now" is before the row's own timestamp. This ride does then look
        // freshly interrupted and will be offered — which is accepted rather
        // than defended against: the app cannot know how stale it is, the cost
        // of guessing "fresh" is a prompt the rider declines, and the cost of
        // guessing "stale" is silently withholding a ride they wanted back.
        val interruption = RideInterruption.between(
            startedAtEpochMs = at(60),
            lastRecordedSec = 300,
            nowEpochMs = start
        )

        assertEquals(0, interruption.unrecordedSec)
        assertEquals(300, interruption.lastRecordedSec)
        assertTrue(interruption.isResumable)
    }
}
