package com.pelonot.domain.model

import java.util.Calendar
import java.util.TimeZone

/**
 * Groups rides into calendar days.
 *
 * A list of forty undifferentiated rows is not a record — it is a log. What a
 * rider is actually looking for is "the one I did on Tuesday", so the list is
 * broken by day with a header, and the two days they can name without a date
 * get named (12.1.2).
 *
 * Pure and JVM-tested, with the clock and the timezone injected. Day boundaries
 * are the one piece of date arithmetic that is easy to get subtly wrong and
 * impossible to notice: an off-by-one only shows up around midnight, in a
 * timezone the author does not live in, or on the two days a year the offset
 * changes.
 *
 * `java.util.Calendar` rather than `java.time` deliberately — `minSdk` is 24
 * and the project does not enable core library desugaring.
 */
object RideDayGrouping {

    /** How a day is named relative to today. */
    enum class Relative { Today, Yesterday, Earlier }

    data class Day<T>(
        /** Local midnight at the start of this day, for formatting a date. */
        val startOfDayMs: Long,
        val relative: Relative,
        val rides: List<T>
    )

    /**
     * Groups [items] by the local calendar day of [timestampOf], preserving the
     * order they arrive in — the query has already sorted them newest first,
     * and re-sorting here would silently paper over a query that had not.
     */
    fun <T> group(
        items: List<T>,
        now: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
        timestampOf: (T) -> Long
    ): List<Day<T>> {
        if (items.isEmpty()) return emptyList()

        val today = startOfDay(now, timeZone)
        // Not `today - 86_400_000`: a day is 23 or 25 hours long twice a year
        // in most of the world, and subtracting a fixed 24 hours puts
        // "Yesterday" an hour into the wrong day on those two dates.
        val yesterday = startOfDay(today - HALF_A_DAY_MS, timeZone)

        val days = LinkedHashMap<Long, MutableList<T>>()
        items.forEach { item ->
            val key = startOfDay(timestampOf(item), timeZone)
            days.getOrPut(key) { mutableListOf() }.add(item)
        }

        return days.map { (start, rides) ->
            Day(
                startOfDayMs = start,
                relative = when (start) {
                    today -> Relative.Today
                    yesterday -> Relative.Yesterday
                    else -> Relative.Earlier
                },
                rides = rides
            )
        }
    }

    private fun startOfDay(epochMs: Long, timeZone: TimeZone): Long =
        Calendar.getInstance(timeZone).apply {
            timeInMillis = epochMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private const val HALF_A_DAY_MS = 12L * 60 * 60 * 1000
}
