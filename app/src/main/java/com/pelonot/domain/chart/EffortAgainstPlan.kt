package com.pelonot.domain.chart

import com.pelonot.domain.model.HeartRateZone
import com.pelonot.domain.model.PowerZone

/**
 * What the class asked for, against what the rider's heart says they did
 * (PLAN 21.6.3).
 *
 * The owner's note is the whole of it: *"if an endurance ride the rider spent
 * most of the time in the top 1 or 2 zones, then they clearly found that ride
 * more difficult than it should have been, you don't even need to ask!"* The
 * class writes down the effort it prescribed and the strap writes down the
 * effort that was made, and the gap between the two is information nobody has
 * to be asked for.
 *
 * **This is an observation about a ride, not a claim about a rider**, which is
 * what makes it 21.6.3 rather than 21.6.1 or 21.6.2. Nothing here is written to
 * the rider's record, nothing prefills an answer they are about to give, and
 * nothing feeds an FTP. It is one sentence under a chart, and it may be wrong
 * without costing anybody anything.
 *
 * ### Why the two scales are compared coarsely, and never zone for zone
 *
 * [HeartRateZone] says plainly that it is **not** [PowerZone] with five entries:
 * different conventions, different boundaries, and telling a rider that H4 and
 * Z4 are the same thing would be false. So this does not map one onto the other.
 * It asks each scale the same *coarse* question in that scale's own terms —
 * **how much of this was hard?** — where hard is [PowerZone.Z4] and above for
 * the prescription and [HeartRateZone.H4] and above for the heart, each being
 * the point its own convention calls threshold. Two fractions, compared with a
 * deliberately blunt tolerance.
 *
 * ### Why it takes whole rides only
 *
 * 21.6.4, and it is not negotiable: heart rate lags effort by 30–60 seconds and
 * drifts upward across a long ride at constant power, so it is a good signal
 * about *a ride* and a poor one about *an interval*. Over a whole class the lag
 * roughly cancels — the heart is low into the first hard block and high through
 * the recovery after it — and over sixty seconds it does not cancel at all.
 * Hence [MIN_HEART_SECONDS] and [MIN_COVERAGE], and hence a tolerance of twenty
 * percentage points rather than five.
 *
 * Null is the ordinary answer. A free ride was not asked for anything, a rider
 * with no strap said nothing, and a strap that heard four minutes of forty
 * described a quarter of a ride rather than the ride.
 */
data class EffortAgainstPlan(
    val verdict: Verdict,
    /** Seconds the class prescribed at [PowerZone.Z4] or above. */
    val prescribedHardSeconds: Int,
    /** Seconds the class prescribed in total, clipped to what was ridden. */
    val prescribedSeconds: Int,
    /** Seconds the heart spent at [HeartRateZone.H4] or above. */
    val heartHardSeconds: Int,
    /** Seconds a heart rate was reported for, which is what the above is out of. */
    val heartSeconds: Int
) {
    enum class Verdict {
        /** The heart was up high for meaningfully longer than the class asked. */
        Harder,

        /** Neither, within a tolerance wide enough to survive the lag. */
        AsAsked,

        /** The heart stayed down for meaningfully longer than the class asked. */
        Easier
    }

    companion object {

        /**
         * The comparison, or null when there is nothing honest to compare.
         *
         * [plan] is read for what it **prescribed**, not for what was ridden
         * inside the band: a condensed ride (23.4) keeps its blocks and loses
         * its compliance, and this question survives a trim for exactly that
         * reason — the heart's own counts come from `distributions_json`.
         */
        fun of(plan: PrescribedPlan, heart: TimeInHeartRateZone): EffortAgainstPlan? {
            val prescribedSeconds = plan.segments.sumOf { it.durationSec }
            if (prescribedSeconds <= 0) return null

            val heartSeconds = heart.totalSeconds
            if (heartSeconds < MIN_HEART_SECONDS) return null
            // A strap that heard half the ride describes half the ride. The
            // fraction below would still be arithmetically true and would read
            // as the shape of the whole thing — the same trap the card's
            // coverage caption exists for (21.4.1).
            if (heartSeconds < heart.recordedSeconds * MIN_COVERAGE) return null

            val prescribedHardSeconds = plan.segments
                .filter { it.zone.number >= HARD_POWER_ZONE.number }
                .sumOf { it.durationSec }
            val heartHardSeconds = heart.secondsByZone
                .filterKeys { it.number >= HARD_HEART_ZONE.number }
                .values.sum()

            val prescribedFraction = prescribedHardSeconds.toDouble() / prescribedSeconds
            val heartFraction = heartHardSeconds.toDouble() / heartSeconds

            val verdict = when {
                heartFraction - prescribedFraction >= TOLERANCE -> Verdict.Harder
                prescribedFraction - heartFraction >= TOLERANCE -> Verdict.Easier
                else -> Verdict.AsAsked
            }

            return EffortAgainstPlan(
                verdict = verdict,
                prescribedHardSeconds = prescribedHardSeconds,
                prescribedSeconds = prescribedSeconds,
                heartHardSeconds = heartHardSeconds,
                heartSeconds = heartSeconds
            )
        }

        /** Threshold, in each scale's own convention. Not equated (see above). */
        private val HARD_POWER_ZONE = PowerZone.Z4
        private val HARD_HEART_ZONE = HeartRateZone.H4

        /**
         * Twenty percentage points, and deliberately blunt.
         *
         * The failure that costs something is telling a rider they overcooked an
         * easy ride when they did not, so the tolerance is set wide enough that
         * the lag, cardiac drift, a warm room and a bad night's sleep together
         * do not reach it. A gap this size is a different ride, not a different
         * day.
         */
        private const val TOLERANCE = 0.20

        /** Ten minutes of heart rate before a whole-ride claim means anything. */
        private const val MIN_HEART_SECONDS = 10 * 60

        /** And it has to cover at least half of what the ride recorded. */
        private const val MIN_COVERAGE = 0.5
    }
}
