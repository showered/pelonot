package com.pelonot.data.sensor

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 11.6.7 — the numbers change too fast to read.
 *
 * The board reports several times a second. On virtual time, so a half-second
 * display interval costs nothing to assert.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DisplayRateTest {

    private val interval = 500L

    @Test
    fun `the first value is shown at once rather than half a second late`() = runTest {
        val board = MutableSharedFlow<Int>(extraBufferCapacity = 16)
        val seen = mutableListOf<Int>()
        val job = launch { board.atDisplayRate(interval).collect { seen += it } }
        runCurrent()

        board.emit(1)
        runCurrent()

        assertEquals(listOf(1), seen)
        job.cancel()
    }

    @Test
    fun `a board reporting ten times a second updates the screen twice`() = runTest {
        val fast = flow {
            // Ten a second for one second, the rate the bike actually reports at.
            repeat(10) { emit(it) ; delay(100) }
        }

        val seen = fast.atDisplayRate(interval).toList()

        assertTrue("expected about 2 updates per second, got ${seen.size}: $seen", seen.size <= 3)
    }

    /**
     * Nothing is averaged and nothing is invented — every number on screen is
     * one the board actually reported, which is the same rule the fence follows.
     * What the rider sees is simply the *latest* of them.
     */
    @Test
    fun `the value shown is the newest one, not the one the interval started on`() = runTest {
        val board = MutableSharedFlow<Int>(extraBufferCapacity = 16)
        val seen = mutableListOf<Int>()
        val job = launch { board.atDisplayRate(interval).collect { seen += it } }
        runCurrent()

        board.emit(1)
        runCurrent()
        // Four more arrive inside the same display window and are dropped, not
        // queued: a burst must not play out in slow motion afterwards.
        board.emit(2); board.emit(3); board.emit(4); board.emit(5)
        runCurrent()

        assertEquals(listOf(1), seen)

        advanceTimeBy(interval + 1)
        runCurrent()

        assertEquals(listOf(1, 5), seen)
        job.cancel()
    }

    @Test
    fun `a board that goes quiet leaves the last value alone`() = runTest {
        val board = MutableSharedFlow<Int>(extraBufferCapacity = 16)
        val seen = mutableListOf<Int>()
        val job = launch { board.atDisplayRate(interval).collect { seen += it } }
        runCurrent()

        board.emit(7)
        advanceTimeBy(10_000)
        runCurrent()

        // Ten seconds of silence invents nothing. Whether that stale value is
        // still worth showing is `isStaleAt`'s question, not this one.
        assertEquals(listOf(7), seen)
        job.cancel()
    }
}
