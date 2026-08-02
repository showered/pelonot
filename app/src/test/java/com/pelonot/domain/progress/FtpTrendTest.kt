package com.pelonot.domain.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules a card full of FTP has to follow (PLAN 7.10.2 / 22.1.4).
 *
 * All of them are about **not claiming more than the data says**, which is the
 * same argument as nullable `heartRateBpm` and as the plausibility fence: a
 * value the app does not have must not be invented to make a picture tidier.
 */
class FtpTrendTest {

    private fun point(watts: Int, at: Long, source: String = "Unknown") =
        FtpPoint(watts = watts, atEpochMs = at, source = source)

    @Test
    fun `nothing recorded is not a trend`() {
        val trend = FtpTrend()
        assertNull(trend.current)
        assertNull(trend.previous)
        assertNull(trend.lastChange)
        assertNull(trend.deltaWatts)
        assertNull(trend.range)
        assertFalse(trend.hasMoved)
    }

    @Test
    fun `a rider whose FTP has never moved has a current value and no change`() {
        // Every rider has exactly this on their first look at the app: one row,
        // the value their profile started with. Reporting it as a change would
        // be announcing an event that never happened.
        val trend = FtpTrend(listOf(point(200, at = 1_000)))
        assertEquals(200, trend.current)
        assertNull(trend.previous)
        assertNull(trend.lastChange)
        assertNull(trend.deltaWatts)
        assertFalse(trend.hasMoved)
    }

    @Test
    fun `one change gives a direction and the value it moved from`() {
        val trend = FtpTrend(
            listOf(point(200, at = 1_000), point(215, at = 2_000, source = "ManualEdit"))
        )
        assertEquals(215, trend.current)
        assertEquals(200, trend.previous)
        assertEquals(15, trend.deltaWatts)
        assertEquals("ManualEdit", trend.lastChange?.source)
        assertTrue(trend.hasMoved)
    }

    @Test
    fun `a fall is a fall and is not hidden`() {
        // FTP goes down after illness, after a layoff, and when a rider corrects
        // an optimistic guess. A progress card that could only go up would be
        // lying by omission.
        val trend = FtpTrend(
            listOf(point(230, at = 1_000), point(212, at = 2_000, source = "ManualEdit"))
        )
        assertEquals(-18, trend.deltaWatts)
    }

    @Test
    fun `the direction is measured against the previous value, not the lowest`() {
        // A rider who went 200 → 240 → 225 has fallen 15, not risen 25.
        val trend = FtpTrend(
            listOf(point(200, at = 1), point(240, at = 2), point(225, at = 3))
        )
        assertEquals(-15, trend.deltaWatts)
        assertEquals(240, trend.previous)
    }

    @Test
    fun `the range is padded so one small change is not drawn as a cliff`() {
        // 200 to 205 fitted exactly fills a sparkline top to bottom with a five
        // watt move. True, and a wild overstatement.
        val trend = FtpTrend(listOf(point(200, at = 1), point(205, at = 2)))
        val range = trend.range!!
        assertTrue("the range must extend below the lowest value", range.first < 200)
        assertTrue("the range must extend above the highest value", range.last > 205)
        assertTrue("five watts must not fill the box", range.last - range.first >= 20)
    }

    @Test
    fun `the range covers a single point too, so a flat card can still scale`() {
        val range = FtpTrend(listOf(point(200, at = 1))).range!!
        assertTrue(range.first < 200 && range.last > 200)
    }

    @Test
    fun `the first value is not a change`() {
        // The row every rider has on their first look at the app. It is where
        // the number began, not somewhere it moved to, and a list that showed it
        // as "+200 W" would be inventing an event.
        assertTrue(FtpTrend(listOf(point(200, at = 1_000))).changes.isEmpty())
        assertEquals(200, FtpTrend(listOf(point(200, at = 1_000))).startedAt?.watts)
    }

    @Test
    fun `every change carries the value either side of it`() {
        val trend = FtpTrend(
            listOf(
                point(200, at = 1_000),
                point(215, at = 2_000, source = "ManualEdit"),
                point(228, at = 3_000, source = "AutoBreakthrough")
            )
        )
        assertEquals(2, trend.changes.size)
        // Newest first: this is a record of what happened, not a line to draw.
        val newest = trend.changes.first()
        assertEquals(215, newest.from)
        assertEquals(228, newest.to)
        assertEquals(13, newest.deltaWatts)
        assertTrue(newest.isRise)
        assertEquals("AutoBreakthrough", newest.source)
        assertEquals(200, trend.changes.last().from)
    }

    @Test
    fun `a change keeps the ride that caused it`() {
        val trend = FtpTrend(
            listOf(
                point(200, at = 1_000),
                FtpPoint(
                    watts = 232,
                    atEpochMs = 2_000,
                    source = "AutoBreakthrough",
                    workoutId = "ride-1"
                )
            )
        )
        assertEquals("ride-1", trend.changes.single().workoutId)
    }

    @Test
    fun `a fall reads as a fall in the list as well as on the card`() {
        val trend = FtpTrend(listOf(point(240, at = 1), point(225, at = 2)))
        val change = trend.changes.single()
        assertEquals(-15, change.deltaWatts)
        assertFalse(change.isRise)
    }

    @Test
    fun `a history of one has no span, so a chart cannot divide by it`() {
        assertNull(FtpTrend().spanMs)
        assertNull(FtpTrend(listOf(point(200, at = 1_000))).spanMs)
        // Two values recorded in the same millisecond is not a span either —
        // migration 7→8 seeds a profile's first row at the time it runs.
        assertNull(FtpTrend(listOf(point(200, at = 1_000), point(215, at = 1_000))).spanMs)
        assertEquals(1_000L, FtpTrend(listOf(point(200, at = 1_000), point(215, at = 2_000))).spanMs)
    }

    @Test
    fun `the chart's axis runs to now, not to the last change`() {
        // Otherwise the line stops on the day of the last change, which says the
        // record ends there. It does not — the value is true today, and the run
        // from the change to the right-hand edge is how long it has been held.
        val trend = FtpTrend(listOf(point(200, at = 1_000), point(215, at = 2_000)))
        assertEquals(9_000L, trend.spanToNow(nowEpochMs = 10_000))
    }

    @Test
    fun `a rider whose FTP has never moved still spans from then until now`() {
        val trend = FtpTrend(listOf(point(200, at = 1_000)))
        assertNull(trend.spanMs)
        assertEquals(4_000L, trend.spanToNow(nowEpochMs = 5_000))
    }

    @Test
    fun `a clock behind the record does not mirror the chart`() {
        // A device whose time has moved backwards. A negative span would draw
        // the trend right to left.
        val trend = FtpTrend(listOf(point(200, at = 1_000), point(215, at = 9_000)))
        assertEquals(8_000L, trend.spanToNow(nowEpochMs = 2_000))
        assertNull(FtpTrend(listOf(point(200, at = 5_000))).spanToNow(nowEpochMs = 1_000))
    }
}
