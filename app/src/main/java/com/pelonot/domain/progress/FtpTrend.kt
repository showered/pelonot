package com.pelonot.domain.progress

/**
 * A rider's FTP over time, reduced to what a card can draw (PLAN 7.10.2,
 * 16.3.1, 22.1.4).
 *
 * Pure, so the rules live somewhere they can be tested without a database or a
 * screen. Two of them are the whole point:
 *
 * **The line is stepped, not interpolated.** FTP does not drift smoothly
 * between two rides — it is a number that was true until the day it changed and
 * then was a different number. Drawing a diagonal between 200 and 215 claims
 * the rider passed through 207 on a Tuesday, which is a thing the app does not
 * know and that no measurement supports. Same family as `heartRateBpm` being
 * nullable: do not invent a value to make a picture tidier.
 *
 * **A history of one is not a trend.** Every rider has a first row — the value
 * their profile started with, or the one migration 7→8 seeded — and a card that
 * announced "last changed" for it would be reporting an event that never
 * happened, on a new rider's very first look at the app.
 */
data class FtpTrend(
    /** Oldest first. Empty when nothing has ever been recorded. */
    val points: List<FtpPoint> = emptyList()
) {

    val current: Int? get() = points.lastOrNull()?.watts

    /** The value before the most recent change, or null if it has never moved. */
    val previous: Int? get() = points.takeIf { it.size > 1 }?.let { it[it.size - 2].watts }

    /** The most recent *change*, or null if there has never been one. */
    val lastChange: FtpPoint? get() = points.takeIf { it.size > 1 }?.last()

    /** True once there is something to draw: two distinct values or more. */
    val hasMoved: Boolean get() = points.size > 1

    /** Positive, negative, or null when it has never moved. */
    val deltaWatts: Int?
        get() {
            val from = previous ?: return null
            val to = current ?: return null
            return to - from
        }

    /**
     * The lowest and highest values, for a chart's own scale.
     *
     * Padded outward by [PAD_WATTS] rather than fitted exactly, because a rider
     * whose FTP has moved once by five watts otherwise gets a sparkline that
     * fills the whole box with a cliff — technically true, and a wild
     * overstatement of five watts.
     */
    val range: IntRange?
        get() {
            if (points.isEmpty()) return null
            val low = points.minOf { it.watts }
            val high = points.maxOf { it.watts }
            return (low - PAD_WATTS)..(high + PAD_WATTS)
        }

    /**
     * Every *change*, newest first — which is one fewer than there are points
     * (16.3.1).
     *
     * The first point is not a change. It is the value the rider's profile
     * started with, or the one migration 7→8 seeded from a profile that already
     * existed, and listing it with a delta would invent an event: the number did
     * not move *to* 200, it began there. Same reason [lastChange] is null for a
     * history of one.
     *
     * Newest first because this is read as a record of what has happened rather
     * than drawn as a line — the opposite order to [points], and the same order
     * the ride history uses.
     */
    val changes: List<FtpChange>
        get() = points.zipWithNext { before, after ->
            FtpChange(
                from = before.watts,
                to = after.watts,
                atEpochMs = after.atEpochMs,
                source = after.source,
                workoutId = after.workoutId
            )
        }.asReversed()

    /** The value the rider started at, for the caption under a full-size chart. */
    val startedAt: FtpPoint? get() = points.firstOrNull()

    /**
     * The time the chart spans, in milliseconds, or null when there is nothing
     * to span.
     *
     * A trend of one point has no span, and a chart that divides by it would
     * either crash or draw every value on top of the last.
     */
    val spanMs: Long?
        get() {
            if (points.size < 2) return null
            return (points.last().atEpochMs - points.first().atEpochMs)
                .takeIf { it > 0L }
        }

    /**
     * The same span, but ending **now** rather than at the last change
     * (16.3.1).
     *
     * A chart whose axis stops on the day of the most recent change says the
     * record ends there. It does not: the current value is true today, and the
     * flat run from the last change to the right-hand edge is the rider's
     * answer to "how long have I been at this". It also stops the newest mark
     * being drawn half off the edge of the plot, which is a drawing accident
     * that reads as missing data.
     *
     * [nowEpochMs] is passed in rather than read from the clock, so this stays
     * pure and the behaviour is testable.
     */
    fun spanToNow(nowEpochMs: Long): Long? {
        if (points.isEmpty()) return null
        val first = points.first().atEpochMs
        // A clock behind the last recorded change — a device whose time moved
        // backwards — must not produce a negative span and mirror the chart.
        val end = maxOf(nowEpochMs, points.last().atEpochMs)
        return (end - first).takeIf { it > 0L }
    }

    private companion object {
        const val PAD_WATTS = 8
    }
}

/**
 * One movement of a rider's FTP: what it was, what it became, when, and who
 * moved it.
 *
 * A pair of values rather than a single value and a delta, because the delta is
 * derived from them and the two numbers are what a rider reads — "200 → 215"
 * says more than "+15" and cannot be misread as the value itself.
 */
data class FtpChange(
    val from: Int,
    val to: Int,
    val atEpochMs: Long,
    /** `FtpChangeSource` by name — the domain does not import the entity. */
    val source: String,
    /**
     * The ride that caused it, where there was one and it still exists.
     *
     * Null covers both "the rider typed it" and "the ride it came off has since
     * been deleted" — `workouts.ftp_history.workout_id` is `ON DELETE SET NULL`
     * (7.9.3), so a deleted ride costs the link and never the change.
     */
    val workoutId: String? = null
) {
    val deltaWatts: Int get() = to - from
    val isRise: Boolean get() = to > from
}

/** One recorded value, and when it became true. */
data class FtpPoint(
    val watts: Int,
    val atEpochMs: Long,
    /** `FtpChangeSource` by name — the domain does not import the entity. */
    val source: String,
    /** The ride that caused it, where there was one (7.9.3). */
    val workoutId: String? = null
)
