package com.pelonot.data.service

import com.pelonot.data.local.entity.WorkoutMetricEntity
import kotlin.math.max

/**
 * Analyzes workout data to detect FTP breakthroughs and other metrics.
 */
class PostWorkoutAnalyzer {
    
    /**
     * Calculate estimated FTP from 20-minute peak power.
     * FTP = P_20min_max × 0.95
     */
    fun calculateFtpFrom20MinPeak(metrics: List<WorkoutMetricEntity>): Double? {
        if (metrics.size < 1200) return null // Need at least 20 minutes of data
        
        // Calculate rolling 20-minute average (1200 seconds)
        var max20MinAvg = 0.0
        for (i in metrics.indices) {
            val windowEnd = minOf(i + 1200, metrics.size)
            val window = metrics.subList(i, windowEnd)
            val avgPower = window.sumOf { it.power } / window.size
            max20MinAvg = max(max20MinAvg, avgPower)
        }
        
        return max20MinAvg * 0.95
    }
    
    /**
     * Detect biometric decoupling.
     * Returns true if Zone 4 power sustained >10 min while HR <80% max HR.
     */
    fun detectBiometricDecoupling(
        metrics: List<WorkoutMetricEntity>,
        ftp: Double,
        maxHr: Int?
    ): Boolean {
        if (maxHr == null) return false
        
        val zone4MinPower = ftp * 0.91
        val zone4MaxPower = ftp * 1.05
        val hrThreshold = maxHr * 0.8
        
        var zone4Seconds = 0
        var decouplingSeconds = 0
        
        for (metric in metrics) {
            val inZone4 = metric.power in zone4MinPower..zone4MaxPower
            val lowHr = metric.heartRateBpm != null && metric.heartRateBpm < hrThreshold
            
            if (inZone4) {
                zone4Seconds++
                if (lowHr) {
                    decouplingSeconds++
                }
            }
        }
        
        // Check if >10 min in Zone 4 and >50% of that time had low HR
        return zone4Seconds > 600 && decouplingSeconds > zone4Seconds * 0.5
    }
    
    /**
     * Suggest FTP increase based on RPE and class difficulty.
     * Returns suggested new FTP or null if no change recommended.
     */
    fun suggestFtpIncrease(
        rpe: Int?,
        isHardClass: Boolean,
        currentFtp: Double
    ): Double? {
        if (rpe == null || rpe > 4 || !isHardClass) return null
        return currentFtp * 1.03 // 3% increase
    }
    
    /**
     * Data class for analysis results.
     */
    data class AnalysisResult(
        val estimatedFtp: Double? = null,
        val ftpIncreaseSuggested: Double? = null,
        val biometricDecoupling: Boolean = false
    )
    
    /**
     * Perform full post-workout analysis.
     */
    fun analyze(
        metrics: List<WorkoutMetricEntity>,
        currentFtp: Double,
        maxHr: Int?,
        rpe: Int?,
        isHardClass: Boolean
    ): AnalysisResult {
        val estimatedFtp = calculateFtpFrom20MinPeak(metrics)
        val ftpIncrease = suggestFtpIncrease(rpe, isHardClass, currentFtp)
        val decoupling = detectBiometricDecoupling(metrics, currentFtp, maxHr)
        
        return AnalysisResult(
            estimatedFtp = estimatedFtp,
            ftpIncreaseSuggested = ftpIncrease,
            biometricDecoupling = decoupling
        )
    }
}
