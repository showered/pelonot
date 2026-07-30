package com.pelonot.data.sensor

import kotlin.math.pow

/**
 * Estimates crank power from cadence and resistance.
 *
 * The Gen 1/Gen 2 Peloton sensor board reports flywheel ticks and a resistance
 * potentiometer position; it does **not** report power. Every subscription-free
 * client therefore models it.
 *
 * > **These coefficients are unvalidated.** They were carried over verbatim
 * > from the previous implementation, which attributes them to the Grupetto
 * > reverse-engineering effort without a citation. Absolute watts should not
 * > be trusted against a real power meter, and FTP derived from them is only
 * > self-consistent — comparable between your own rides, not with anyone
 * > else's. PLAN.md item 2.2 tracks validating this against a known curve.
 *
 * Kept as a separate object so the curve can be swapped or calibrated without
 * touching transport code, and so it can be unit-tested.
 */
object PowerModel {

    /**
     * Cubic in cadence, scaled linearly by resistance.
     *
     * `P = (c3·rpm³ + c2·rpm² + c1·rpm + c0) · (1 + R/50)`
     */
    fun estimateWatts(cadenceRpm: Double, resistancePercent: Double): Double {
        if (cadenceRpm < MIN_CADENCE_RPM) return 0.0

        val base = (C3 * cadenceRpm.pow(3)) +
            (C2 * cadenceRpm.pow(2)) +
            (C1 * cadenceRpm) +
            C0

        val resistanceFactor = 1.0 + (resistancePercent.coerceIn(0.0, 100.0) / RESISTANCE_DIVISOR)

        return (base * resistanceFactor).coerceIn(0.0, MAX_PLAUSIBLE_WATTS)
    }

    /** Below this the flywheel is coasting, not being driven. */
    private const val MIN_CADENCE_RPM = 10.0

    private const val C3 = 0.000185
    private const val C2 = -0.0125
    private const val C1 = 0.85
    private const val C0 = -15.0
    private const val RESISTANCE_DIVISOR = 50.0

    /** Track sprinters peak near 2000W; anything beyond is a bad reading. */
    private const val MAX_PLAUSIBLE_WATTS = 2_500.0
}
