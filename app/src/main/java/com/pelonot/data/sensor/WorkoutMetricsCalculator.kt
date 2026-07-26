package com.pelonot.data.sensor

import android.util.Log
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Calculates derived workout metrics from raw SensorReading data.
 *
 * Responsibilities:
 * - Total Output (kJ): Integrate power over time using discrete 1-second samples
 * - Rolling Averages: 1s, 5s, 30s windows for power and cadence
 * - Distance Estimation: Derived from cadence + resistance model
 * - Current Power Zone: Based on FTP and Coggan 7-zone model
 *
 * Formula for Total Output:
 *   Output(kJ) = sum(P_i for i in 1..T) / 1000
 *   where P_i is power in watts at second i, T is total seconds
 */
class WorkoutMetricsCalculator {

    companion object {
        private const val TAG = "WorkoutMetricsCalculator"
        private const val MS_PER_SECOND = 1000L
        private const val SECONDS_PER_MINUTE = 60.0
        private const val WHEEL_CIRCUMFERENCE_KM = 0.0021 // 2.1 meters in km

        /** Coggan 7-zone boundaries (% of FTP) */
        val ZONE_BOUNDARIES = listOf(
            0.0 to 0.55,   // Z1: Active Recovery
            0.56 to 0.75,  // Z2: Endurance
            0.76 to 0.90,  // Z3: Tempo
            0.91 to 1.05,  // Z4: Lactate Threshold
            1.06 to 1.20,  // Z5: VO2 Max
            1.21 to 1.50,  // Z6: Anaerobic Capacity
            1.51 to 99.0   // Z7: Neuromuscular Power
        )
    }

    // ── Internal state ──────────────────────────────────────────────
    private val powerHistory = mutableListOf<Double>()
    private val cadenceHistory = mutableListOf<Double>()
    private val timestampHistory = mutableListOf<Long>()
    private var totalEnergyJoules = 0.0 // Running sum of power samples

    /**
     * Process a new sensor reading and return updated metrics.
     *
     * @param reading The current SensorReading from SensorRepository
     * @param ftpWatts The user's Functional Threshold Power
     * @return CalculatedMetrics with all derived values
     */
    fun processReading(reading: SensorReading, ftpWatts: Int): CalculatedMetrics {
        val now = reading.timestampMs

        // Add to history
        powerHistory.add(reading.powerWatts)
        cadenceHistory.add(reading.cadenceRpm)
        timestampHistory.add(now)

        // Integrate power (trapezoidal rule for better accuracy)
        if (powerHistory.size >= 2) {
            val dtSec = (powerHistory.size - 1).toDouble() // 1-second samples
            val avgPower = (powerHistory[powerHistory.size - 2] + reading.powerWatts) / 2.0
            totalEnergyJoules += avgPower * dtSec
        } else {
            totalEnergyJoules += reading.powerWatts
        }

        // Trim history to last 30 seconds for rolling averages
        val cutoff = now - 30_000L
        while (timestampHistory.isNotEmpty() && timestampHistory.first() < cutoff) {
            powerHistory.removeAt(0)
            cadenceHistory.removeAt(0)
            timestampHistory.removeAt(0)
        }

        // Calculate rolling averages
        val avgPower1s = rollingAverage(powerHistory, 1)
        val avgPower5s = rollingAverage(powerHistory, 5)
        val avgPower30s = rollingAverage(powerHistory, 30)
        val avgCadence30s = rollingAverage(cadenceHistory, 30)

        // Calculate distance (estimated from cadence + resistance model)
        val distanceKm = estimateDistance(reading.cadenceRpm, reading.resistancePercent)

        // Calculate current power zone
        val powerZone = getPowerZone(reading.powerWatts, ftpWatts.toDouble())

        // Total output in kJ
        val totalOutputKj = totalEnergyJoules / 1000.0

        return CalculatedMetrics(
            totalOutputKj = totalOutputKj,
            avgPower1s = avgPower1s,
            avgPower5s = avgPower5s,
            avgPower30s = avgPower30s,
            avgCadence30s = avgCadence30s,
            distanceKm = distanceKm,
            currentPowerZone = powerZone,
            currentFtpPercentage = if (ftpWatts > 0) reading.powerWatts / ftpWatts * 100.0 else 0.0
        )
    }

    /**
     * Calculate rolling average over the last N samples.
     */
    private fun rollingAverage(history: List<Double>, windowSize: Int): Double {
        if (history.isEmpty()) return 0.0
        val start = max(0, history.size - windowSize)
        val window = history.subList(start, history.size)
        return window.average()
    }

    /**
     * Estimate distance traveled using cadence and resistance.
     *
     * Uses a simplified model: distance = cadence * time * wheel_circumference
     * adjusted by a resistance factor (higher resistance = slightly less distance
     * per revolution due to slip, but this is negligible for estimation).
     */
    private fun estimateDistance(cadenceRpm: Double, resistancePercent: Double): Double {
        if (cadenceRpm <= 0.0) return 0.0

        // Each cadence tick represents one flywheel revolution
        // Distance per revolution = wheel circumference
        // We use the last sample's contribution (per-second)
        val revsPerSecond = cadenceRpm / SECONDS_PER_MINUTE
        val distanceThisSecondKm = revsPerSecond * WHEEL_CIRCUMFERENCE_KM

        // Accumulate (this is a simplified per-sample estimate)
        // In practice, the service accumulates this over time
        return distanceThisSecondKm
    }

    /**
     * Get the Coggan power zone (1-7) for a given power and FTP.
     */
    fun getPowerZone(powerWatts: Double, ftpWatts: Double): Int {
        if (ftpWatts <= 0.0 || powerWatts <= 0.0) return 1

        val ratio = powerWatts / ftpWatts
        for ((index, range) in ZONE_BOUNDARIES.withIndex()) {
            if (ratio >= range.first && ratio <= range.second) {
                return index + 1
            }
        }
        return 7 // Above Z7
    }

    /**
     * Get the zone name for a given zone number.
     */
    fun getZoneName(zone: Int): String {
        return when (zone) {
            1 -> "Active Recovery"
            2 -> "Endurance"
            3 -> "Tempo"
            4 -> "Lactate Threshold"
            5 -> "VO2 Max"
            6 -> "Anaerobic Capacity"
            7 -> "Neuromuscular Power"
            else -> "Unknown"
        }
    }

    /**
     * Reset all accumulated state (call when starting a new workout).
     */
    fun reset() {
        powerHistory.clear()
        cadenceHistory.clear()
        timestampHistory.clear()
        totalEnergyJoules = 0.0
        Log.d(TAG, "Metrics calculator reset")
    }
}

/**
 * All calculated metrics for the current workout state.
 *
 * @property totalOutputKj Total energy output in kilojoules
 * @property avgPower1s Rolling 1-second average power (watts)
 * @property avgPower5s Rolling 5-second average power (watts)
 * @property avgPower30s Rolling 30-second average power (watts)
 * @property avgCadence30s Rolling 30-second average cadence (RPM)
 * @property distanceKm Estimated distance traveled (km)
 * @property currentPowerZone Current Coggan power zone (1-7)
 * @property currentFtpPercentage Current power as percentage of FTP
 */
data class CalculatedMetrics(
    val totalOutputKj: Double,
    val avgPower1s: Double,
    val avgPower5s: Double,
    val avgPower30s: Double,
    val avgCadence30s: Double,
    val distanceKm: Double,
    val currentPowerZone: Int,
    val currentFtpPercentage: Double
)
