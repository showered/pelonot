package com.pelonot.domain.model

/**
 * A rival's cumulative score, second by second, for the live ghost (PLAN
 * 24.3.3–24.3.9).
 *
 * Built once from a finished ride's own samples, at ride start (or resume),
 * and then read by elapsed second as the ride in progress advances — never
 * recomputed and never touching the database again mid-ride.
 *
 * Same trapezoidal integration as [WorkoutAggregates], so the total at the
 * last point of this trace is the same number the rival's own ride recorded.
 *
 * **What is accumulated is [metric]'s business, not this class's** (24.3.14).
 * A race is one cumulative series against another aligned by elapsed second,
 * and that shape is the same whether the series is kilojoules or kilometres —
 * so everything below is written in terms of "value" and the unit lives at the
 * edges, in [RaceMetric] and in whatever formats it.
 */
data class RivalTrace(
    /** `(second, cumulativeValue)`, sorted by second, one entry per sample. */
    private val points: List<Pair<Int, Double>>,
    /** What the values are. Carried so a reader can never guess wrong. */
    val metric: RaceMetric = RaceMetric.Output
) {
    val isEmpty: Boolean get() = points.isEmpty()
    val finalSecond: Int get() = points.lastOrNull()?.first ?: 0
    val finalValue: Double get() = points.lastOrNull()?.second ?: 0.0

    /**
     * The rival's cumulative score at [second], or **null once the rival's
     * own ride had already ended** (24.3.6) — never extrapolated forward, the
     * same rule as `isStaleAt` and the gap-not-a-clamp family.
     *
     * Between two recorded seconds this is the cumulative total as of the
     * last one — not interpolated — because a gap in the rival's own series
     * is a gap in what they had actually done by then, not evidence for a
     * guess about it.
     */
    fun valueAt(second: Int): Double? {
        if (points.isEmpty() || second > finalSecond) return null
        var result = 0.0
        for ((sec, kj) in points) {
            if (sec > second) break
            result = kj
        }
        return result
    }

    /**
     * The live comparison at [second]: how far [yourValue] is ahead of
     * (positive) or behind (negative) the rival at the same point in the
     * class, in [metric]'s own units.
     *
     * [rivalFinished] is true once the rival's own ride has nothing left to
     * say — the gap freezes at their final total rather than the comparison
     * silently vanishing (24.3.6).
     */
    fun statusAt(second: Int, yourValue: Double, rivalName: String): RivalStatus {
        val atSecond = valueAt(second)
        return RivalStatus(
            rivalName = rivalName,
            gap = yourValue - (atSecond ?: finalValue),
            rivalFinished = atSecond == null,
            metric = metric
        )
    }

    companion object {
        // Borrowed from `WorkoutAggregates` rather than restated. The whole
        // promise of this class is that its final point equals the total the
        // rival's own ride recorded, and that promise is only kept if both
        // integrations use the same constants — including the gap clamp.
        private const val MAX_SAMPLE_GAP_SEC = WorkoutAggregates.MAX_SAMPLE_GAP_SEC
        private const val KM_PER_REVOLUTION = WorkoutAggregates.KM_PER_REVOLUTION
        private const val SECONDS_PER_MINUTE = WorkoutAggregates.SECONDS_PER_MINUTE

        /**
         * @param samples time-ordered `(second, power, cadence, heartRate)`
         *   tuples — the rival's own finished ride, exactly as
         *   [WorkoutAggregates.from] consumes them.
         * @param metric what to accumulate. Both branches are the same
         *   trapezoid over the same samples with the same gap clamp; only the
         *   quantity under the curve differs, which is the whole reason
         *   24.3.14's "structure it agnostically" cost nothing.
         */
        fun from(
            samples: List<MetricSample>,
            metric: RaceMetric = RaceMetric.Output
        ): RivalTrace {
            if (samples.isEmpty()) return RivalTrace(emptyList(), metric)

            val ordered = samples.sortedBy { it.second }
            var total = 0.0
            val points = mutableListOf(ordered.first().second to 0.0)

            ordered.zipWithNext { previous, current ->
                val dt = (current.second - previous.second).toDouble()
                    .coerceIn(0.0, MAX_SAMPLE_GAP_SEC)
                total += when (metric) {
                    // Joules, reported as kJ.
                    RaceMetric.Output ->
                        (previous.power + current.power) / 2.0 * dt / 1000.0
                    // Revolutions, reported as km.
                    RaceMetric.Distance ->
                        (previous.cadence + current.cadence) / 2.0 /
                            SECONDS_PER_MINUTE * KM_PER_REVOLUTION * dt
                }
                points += current.second to total
            }

            return RivalTrace(points, metric)
        }
    }
}

/** What the ride screen draws for the live ghost — one number (24.3.4). */
data class RivalStatus(
    val rivalName: String,
    /** Positive: you are ahead. Negative: you are behind. In [metric]'s units. */
    val gap: Double,
    val rivalFinished: Boolean,
    /** What the gap is measured in. Nothing may assume kilojoules (24.3.14). */
    val metric: RaceMetric = RaceMetric.Output
)
