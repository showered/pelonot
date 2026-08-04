package com.pelonot.domain.model

/**
 * Ride totals recomputed from a stored metric series.
 *
 * The live path keeps these running in memory as the ride happens. This is the
 * cold path: a ride the app was killed in the middle of has a `workouts` row
 * full of zeros and a complete `workout_metrics` series behind it, and the only
 * way to recover it is to add the series back up.
 *
 * Kept deliberately consistent with
 * [com.pelonot.data.sensor.WorkoutMetricsCalculator] — same trapezoidal
 * integration, same 5-second gap clamp, same revolution-to-distance fiction —
 * so a recovered ride is comparable with one that finished normally rather
 * than being a differently-shaped number with the same name.
 */
data class WorkoutAggregates(
    val durationSec: Int = 0,
    val totalOutputKj: Double = 0.0,
    val distanceKm: Double = 0.0,
    val avgPower: Double = 0.0,
    val avgCadence: Double = 0.0,
    val avgHeartRate: Int? = null,
    val sampleCount: Int = 0,
    /**
     * How many of [sampleCount] carried a heart rate.
     *
     * Counted separately for the same reason `WorkoutSession` counts it
     * separately: a strap that connects thirty seconds in contributes to far
     * fewer samples than the bike does. It exists so a resumed ride (8.3d) can
     * restore the running mean with the weight it was actually built at —
     * folding new readings in against the total sample count would under-weight
     * every one of them.
     */
    val heartRateSampleCount: Int = 0,
    /**
     * The unrounded mean behind [avgHeartRate].
     *
     * [avgHeartRate] is a whole bpm for display and for the `avg_hr` column.
     * Resuming (8.3d) has to carry the running mean *forward* and keep folding
     * into it, and a mean that is re-rounded every time it is restored drifts —
     * which is the failure `WorkoutSession.avgHeartRateBpm` already exists to
     * avoid, one layer up.
     */
    val avgHeartRateExact: Double? = null
) {
    val isEmpty: Boolean get() = sampleCount == 0

    companion object {
        // Not private, and deliberately so: `RivalTrace` integrates the same
        // two quantities over the same samples to build a live ghost, and a
        // second copy of any of these three numbers is a distance that
        // disagrees with the distance the ride recorded. Same family as the
        // `avg_*` trap — one quantity, two derivations, one of them drifting.
        internal const val SECONDS_PER_MINUTE = 60.0
        internal const val KM_PER_REVOLUTION = 0.0021
        internal const val MAX_SAMPLE_GAP_SEC = 5.0

        /**
         * @param samples time-ordered `(second, power, cadence, heartRate)`
         *   tuples. Heart rate is nullable throughout: null means no strap, and
         *   averaging it in as zero would libel the rider's cardiovascular
         *   system.
         */
        fun from(samples: List<MetricSample>): WorkoutAggregates {
            if (samples.isEmpty()) return WorkoutAggregates()

            val ordered = samples.sortedBy { it.second }
            var energyJoules = 0.0
            var distanceKm = 0.0

            ordered.zipWithNext { previous, current ->
                val dt = (current.second - previous.second).toDouble()
                    .coerceIn(0.0, MAX_SAMPLE_GAP_SEC)
                energyJoules += (previous.power + current.power) / 2.0 * dt
                distanceKm += ((previous.cadence + current.cadence) / 2.0 / SECONDS_PER_MINUTE) *
                    KM_PER_REVOLUTION * dt
            }

            val heartRates = ordered.mapNotNull { it.heartRate }
            val meanHeartRate = if (heartRates.isEmpty()) null else heartRates.average()

            return WorkoutAggregates(
                durationSec = ordered.last().second,
                totalOutputKj = energyJoules / 1000.0,
                distanceKm = distanceKm,
                avgPower = ordered.sumOf { it.power } / ordered.size,
                avgCadence = ordered.sumOf { it.cadence } / ordered.size,
                avgHeartRate = meanHeartRate?.toInt(),
                sampleCount = ordered.size,
                heartRateSampleCount = heartRates.size,
                avgHeartRateExact = meanHeartRate
            )
        }
    }
}

/** One stored per-second sample, independent of the Room entity. */
data class MetricSample(
    val second: Int,
    val power: Double,
    val cadence: Double,
    val heartRate: Int?
)
