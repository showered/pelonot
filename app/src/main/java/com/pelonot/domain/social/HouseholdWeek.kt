package com.pelonot.domain.social

import java.util.Calendar
import java.util.TimeZone

/**
 * One rider's week, for the dashboard's household panel (PLAN 24.2).
 *
 * Note what is *not* here: anybody who has not ridden. That is 24.2.4 made
 * structural rather than remembered — "Sam hasn't ridden this week" is a
 * feature that starts arguments, and the safest way not to ship it is to have
 * no row that could carry it. A rider with no rides in the window is absent
 * from the list, not present with a zero.
 */
data class HouseholdRiderWeek(
    val localUserId: Int,
    val name: String,
    val rides: Int,
    val outputKj: Double,
    val lastRideAt: Long,
    /** Consecutive days ending today or yesterday, or 0. See [StreakCalculator]. */
    val streakDays: Int
)

/**
 * How many days in a row a rider has ridden.
 *
 * Pure and JVM-tested, with the clock and the timezone injected, for the same
 * reason `RideDayGrouping` is: day boundaries are the piece of date arithmetic
 * that is easy to get subtly wrong and impossible to notice, because an
 * off-by-one only shows around midnight, in a timezone the author does not live
 * in, or on the two days a year the offset changes.
 *
 * `java.util.Calendar` rather than `java.time` deliberately — `minSdk` is 24
 * and the project does not enable core library desugaring.
 */
object StreakCalculator {

    /**
     * The rider's current run of consecutive riding days.
     *
     * **A streak that ended yesterday still counts today**, and this is the
     * only real decision in here. Someone who rode six days running and has not
     * yet ridden today has a streak of six, not zero: telling them it is over
     * before the day is out is both wrong and the sort of thing that makes a
     * person give up. It ends when a whole day passes with no ride in it.
     */
    fun currentStreak(
        rideTimestamps: List<Long>,
        now: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Int {
        if (rideTimestamps.isEmpty()) return 0

        val days = rideTimestamps.map { startOfDay(it, timeZone) }.toSortedSet().toList().reversed()
        val today = startOfDay(now, timeZone)
        val yesterday = previousDay(today, timeZone)

        var expected = when (days.first()) {
            today -> today
            yesterday -> yesterday
            // The most recent ride is older than yesterday: nothing is running.
            else -> return 0
        }

        var streak = 0
        for (day in days) {
            if (day != expected) break
            streak++
            expected = previousDay(expected, timeZone)
        }
        return streak
    }

    /**
     * The rider's current run of consecutive **weeks** with a ride in them
     * (PLAN 22.5.2).
     *
     * The unit matters more than the arithmetic. [currentStreak] counts days,
     * and on the owner's own stated assumption — a rider uses the bike at most
     * once a week — a perfect year of Sundays is a day-streak of **1**, which
     * the dashboard does not even show, on the grounds that calling a single
     * ride a streak is flattery. So the feature built to reward consistency was
     * blind to the most consistent rider it can have. Counted in weeks it reads
     * "7 weeks in a row", which is both true and worth keeping up.
     *
     * **A streak that ended last week still counts this week**, for exactly the
     * reason the daily one gives a rider until the end of today: somebody who
     * rode last Sunday and has not yet ridden this week has not stopped, and
     * telling them they have is how a person gives up. It ends when a whole
     * week passes with no ride in it.
     */
    fun currentWeeklyStreak(
        rideTimestamps: List<Long>,
        now: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Int {
        if (rideTimestamps.isEmpty()) return 0

        val weeks = rideTimestamps.map { startOfWeek(it, timeZone) }
            .toSortedSet().toList().reversed()
        val thisWeek = startOfWeek(now, timeZone)
        val lastWeek = previousWeek(thisWeek, timeZone)

        var expected = when (weeks.first()) {
            thisWeek -> thisWeek
            lastWeek -> lastWeek
            else -> return 0
        }

        var streak = 0
        for (week in weeks) {
            if (week != expected) break
            streak++
            expected = previousWeek(expected, timeZone)
        }
        return streak
    }

    private fun startOfDay(epochMs: Long, timeZone: TimeZone): Long =
        Calendar.getInstance(timeZone).apply {
            timeInMillis = epochMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /** The locale's first day of the week, so the boundary matches the calendar. */
    private fun startOfWeek(epochMs: Long, timeZone: TimeZone): Long {
        val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = epochMs }
        val first = calendar.firstDayOfWeek
        val offset = (calendar.get(Calendar.DAY_OF_WEEK) - first + DAYS_IN_WEEK) % DAYS_IN_WEEK
        return startOfDay(calendar.timeInMillis, timeZone).let { startOfDayMs ->
            // Back one day at a time rather than by `offset × 86_400_000`, for
            // the same daylight-saving reason as `previousDay`.
            (0 until offset).fold(startOfDayMs) { day, _ -> previousDay(day, timeZone) }
        }
    }

    private fun previousWeek(startOfWeekMs: Long, timeZone: TimeZone): Long =
        (0 until DAYS_IN_WEEK).fold(startOfWeekMs) { day, _ -> previousDay(day, timeZone) }

    private const val DAYS_IN_WEEK = 7

    /**
     * Not `day - 86_400_000`: a day is 23 or 25 hours long twice a year in most
     * of the world, and subtracting a fixed 24 hours lands an hour inside the
     * wrong day on those two dates — which would break a streak that was never
     * broken, on a date nobody would think to test.
     */
    private fun previousDay(startOfDayMs: Long, timeZone: TimeZone): Long =
        startOfDay(startOfDayMs - HALF_A_DAY_MS, timeZone)

    private const val HALF_A_DAY_MS = 12L * 60 * 60 * 1000
}
