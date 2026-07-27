package com.pelonot.data.sensor

/**
 * Calculates Coggan power zones.
 */
object PowerZoneCalculator {

    enum class PowerZone(val number: Int, val displayName: String, val rangePct: ClosedRange<Double>) {
        Z1(1, "Active Recovery", 0.0..0.55),
        Z2(2, "Endurance", 0.56..0.75),
        Z3(3, "Tempo", 0.76..0.90),
        Z4(4, "Lactate Threshold", 0.91..1.05),
        Z5(5, "VO2 Max", 1.06..1.20),
        Z6(6, "Anaerobic Capacity", 1.21..1.50),
        Z7(7, "Neuromuscular Power", 1.51..99.0)
    }

    /**
     * Get Coggan power zone for a given power and FTP.
     */
    fun getZoneForPower(power: Double, ftp: Double): PowerZone {
        if (ftp <= 0.0) return PowerZone.Z1
        val pct = power / ftp
        return PowerZone.values().find { pct in it.rangePct } ?: PowerZone.Z7
    }

    /**
     * Calculate target power based on zone, FTP, and intent modifier multiplier.
     */
    fun getTargetPower(zone: PowerZone, ftp: Double, intentModifier: String): ClosedRange<Double> {
        val multiplier = when (intentModifier) {
            "Reach New Milestones" -> 1.05
            "Just Stay Fit" -> 0.95
            else -> 1.0
        }
        val minPower = ftp * zone.rangePct.start * multiplier
        val maxPower = ftp * zone.rangePct.endInclusive * multiplier
        return minPower..maxPower
    }
}
