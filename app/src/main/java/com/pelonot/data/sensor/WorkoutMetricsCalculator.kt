package com.pelonot.data.sensor

import com.pelonot.domain.model.PowerZone

/**
 * Derives cumulative and rolling workout metrics from the raw [SensorReading]
 * stream.
 *
 * Everything here is pure Kotlin with no Android dependencies so it can be
 * covered by fast JVM unit tests.
 *
 * Total output is the integral of power over time, `∫P dt`, approximated with
 * the trapezoidal rule between consecutive samples. The previous version
 * multiplied each trapezoid by `powerHistory.size - 1` instead of the elapsed
 * time between samples, so reported energy grew with the square of ride
 * duration — a 45-minute ride over-reported by three orders of magnitude.
 */
class WorkoutMetricsCalculator {

    private data class Sample(val timestampMs: Long, val power: Double, val cadence: Double)

    private val samples = ArrayDeque<Sample>()
    private var totalEnergyJoules = 0.0
    private var totalDistanceKm = 0.0
    private var lastSample: Sample? = null

    /**
     * Folds a new reading into the running totals and returns the current
     * metric snapshot.
     */
    fun processReading(reading: SensorReading, ftpWatts: Int): CalculatedMetrics {
        val sample = Sample(reading.timestampMs, reading.powerWatts, reading.cadenceRpm)
        val previous = lastSample

        if (previous != null) {
            // Clamp the step so a backgrounded app or a stalled sensor cannot
            // integrate a long gap as though the rider pedalled through it.
            val dtSec = ((sample.timestampMs - previous.timestampMs) / 1000.0)
                .coerceIn(0.0, MAX_SAMPLE_GAP_SEC)

            totalEnergyJoules += (previous.power + sample.power) / 2.0 * dtSec

            val avgCadence = (previous.cadence + sample.cadence) / 2.0
            totalDistanceKm += (avgCadence / SECONDS_PER_MINUTE) * KM_PER_REVOLUTION * dtSec
        }

        lastSample = sample
        samples.addLast(sample)
        trimTo(sample.timestampMs)

        return CalculatedMetrics(
            totalOutputKj = totalEnergyJoules / 1000.0,
            distanceKm = totalDistanceKm,
            avgPower1s = averagePowerOver(1),
            avgPower5s = averagePowerOver(5),
            avgPower30s = averagePowerOver(30),
            avgCadence30s = averageCadenceOver(30),
            currentPowerZone = PowerZone.forPower(reading.powerWatts, ftpWatts.toDouble()),
            currentFtpPercentage = if (ftpWatts > 0) {
                reading.powerWatts / ftpWatts * 100.0
            } else {
                0.0
            }
        )
    }

    /** Mean power over the trailing [windowSec] seconds of samples. */
    fun averagePowerOver(windowSec: Int): Double = averageOver(windowSec) { it.power }

    /** Mean cadence over the trailing [windowSec] seconds of samples. */
    fun averageCadenceOver(windowSec: Int): Double = averageOver(windowSec) { it.cadence }

    private fun averageOver(windowSec: Int, selector: (Sample) -> Double): Double {
        val newest = samples.lastOrNull() ?: return 0.0
        val cutoff = newest.timestampMs - windowSec * 1000L
        // Inclusive of the lower edge, so a 1s window over 1 Hz samples still
        // sees the two readings that bracket that second.
        var sum = 0.0
        var count = 0
        for (sample in samples) {
            if (sample.timestampMs >= cutoff) {
                sum += selector(sample)
                count++
            }
        }
        return if (count == 0) 0.0 else sum / count
    }

    private fun trimTo(nowMs: Long) {
        val cutoff = nowMs - ROLLING_WINDOW_SEC * 1000L
        while (samples.size > 1 && samples.first().timestampMs < cutoff) {
            samples.removeFirst()
        }
    }

    /** Clears all accumulated state. Call when starting a new workout. */
    fun reset() {
        samples.clear()
        totalEnergyJoules = 0.0
        totalDistanceKm = 0.0
        lastSample = null
    }

    companion object {
        private const val SECONDS_PER_MINUTE = 60.0
        private const val ROLLING_WINDOW_SEC = 30

        /**
         * Rough distance model: one flywheel revolution is treated as 2.1 m of
         * road. Peloton's own "distance" is a similar fiction — there is no
         * wheel — so this exists for comparability between rides, not accuracy.
         */
        private const val KM_PER_REVOLUTION = 0.0021

        /** Longest gap between samples that still counts as continuous riding. */
        private const val MAX_SAMPLE_GAP_SEC = 5.0
    }
}

/**
 * A snapshot of everything derived from the telemetry stream at one instant.
 *
 * @property totalOutputKj Cumulative energy output in kilojoules.
 * @property distanceKm Cumulative estimated distance. This is a running total;
 *   the previous version returned only the latest sample's contribution while
 *   naming it as a total.
 * @property currentFtpPercentage Instantaneous power as a percentage of FTP.
 */
data class CalculatedMetrics(
    val totalOutputKj: Double,
    val distanceKm: Double,
    val avgPower1s: Double,
    val avgPower5s: Double,
    val avgPower30s: Double,
    val avgCadence30s: Double,
    val currentPowerZone: PowerZone,
    val currentFtpPercentage: Double
)
