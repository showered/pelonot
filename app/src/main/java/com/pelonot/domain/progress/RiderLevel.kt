package com.pelonot.domain.progress

import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Everything a rider has ever done on this bike, in three numbers (26.4.1).
 *
 * Lifetime rather than windowed, and that is the whole point: every other
 * figure in this app is a window — the last 30 days (`RidingWindow`), the last
 * seventeen weeks (`RidingHistory`), the last ten rides (`ClassToRide`) — and a
 * window is a thing a rider can fall out of. This one cannot go down.
 *
 * **A trimmed ride still counts in full.** 23.4 condenses `workout_metrics`,
 * not `workouts`, and all three of these columns live on the ride row. That is
 * the opposite of time-in-zone, which cannot be recomputed once the seconds are
 * gone — so a rider who turns retention on does not lose levels.
 */
data class RidingTotals(
    val rides: Int = 0,
    val durationSec: Long = 0,
    val outputKj: Double = 0.0
) {
    val minutes: Double get() = durationSec / 60.0

    val hasAnything: Boolean get() = rides > 0
}

/**
 * A dimensionless number for a rider, built on volume rather than on fitness
 * (26.4, the owner's note of 4 August 2026 — *"a bit like 'lvl' in video
 * games"*).
 *
 * **Why this is not the FTP with a different label.** The note arrived after
 * 26.1.1 took *"150 W FTP"* off the profile tile and the owner found the screen
 * "SO much better" — but then nothing anywhere said `200`. The obvious fix is
 * to put the FTP back somewhere prettier, and it is wrong, because a level in a
 * game has three properties the FTP has none of:
 *
 * 1. **It only ever goes up.** The FTP falls when a rider is ill, off the bike,
 *    or has a bad day on the test that proposed it — and Phase 7 moves it *by
 *    itself*, so a rider could be demoted by an algorithm while they slept.
 * 2. **It is earned by playing**, so it accumulates. The FTP is a measurement.
 * 3. **It is comparable without a unit.** Raw watts are not fair between
 *    bodies, which this app already knows — the household board ranks on
 *    kilojoules and kJ/kg exists precisely so two housemates are not compared
 *    by mass.
 *
 * So this is a *second* quantity beside the FTP and never a replacement for it
 * (26.4.5). The FTP keeps its two screens, the zone ladder and every chart's
 * bands.
 *
 * **What it is allowed to claim, and it is one thing: how much you have
 * ridden** (26.4.2). A rider at level 12 beside a rider at level 30 has not
 * been told they are less fit — they have been told the other one has ridden
 * more, which is true and is the only thing this number knows. Nothing that
 * draws it may caption it as fitness, form, or progress.
 *
 * **The kilojoule term is the one place fairness is even slightly in
 * question**, since a bigger rider producing more watts earns points faster.
 * It is deliberately small: on a typical thirty-minute, 200 kJ ride it is 20 of
 * 70 points, and levels are the square root of points — so a rider producing
 * *twice* the power of another, over identical time on the bike, is about 14%
 * ahead in level rather than twice ahead. Getting on the bike is what this
 * rewards, which is 22.5.2's argument about the streak arriving on a badge.
 */
data class RiderLevel(
    val level: Int,
    /** Lifetime points, the raw accumulation the level is a reading of. */
    val points: Int,
    /** Points banked since reaching [level]. */
    val pointsIntoLevel: Int,
    /** Points from [level] to the next one — the width of the current step. */
    val pointsPerLevel: Int,
    /** The totals it was computed from, so a caller can say what it counted. */
    val totals: RidingTotals
) {
    /** How far through the current level, 0f–1f, for a ring or a bar. */
    val progress: Float
        get() = if (pointsPerLevel <= 0) 0f else (pointsIntoLevel.toFloat() / pointsPerLevel).coerceIn(0f, 1f)

    /** True before the first finished ride — level 1 is the start, not an achievement. */
    val isUnstarted: Boolean get() = !totals.hasAnything

    companion object {
        /**
         * A ride is worth showing up for, whatever it was.
         *
         * The largest of the three terms for a short ride, on purpose: the
         * quantity the app should reward is *getting on the bike*, because it
         * is the one the rider controls (22.5.2). Twenty points is a third of a
         * typical ride's total, so ten short rides beat one very long one.
         */
        const val POINTS_PER_RIDE = 20.0

        /** One point a minute — the plainest possible reading of "how much". */
        const val POINTS_PER_MINUTE = 1.0

        /**
         * Ten kilojoules to the point, which is what keeps the effort term
         * small enough to be fair between bodies. See the class KDoc.
         */
        const val POINTS_PER_KJ = 0.1

        /**
         * Points in the first level, and every level after it is this times the
         * square of how far up the ladder it is.
         *
         * Seventy is one typical ride — thirty minutes at 200 kJ — so **the
         * first ride a rider ever finishes takes them to level 2**, which is
         * the only moment on this curve where a single ride can move the
         * number and is exactly the moment it should.
         *
         * After that it slows by construction — a level costs the square of
         * how far up it is, so level *n* is around (n−1)² typical rides: level
         * 3 at four, level 4 at nine, level 11 at a hundred. A rider who rides
         * once a week is about level 8 after a year and 13 after three; level
         * 30 is 841 rides, which is five years of riding five times a week.
         * Nobody is ever demoted for a bad winter, and nobody arrives anywhere
         * quickly.
         */
        const val POINTS_PER_FIRST_LEVEL = 70.0

        /** The level of a rider who has never finished a ride. */
        const val FIRST_LEVEL = 1

        /**
         * Lifetime points, kept as a whole number because it is shown to nobody
         * as a decimal and a level boundary should not depend on rounding.
         */
        fun pointsFor(totals: RidingTotals): Int {
            if (totals.rides <= 0) return 0
            val raw = totals.rides * POINTS_PER_RIDE +
                totals.minutes.coerceAtLeast(0.0) * POINTS_PER_MINUTE +
                totals.outputKj.coerceAtLeast(0.0) * POINTS_PER_KJ
            // Long first: a household with a decade of riding is still far
            // inside Int, but a corrupt duration should clamp rather than wrap.
            return raw.coerceIn(0.0, Int.MAX_VALUE.toDouble()).toLong().toInt()
        }

        /** The lifetime points needed to reach [level]. */
        fun pointsToReach(level: Int): Int {
            val steps = (level - FIRST_LEVEL).coerceAtLeast(0)
            return (POINTS_PER_FIRST_LEVEL * steps * steps).toLong().toInt()
        }

        fun of(totals: RidingTotals): RiderLevel {
            val points = pointsFor(totals)
            // The inverse of pointsToReach: level = 1 + floor(sqrt(points / 70)).
            val level = FIRST_LEVEL + floor(sqrt(points / POINTS_PER_FIRST_LEVEL)).toInt()
            val floorPoints = pointsToReach(level)
            val nextPoints = pointsToReach(level + 1)
            return RiderLevel(
                level = level,
                points = points,
                pointsIntoLevel = points - floorPoints,
                pointsPerLevel = nextPoints - floorPoints,
                totals = totals
            )
        }
    }
}
