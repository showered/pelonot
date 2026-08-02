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
}
