package com.pelonot.domain.model

/**
 * Coggan 7-zone power model.
 *
 * Zone bounds are expressed as a fraction of FTP and are **contiguous and
 * half-open** (`lower <= pct < upper`). The commonly published table quotes
 * whole percentages — "Z1 < 55%, Z2 56–75%" — which leaves unassigned slivers
 * between zones. Modelling those literally means a rider at 55.5% of FTP
 * matches no zone at all, so the boundaries here butt up against each other.
 */
enum class PowerZone(
    val number: Int,
    val displayName: String,
    /** Lower bound as a fraction of FTP, inclusive. */
    val lowerBound: Double,
    /** Upper bound as a fraction of FTP, exclusive. [Z7] is unbounded. */
    val upperBound: Double
) {
    Z1(1, "Active Recovery", 0.00, 0.56),
    Z2(2, "Endurance", 0.56, 0.76),
    Z3(3, "Tempo", 0.76, 0.91),
    Z4(4, "Lactate Threshold", 0.91, 1.06),
    Z5(5, "VO2 Max", 1.06, 1.21),
    Z6(6, "Anaerobic Capacity", 1.21, 1.51),
    Z7(7, "Neuromuscular Power", 1.51, Double.POSITIVE_INFINITY);

    /** Absolute power range in watts for a rider with this [ftp]. */
    fun powerRange(ftp: Double): ClosedFloatingPointRange<Double> {
        val upper = if (upperBound.isInfinite()) ftp * 2.0 else ftp * upperBound
        return (ftp * lowerBound)..upper
    }

    companion object {
        /**
         * The zone containing [power] for a rider with the given [ftp].
         * Returns [Z1] when FTP is unknown or invalid, since we cannot say
         * anything meaningful about intensity without it.
         */
        fun forPower(power: Double, ftp: Double): PowerZone {
            if (ftp <= 0.0 || power <= 0.0) return Z1
            val pct = power / ftp
            return entries.lastOrNull { pct >= it.lowerBound } ?: Z1
        }

        fun forNumber(number: Int): PowerZone =
            entries.firstOrNull { it.number == number } ?: Z1
    }
}
