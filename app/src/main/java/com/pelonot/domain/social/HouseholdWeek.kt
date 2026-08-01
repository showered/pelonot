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

    private fun startOfDay(epochMs: Long, timeZone: TimeZone): Long =
        Calendar.getInstance(timeZone).apply {
            timeInMillis = epochMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

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
