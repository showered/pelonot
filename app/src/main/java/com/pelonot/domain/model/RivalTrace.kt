package com.pelonot.domain.model

/**
 * A rival's cumulative output, second by second, for the live ghost (PLAN
 * 24.3.3–24.3.9).
 *
 * Built once from a finished ride's own samples, at ride start (or resume),
 * and then read by elapsed second as the ride in progress advances — never
 * recomputed and never touching the database again mid-ride.
 *
 * Same trapezoidal integration as [WorkoutAggregates], so the total at the
 * last point of this trace is the same number the rival's own ride recorded.
 */
data class RivalTrace(
    /** `(second, cumulativeKj)`, sorted by second, one entry per sample. */
    private val points: List<Pair<Int, Double>>
) {
    val isEmpty: Boolean get() = points.isEmpty()
    val finalSecond: Int get() = points.lastOrNull()?.first ?: 0
    val finalKj: Double get() = points.lastOrNull()?.second ?: 0.0

    /**
     * The rival's cumulative output at [second], or **null once the rival's
     * own ride had already ended** (24.3.6) — never extrapolated forward, the
     * same rule as `isStaleAt` and the gap-not-a-clamp family.
     *
     * Between two recorded seconds this is the cumulative total as of the
     * last one — not interpolated — because a gap in the rival's own series
     * is a gap in what they had actually done by then, not evidence for a
     * guess about it.
     */
    fun kjAt(second: Int): Double? {
        if (points.isEmpty() || second > finalSecond) return null
        var result = 0.0
        for ((sec, kj) in points) {
            if (sec > second) break
            result = kj
        }
        return result
    }

    /**
     * The live comparison at [second]: how far [yourKj] is ahead of (positive)
     * or behind (negative) the rival at the same point in the class.
     *
     * [rivalFinished] is true once the rival's own ride has nothing left to
     * say — the gap freezes at their final total rather than the comparison
     * silently vanishing (24.3.6).
     */
    fun statusAt(second: Int, yourKj: Double, rivalName: String): RivalStatus {
        val atSecond = kjAt(second)
        return RivalStatus(
            rivalName = rivalName,
            gapKj = yourKj - (atSecond ?: finalKj),
            rivalFinished = atSecond == null
        )
    }

    companion object {
        private const val MAX_SAMPLE_GAP_SEC = 5.0

        /**
         * @param samples time-ordered `(second, power, cadence, heartRate)`
         *   tuples — the rival's own finished ride, exactly as
         *   [WorkoutAggregates.from] consumes them.
         */
        fun from(samples: List<MetricSample>): RivalTrace {
            if (samples.isEmpty()) return RivalTrace(emptyList())

            val ordered = samples.sortedBy { it.second }
            var energyJoules = 0.0
            val points = mutableListOf(ordered.first().second to 0.0)

            ordered.zipWithNext { previous, current ->
                val dt = (current.second - previous.second).toDouble()
                    .coerceIn(0.0, MAX_SAMPLE_GAP_SEC)
                energyJoules += (previous.power + current.power) / 2.0 * dt
                points += current.second to energyJoules / 1000.0
            }

            return RivalTrace(points)
        }
    }
}

/** What the ride screen draws for the live ghost — one number (24.3.4). */
data class RivalStatus(
    val rivalName: String,
    /** Positive: you are ahead. Negative: you are behind. */
    val gapKj: Double,
    val rivalFinished: Boolean
)
