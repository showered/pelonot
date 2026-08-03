package com.pelonot.domain.progress

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * One finished ride, reduced to what a trend needs (PLAN 16.3.2, 16.3.5).
 *
 * Three columns off `workouts` and nothing else: the per-second series is 2,700
 * rows a ride and none of it is needed to answer *how much, and how often*.
 */
data class RideRecord(
    val atEpochMs: Long,
    val durationSec: Int,
    val outputKj: Double
)

/** One calendar day of riding. Absent from a week's list means the day is still to come. */
data class RidingDay(
    val startMs: Long,
    val rides: Int,
    val durationSec: Int,
    val outputKj: Double
) {
    val minutes: Int get() = (durationSec / 60.0).roundToInt()
    val ridden: Boolean get() = rides > 0
}

/**
 * One week, carrying both its totals and its seven days.
 *
 * The two views on this screen — the bars and the calendar — are built from the
 * same object on purpose. A weekly total computed one way and a set of day
 * squares computed another can disagree by a ride that fell either side of
 * midnight, and the rider would be looking at both at once.
 */
data class RidingWeek(
    val startMs: Long,
    /**
     * Seven entries, Monday-or-Sunday first depending on the locale, with
     * **null for a day that has not happened yet**.
     *
     * A future day is not a day off. Drawing the rest of the current week as
     * four empty squares says the rider missed days they have not reached.
     */
    val days: List<RidingDay?>
) {
    val rides: Int get() = days.sumOf { it?.rides ?: 0 }

    val durationSec: Int get() = days.sumOf { it?.durationSec ?: 0 }

    val minutes: Int get() = (durationSec / 60.0).roundToInt()

    val outputKj: Double get() = days.sumOf { it?.outputKj ?: 0.0 }

    /** True while the week is still being ridden, which is why it may be short. */
    val isPartial: Boolean get() = days.any { it == null }
}

/**
 * How much a rider has ridden, and how often, over a window of whole weeks.
 *
 * Two questions that are really one, which is why they are one object and end up
 * on one screen: *volume* (16.3.2) is the height of the bars and *consistency*
 * (16.3.5) is the pattern of the squares, and a rider reading either one alone
 * gets half the picture — 300 minutes in a week is a different training week
 * depending on whether it was one ride or five.
 */
data class RidingHistory(
    val weeks: List<RidingWeek> = emptyList(),
    /** Consecutive riding days ending today or yesterday. See `StreakCalculator`. */
    val streakDays: Int = 0
) {
    val hasAnything: Boolean get() = weeks.any { it.rides > 0 }

    val totalRides: Int get() = weeks.sumOf { it.rides }

    /** The week being ridden now, which is always the last one. */
    val currentWeek: RidingWeek? get() = weeks.lastOrNull()

    /**
     * The tallest **finished** week, which is what the bars are scaled against.
     *
     * Scaling to include the current week would make a Monday with one ride in
     * it the full height of the chart, and every finished week a stub beside it.
     */
    val busiestFinishedMinutes: Int
        get() = weeks.filterNot { it.isPartial }.maxOfOrNull { it.minutes } ?: 0

    val busiestFinishedOutputKj: Double
        get() = weeks.filterNot { it.isPartial }.maxOfOrNull { it.outputKj } ?: 0.0

    /** The busiest single day, for the calendar's intensity scale. */
    val busiestDayMinutes: Int
        get() = weeks.flatMap { it.days }.maxOfOrNull { it?.minutes ?: 0 } ?: 0

    /** Weeks with at least one ride in them, out of the finished weeks on show. */
    val weeksRiddenIn: Int get() = weeks.count { it.rides > 0 }
}

/**
 * Turns a list of rides into whole weeks of days.
 *
 * Pure, with the clock and the timezone injected, for the reason
 * `StreakCalculator` and `RideDayGrouping` both are: day and week boundaries are
 * the piece of date arithmetic that is easy to get subtly wrong and impossible
 * to notice, because the mistake only shows around midnight, in a timezone the
 * author does not live in, or on the two days a year the offset changes.
 *
 * `java.util.Calendar` rather than `java.time`, because `minSdk` is 24 and the
 * project does not enable core library desugaring.
 */
object RidingHistoryBuilder {

    const val DEFAULT_WEEKS = 17

    /**
     * @param weeks how many weeks back to build, including the current one.
     *
     * The window starts at the **beginning of a week**, so every week except
     * the last is whole and the bars are comparable. A window that started
     * mid-week would draw its first bar short and say the rider had an easy
     * week they did not have.
     */
    fun build(
        rides: List<RideRecord>,
        now: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
        weeks: Int = DEFAULT_WEEKS,
        streakDays: Int = 0
    ): RidingHistory {
        if (weeks <= 0) return RidingHistory(streakDays = streakDays)

        val today = startOfDay(now, timeZone)
        val thisWeek = startOfWeek(now, timeZone)
        val byDay = rides.groupBy { startOfDay(it.atEpochMs, timeZone) }

        // Backwards from this week, then reversed: stepping forwards by adding
        // seven days would drift an hour across a daylight-saving change and
        // land the boundary inside the wrong day.
        val weekStarts = generateSequence(thisWeek) { previousWeek(it, timeZone) }
            .take(weeks)
            .toList()
            .reversed()

        return RidingHistory(
            weeks = weekStarts.map { weekStart ->
                RidingWeek(
                    startMs = weekStart,
                    days = (0 until DAYS_IN_WEEK).map { offset ->
                        val day = addDays(weekStart, offset, timeZone)
                        // The future is absent, not empty.
                        if (day > today) return@map null
                        val ridesThatDay = byDay[day].orEmpty()
                        RidingDay(
                            startMs = day,
                            rides = ridesThatDay.size,
                            durationSec = ridesThatDay.sumOf { it.durationSec },
                            outputKj = ridesThatDay.sumOf { it.outputKj }
                        )
                    }
                )
            },
            streakDays = streakDays
        )
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
     * The week starts where the rider's locale says it does — Monday in Britain,
     * Sunday in the United States.
     *
     * Hard-coding Monday would put every bar a day out for half the world, and
     * the calendar's rows would not line up with the calendar on their wall.
     */
    private fun startOfWeek(epochMs: Long, timeZone: TimeZone): Long {
        val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = startOfDay(epochMs, timeZone) }
        val first = calendar.firstDayOfWeek
        var backwards = calendar.get(Calendar.DAY_OF_WEEK) - first
        if (backwards < 0) backwards += DAYS_IN_WEEK
        return addDays(calendar.timeInMillis, -backwards, timeZone)
    }

    private fun previousWeek(startOfWeekMs: Long, timeZone: TimeZone): Long =
        addDays(startOfWeekMs, -DAYS_IN_WEEK, timeZone)

    /**
     * Not `days * 86_400_000`: a day is 23 or 25 hours long twice a year in most
     * of the world, and a fixed offset lands an hour inside the wrong day on
     * exactly the dates nobody thinks to test.
     */
    private fun addDays(startOfDayMs: Long, days: Int, timeZone: TimeZone): Long =
        Calendar.getInstance(timeZone).apply {
            timeInMillis = startOfDayMs
            add(Calendar.DAY_OF_YEAR, days)
        }.timeInMillis

    private const val DAYS_IN_WEEK = 7
}
