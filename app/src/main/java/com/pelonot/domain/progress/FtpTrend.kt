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

    private companion object {
        const val PAD_WATTS = 8
    }
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
