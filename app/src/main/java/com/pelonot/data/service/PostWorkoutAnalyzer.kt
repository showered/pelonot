package com.pelonot.data.service

import com.pelonot.data.local.entity.WorkoutMetricEntity
import com.pelonot.domain.model.PowerZone

/**
 * Post-ride analysis: FTP estimation and effort/heart-rate decoupling.
 *
 * Pure Kotlin, no Android dependencies, so it is unit-testable.
 */
class PostWorkoutAnalyzer {

    /**
     * Estimated FTP from the best sustained 20-minute effort:
     * `FTP ≈ P₂₀ × 0.95`.
     *
     * Uses a true sliding window over a running sum, so it is O(n) rather than
     * the previous O(n²), and only considers windows that are actually a full
     * 20 minutes long. The old loop clamped the window end to the array
     * length, so near the tail it averaged progressively shorter slices — a
     * hard 90-second finish could produce a higher "20-minute average" than
     * any real 20-minute effort in the ride, inflating the FTP estimate.
     *
     * Returns null when the ride is shorter than the window.
     */
    fun estimateFtpFrom20MinPeak(metrics: List<WorkoutMetricEntity>): Double? {
        val window = TWENTY_MINUTES_SEC
        if (metrics.size < window) return null

        var runningSum = 0.0
        for (i in 0 until window) runningSum += metrics[i].power

        var bestSum = runningSum
        for (i in window until metrics.size) {
            runningSum += metrics[i].power - metrics[i - window].power
            if (runningSum > bestSum) bestSum = runningSum
        }

        val bestAverage = bestSum / window
        return bestAverage * FTP_FROM_20_MIN
    }

    /**
     * Detects aerobic decoupling: sustained threshold power at a heart rate
     * well below maximum, which suggests the rider's FTP is set too low.
     *
     * Requires more than [DECOUPLING_MIN_SEC] of Zone 4 riding, of which the
     * majority was below [HR_THRESHOLD_FRACTION] of max heart rate.
     */
    fun detectBiometricDecoupling(
        metrics: List<WorkoutMetricEntity>,
        ftp: Double,
        maxHr: Int?
    ): Boolean {
        if (maxHr == null || maxHr <= 0 || ftp <= 0.0) return false

        val zone4 = PowerZone.Z4.powerRange(ftp)
        val hrThreshold = maxHr * HR_THRESHOLD_FRACTION

        var zone4Seconds = 0
        var lowHrSeconds = 0

        for (metric in metrics) {
            if (metric.power !in zone4) continue
            zone4Seconds++
            val hr = metric.heartRate
            if (hr != null && hr < hrThreshold) lowHrSeconds++
        }

        return zone4Seconds > DECOUPLING_MIN_SEC &&
            lowHrSeconds > zone4Seconds * DECOUPLING_MAJORITY
    }

    /**
     * A hard class that felt easy is evidence the rider has improved.
     * Returns the proposed FTP, or null when no change is warranted.
     */
    fun suggestFtpFromRpe(rpe: Int?, isHardClass: Boolean, currentFtp: Double): Double? {
        if (rpe == null || !isHardClass || currentFtp <= 0.0) return null
        if (rpe > EASY_RPE_THRESHOLD) return null
        return currentFtp * RPE_FTP_BUMP
    }

    /**
     * Runs every check and picks a single recommendation.
     *
     * A proposal is only surfaced when it is a meaningful improvement on the
     * current value — otherwise a rider gets an "FTP breakthrough!" dialog
     * after a recovery spin.
     */
    fun analyze(
        metrics: List<WorkoutMetricEntity>,
        currentFtp: Double,
        maxHr: Int? = null,
        rpe: Int? = null,
        isHardClass: Boolean = false
    ): AnalysisResult {
        val fromPeak = estimateFtpFrom20MinPeak(metrics)
        val fromRpe = suggestFtpFromRpe(rpe, isHardClass, currentFtp)
        val decoupling = detectBiometricDecoupling(metrics, currentFtp, maxHr)

        val proposal = listOfNotNull(fromPeak, fromRpe).maxOrNull()
        val isBreakthrough = proposal != null &&
            currentFtp > 0 &&
            proposal >= currentFtp * MIN_MEANINGFUL_GAIN

        return AnalysisResult(
            estimatedFtpFromPeak = fromPeak,
            suggestedFtpFromRpe = fromRpe,
            biometricDecoupling = decoupling,
            proposedFtp = proposal.takeIf { isBreakthrough }
        )
    }

    /**
     * @property proposedFtp Non-null only when a breakthrough is worth showing
     *   the rider; the other fields are the raw evidence behind it.
     */
    data class AnalysisResult(
        val estimatedFtpFromPeak: Double? = null,
        val suggestedFtpFromRpe: Double? = null,
        val biometricDecoupling: Boolean = false,
        val proposedFtp: Double? = null
    ) {
        val hasBreakthrough: Boolean get() = proposedFtp != null
    }

    companion object {
        const val TWENTY_MINUTES_SEC = 20 * 60

        /** Coggan's standard correction from a 20-minute test to FTP. */
        const val FTP_FROM_20_MIN = 0.95

        const val HR_THRESHOLD_FRACTION = 0.80
        const val DECOUPLING_MIN_SEC = 600
        const val DECOUPLING_MAJORITY = 0.5

        const val EASY_RPE_THRESHOLD = 4
        const val RPE_FTP_BUMP = 1.03

        /** Below a 2% gain, the change is inside the noise of the power model. */
        const val MIN_MEANINGFUL_GAIN = 1.02
    }
}
