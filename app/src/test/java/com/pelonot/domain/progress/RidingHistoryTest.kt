package com.pelonot.domain.progress

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Weeks and days (PLAN 16.3.2, 16.3.5).
 *
 * Everything here is a date-boundary test, which is the only kind this object
 * can really fail: the arithmetic is trivial and the calendar is not.
 */
class RidingHistoryTest {

    private val london = TimeZone.getTimeZone("Europe/London")

    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 12,
        zone: TimeZone = london
    ): Long = Calendar.getInstance(zone).apply {
        clear()
        set(year, month - 1, day, hour, 0, 0)
    }.timeInMillis

    private fun ride(atMs: Long, minutes: Int = 30, kj: Double = 200.0) =
        RideRecord(atEpochMs = atMs, durationSec = minutes * 60, outputKj = kj)

    @Test
    fun `no rides is an empty history rather than a wall of zeroes`() {
        val history = RidingHistoryBuilder.build(
            emptyList(),
            now = at(2026, 8, 2),
            timeZone = london,
            weeks = 4
        )

        assertEquals(4, history.weeks.size)
        assertFalse(history.hasAnything)
        assertEquals(0, history.totalRides)
    }

    @Test
    fun `the window is whole weeks, so the bars are comparable`() {
        // 2 August 2026 is a Sunday. Whatever the locale's first day is, the
        // last week must contain today and every earlier week must be whole.
        val history = RidingHistoryBuilder.build(
            listOf(ride(at(2026, 8, 2))),
            now = at(2026, 8, 2),
            timeZone = london,
            weeks = 6
        )

        assertEquals(6, history.weeks.size)
        history.weeks.dropLast(1).forEach { week ->
            assertFalse("a finished week must have seven days", week.isPartial)
            assertEquals(7, week.days.size)
        }
        assertEquals(1, history.totalRides)
    }

    @Test
    fun `the days after today are absent, not empty`() {
        // A Wednesday. The rest of the week has not happened, and drawing it as
        // days off would tell the rider they missed rides they have not reached.
        val wednesday = at(2026, 7, 29)
        val history = RidingHistoryBuilder.build(
            listOf(ride(wednesday)),
            now = wednesday,
            timeZone = london,
            weeks = 3
        )

        val current = history.currentWeek!!
        assertTrue(current.isPartial)
        assertTrue("today must be present", current.days.any { it?.ridden == true })
        assertTrue("the rest of the week must be null", current.days.any { it == null })
        // And every day up to and including today is there.
        val known = current.days.takeWhile { it != null }
        assertTrue(known.isNotEmpty())
        assertNotNull(known.last())
    }

    @Test
    fun `two rides on one day are one day with two rides in it`() {
        val day = at(2026, 7, 29, hour = 7)
        val history = RidingHistoryBuilder.build(
            listOf(ride(day, minutes = 20, kj = 150.0), ride(day + 3_600_000, minutes = 45, kj = 400.0)),
            now = day,
            timeZone = london,
            weeks = 2
        )

        val ridden = history.weeks.flatMap { it.days }.filterNotNull().single { it.ridden }
        assertEquals(2, ridden.rides)
        assertEquals(65, ridden.minutes)
        assertEquals(550.0, ridden.outputKj, 0.001)
        assertEquals(1, history.weeksRiddenIn)
    }

    /**
     * The boundary this whole object exists to get right. A ride at 23:50 in
     * London is a different *day* from one at 00:10, and both are the same day
     * in a timezone three hours east.
     */
    @Test
    fun `a late ride belongs to the day the rider was riding it`() {
        val lateSunday = at(2026, 8, 2, hour = 23)
        val history = RidingHistoryBuilder.build(
            listOf(ride(lateSunday)),
            now = at(2026, 8, 3, hour = 9),
            timeZone = london,
            weeks = 3
        )

        val ridden = history.weeks.flatMap { it.days }.filterNotNull().single { it.ridden }
        assertEquals(startOfDay(lateSunday), ridden.startMs)
    }

    /**
     * Stepping a week forward by adding 604,800,000 ms lands an hour inside the
     * wrong day the weekend the clocks change, which moves a Sunday ride into
     * the following week and is invisible for the other fifty weeks of the year.
     */
    @Test
    fun `the clocks changing does not move a ride into another week`() {
        // British Summer Time ended on 25 October 2026.
        val beforeTheChange = at(2026, 10, 24, hour = 10)
        val afterTheChange = at(2026, 10, 26, hour = 10)

        val history = RidingHistoryBuilder.build(
            listOf(ride(beforeTheChange), ride(afterTheChange)),
            now = at(2026, 10, 28, hour = 10),
            timeZone = london,
            weeks = 4
        )

        val riddenDays = history.weeks.flatMap { it.days }.filterNotNull().filter { it.ridden }
        assertEquals(2, riddenDays.size)
        // Every day sits at midnight local time, whichever side of the change
        // it falls on.
        riddenDays.forEach { day ->
            val calendar = Calendar.getInstance(london).apply { timeInMillis = day.startMs }
            assertEquals(0, calendar.get(Calendar.HOUR_OF_DAY))
            assertEquals(0, calendar.get(Calendar.MINUTE))
        }
    }

    @Test
    fun `the bars are scaled against finished weeks only`() {
        // A Monday with one ride already on it would otherwise be the tallest
        // week on the chart and make every real week a stub beside it.
        val monday = at(2026, 7, 27, hour = 7)
        val lastWeek = at(2026, 7, 21, hour = 7)

        val history = RidingHistoryBuilder.build(
            listOf(
                ride(monday, minutes = 90, kj = 900.0),
                ride(lastWeek, minutes = 40, kj = 300.0)
            ),
            now = at(2026, 7, 29),
            timeZone = london,
            weeks = 4
        )

        assertEquals(40, history.busiestFinishedMinutes)
        assertEquals(300.0, history.busiestFinishedOutputKj, 0.001)
        // The day scale is not restricted that way — the calendar's busiest
        // square is simply the busiest square.
        assertEquals(90, history.busiestDayMinutes)
    }

    @Test
    fun `a ride older than the window is not counted twice at the edge`() {
        val old = at(2026, 5, 1)
        val history = RidingHistoryBuilder.build(
            listOf(ride(old)),
            now = at(2026, 8, 2),
            timeZone = london,
            weeks = 4
        )

        assertFalse(history.hasAnything)
        assertNull(history.weeks.flatMap { it.days }.filterNotNull().firstOrNull { it.ridden })
    }

    private fun startOfDay(epochMs: Long): Long =
        Calendar.getInstance(london).apply {
            timeInMillis = epochMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
