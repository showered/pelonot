package com.pelonot.data.service

import com.pelonot.data.local.entity.WorkoutMetricEntity
import com.pelonot.domain.model.PowerZone

/**
 * Post-ride analysis: FTP estimation and effort/heart-rate decoupling.
 *
 * Pure Kotlin, no Android dependencies, so it is unit-testable.
 *
 * **RPE proposes nothing, and that is a decision (7.11.6).** There used to be a
 * `suggestFtpFromRpe` here that returned `currentFtp × 1.03` for a hard class
 * the rider had called easy, and [analyze] fed it straight into `proposedFtp`.
 * It never ran — the one production call site passed no `rpe`, because the
 * parameter had a default — so for the project's whole history the entirety of
 * auto-FTP that actually happened was the twenty-minute peak. It is deleted
 * rather than wired up or fenced, for three reasons:
 *
 * - **7.11.2 has since written down that RPE alone must not be enough.** A
 *   rider who rates every ride *Everything I had* is describing their effort,
 *   not their fitness. A function whose only behaviour is forbidden is not
 *   plumbing waiting to be connected.
 * - **It is the wrong shape for what will replace it.** 7.11's RPE half is a
 *   *trend across several rides*, which is a different computation from a check
 *   that reads one ride and returns. `detectBiometricDecoupling` is kept for
 *   exactly the opposite reason: it is the right shape facing the wrong way.
 * - **It could not have fired anyway.** The rider answers *How did that feel?*
 *   on the same screen that runs this analysis, so `rpe` is null at analysis
 *   time by construction. Any RPE-fed proposal needs a second pass triggered by
 *   the answer — a different flow, not an extra argument.
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
     * Runs every check and picks a single recommendation.
     *
     * A proposal is only surfaced when it is a meaningful improvement on the
     * current value — otherwise a rider gets an "FTP breakthrough!" dialog
     * after a recovery spin.
     *
     * **The twenty-minute peak is the only thing that can propose a number,
     * and that is the decision rather than the state of the work (7.11.6).**
     * [maxHr] takes no default on purpose: a signal that is optional at the
     * call site is a signal nobody notices is missing, which is precisely how
     * the decoupling check spent the project's whole history returning `false`
     * before reaching its own logic.
     */
    fun analyze(
        metrics: List<WorkoutMetricEntity>,
        currentFtp: Double,
        maxHr: Int?
    ): AnalysisResult {
        val fromPeak = estimateFtpFrom20MinPeak(metrics)
        val decoupling = detectBiometricDecoupling(metrics, currentFtp, maxHr)

        val isBreakthrough = fromPeak != null &&
            currentFtp > 0 &&
            fromPeak >= currentFtp * MIN_MEANINGFUL_GAIN

        return AnalysisResult(
            estimatedFtpFromPeak = fromPeak,
            biometricDecoupling = decoupling,
            proposedFtp = fromPeak.takeIf { isBreakthrough }
        )
    }

    /**
     * @property proposedFtp Non-null only when a breakthrough is worth showing
     *   the rider; the other fields are the raw evidence behind it.
     * @property biometricDecoupling Evidence the rider's FTP is set too *low*.
     *   **Nothing reads it yet** — it is the seed of 7.11's downward path
     *   facing the wrong way, and it is recorded honestly rather than left as a
     *   constant `false` so that the day something does read it, it is a
     *   measurement rather than a default.
     */
    data class AnalysisResult(
        val estimatedFtpFromPeak: Double? = null,
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

        /** Below a 2% gain, the change is inside the noise of the power model. */
        const val MIN_MEANINGFUL_GAIN = 1.02
    }
}
