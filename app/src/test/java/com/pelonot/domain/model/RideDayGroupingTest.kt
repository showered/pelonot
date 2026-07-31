package com.pelonot.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Day boundaries.
 *
 * These are worth testing precisely because they are invisible when wrong: an
 * off-by-one only shows up around midnight, in a timezone the author does not
 * live in, or on the two days a year the UTC offset changes.
 */
class RideDayGroupingTest {

    private val london = TimeZone.getTimeZone("Europe/London")

    private fun at(
        year: Int, month: Int, day: Int, hour: Int, minute: Int = 0,
        zone: TimeZone = london
    ): Long = Calendar.getInstance(zone).apply {
        clear()
        set(year, month - 1, day, hour, minute, 0)
    }.timeInMillis

    private fun group(vararg times: Long, now: Long, zone: TimeZone = london) =
        RideDayGrouping.group(times.toList(), now = now, timeZone = zone) { it }

    @Test
    fun anEmptyListGroupsIntoNothing() {
        assertEquals(emptyList<Any>(), group(now = at(2026, 7, 31, 12)))
    }

    @Test
    fun ridesOnTheSameDayShareOneGroup() {
        val days = group(
            at(2026, 7, 31, 19),
            at(2026, 7, 31, 7),
            now = at(2026, 7, 31, 20)
        )

        assertEquals(1, days.size)
        assertEquals(2, days[0].rides.size)
        assertEquals(RideDayGrouping.Relative.Today, days[0].relative)
    }

    @Test
    fun theOrderTheRidesArrivedInIsPreserved() {
        // The query has already sorted newest first. Re-sorting here would hide
        // a query that had not.
        val evening = at(2026, 7, 31, 19)
        val morning = at(2026, 7, 31, 7)

        val days = group(evening, morning, now = at(2026, 7, 31, 20))

        assertEquals(listOf(evening, morning), days[0].rides)
    }

    @Test
    fun todayYesterdayAndEarlierAreNamedSeparately() {
        val days = group(
            at(2026, 7, 31, 9),
            at(2026, 7, 30, 18),
            at(2026, 7, 28, 18),
            now = at(2026, 7, 31, 20)
        )

        assertEquals(
            listOf(
                RideDayGrouping.Relative.Today,
                RideDayGrouping.Relative.Yesterday,
                RideDayGrouping.Relative.Earlier
            ),
            days.map { it.relative }
        )
    }

    @Test
    fun aRideJustBeforeMidnightIsNotTodayTheFollowingMorning() {
        val days = group(at(2026, 7, 30, 23, 58), now = at(2026, 7, 31, 0, 3))

        assertEquals(RideDayGrouping.Relative.Yesterday, days.single().relative)
    }

    @Test
    fun aRideJustAfterMidnightIsToday() {
        val days = group(at(2026, 7, 31, 0, 1), now = at(2026, 7, 31, 0, 3))

        assertEquals(RideDayGrouping.Relative.Today, days.single().relative)
    }

    /**
     * The clocks go forward in London on 29 March 2026, making that day 23
     * hours long. Subtracting a fixed 24 hours from the start of the 30th lands
     * at 23:00 on the 28th, so "yesterday" would name the wrong day.
     */
    @Test
    fun yesterdaySurvivesTheShortDayWhenTheClocksGoForward() {
        val days = group(at(2026, 3, 29, 10), now = at(2026, 3, 30, 9))

        assertEquals(RideDayGrouping.Relative.Yesterday, days.single().relative)
    }

    /** And the long day in October, which is the same bug in the other direction. */
    @Test
    fun yesterdaySurvivesTheLongDayWhenTheClocksGoBack() {
        val days = group(at(2026, 10, 25, 10), now = at(2026, 10, 26, 9))

        assertEquals(RideDayGrouping.Relative.Yesterday, days.single().relative)
    }

    @Test
    fun theDayBoundaryIsLocalNotUtc() {
        // 22:00 in Auckland on 31 July is 10:00 UTC the same day, but 20:00 in
        // Auckland on 1 August is 08:00 UTC — grouping on UTC days would split
        // one evening's rides across two headers for half the world.
        val auckland = TimeZone.getTimeZone("Pacific/Auckland")
        val days = group(
            at(2026, 7, 31, 23, 30, auckland),
            at(2026, 7, 31, 6, 30, auckland),
            now = at(2026, 7, 31, 23, 45, auckland),
            zone = auckland
        )

        assertEquals(1, days.size)
        assertEquals(RideDayGrouping.Relative.Today, days.single().relative)
    }

    @Test
    fun eachGroupCarriesLocalMidnightForItsOwnDay() {
        val days = group(at(2026, 7, 30, 18), now = at(2026, 7, 31, 20))

        assertEquals(at(2026, 7, 30, 0), days.single().startOfDayMs)
    }
}
