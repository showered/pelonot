package com.pelonot.domain.social

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Streaks, checked against a fixed clock in a fixed timezone.
 *
 * The timezone is London rather than UTC on purpose: it is one that actually
 * changes offset, so the DST cases below are real rather than decorative.
 */
class StreakCalculatorTest {

    private val zone = TimeZone.getTimeZone("Europe/London")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 18, minute: Int = 0): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month - 1, day, hour, minute)
        }.timeInMillis

    private fun streak(vararg rides: Long, now: Long) =
        StreakCalculator.currentStreak(rides.toList(), now = now, timeZone = zone)

    @Test
    fun `no rides is no streak`() {
        assertEquals(0, StreakCalculator.currentStreak(emptyList(), now = at(2026, 8, 1), timeZone = zone))
    }

    @Test
    fun `a ride today is a streak of one`() {
        assertEquals(1, streak(at(2026, 8, 1, hour = 7), now = at(2026, 8, 1, hour = 20)))
    }

    @Test
    fun `consecutive days count`() {
        assertEquals(
            4,
            streak(
                at(2026, 7, 29), at(2026, 7, 30), at(2026, 7, 31), at(2026, 8, 1),
                now = at(2026, 8, 1, hour = 22)
            )
        )
    }

    /**
     * The only real decision in the calculator. Somebody who rode six days
     * running and has not yet ridden *today* has a streak of six, not zero:
     * telling them it is over before the day is out is both wrong and the sort
     * of thing that makes a person give up.
     */
    @Test
    fun `a streak that ended yesterday still counts today`() {
        assertEquals(
            3,
            streak(
                at(2026, 7, 29), at(2026, 7, 30), at(2026, 7, 31),
                now = at(2026, 8, 1, hour = 9)
            )
        )
    }

    @Test
    fun `a gap of a whole day ends it`() {
        assertEquals(
            2,
            streak(
                at(2026, 7, 20), at(2026, 7, 21),
                at(2026, 7, 31), at(2026, 8, 1),
                now = at(2026, 8, 1, hour = 22)
            )
        )
    }

    @Test
    fun `nothing since the day before yesterday is not a streak`() {
        assertEquals(
            0,
            streak(at(2026, 7, 29), at(2026, 7, 30), now = at(2026, 8, 1, hour = 12))
        )
    }

    @Test
    fun `two rides on one day are one day`() {
        assertEquals(
            2,
            streak(
                at(2026, 7, 31, hour = 7), at(2026, 7, 31, hour = 19),
                at(2026, 8, 1, hour = 8),
                now = at(2026, 8, 1, hour = 20)
            )
        )
    }

    /**
     * The clocks go forward in London on 29 March 2026, so that day is 23 hours
     * long. Stepping back a fixed 86 400 000 ms from 30 March lands at 23:00 on
     * the 29th — still the right day here, but from 30 March it lands on the
     * 29th at 23:00 and from 29 March it lands on the 28th at 01:00. A
     * calculator that walked back in fixed milliseconds would break a streak
     * that was never broken, on a date nobody would think to test.
     */
    @Test
    fun `a spring-forward day does not break a streak`() {
        assertEquals(
            4,
            streak(
                at(2026, 3, 28), at(2026, 3, 29), at(2026, 3, 30), at(2026, 3, 31),
                now = at(2026, 3, 31, hour = 21)
            )
        )
    }

    /** And the same in the other direction: 25 October 2026 is 25 hours long. */
    @Test
    fun `an autumn fall-back day does not break a streak`() {
        assertEquals(
            3,
            streak(
                at(2026, 10, 24), at(2026, 10, 25), at(2026, 10, 26),
                now = at(2026, 10, 26, hour = 21)
            )
        )
    }
}
