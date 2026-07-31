package com.pelonot.domain.calibration

import kotlin.math.pow

/**
 * A mapping from what the rider controls — cadence and the resistance knob —
 * to the watts it produces, and back again.
 *
 * Two implementations: the coefficients this app ships with, and a set fitted
 * to one particular bike's own measured rides (PLAN 2.2a). Both directions
 * matter. `watts` drives simulated rides; `resistanceForWatts` is what turns a
 * class's "250 W" into "set the knob about here", which is the only form of
 * that instruction a rider can act on.
 */
interface PowerCurve {

    fun watts(cadenceRpm: Double, resistancePercent: Double): Double

    /**
     * @return null when the target cannot be reached at that cadence. The
     *   honest answer there is "change your legs, not the knob", and it must
     *   not be disguised as a clamped percentage.
     */
    fun resistanceForWatts(targetWatts: Double, cadenceRpm: Double): Double?

    companion object {
        /** Below this the flywheel is coasting, not being driven. */
        const val MIN_CADENCE_RPM = 10.0

        /** Track sprinters peak near 2000 W; anything beyond is a bad reading. */
        const val MAX_PLAUSIBLE_WATTS = 2_500.0
    }
}

/**
 * The curve this app ships with: cubic in cadence, scaled linearly by
 * resistance.
 *
 * `P = (c3·rpm³ + c2·rpm² + c1·rpm + c0) · (1 + R/50)`
 *
 * > **These coefficients are not merely unvalidated, they are measurably
 * > wrong.** Against 310 steady-state samples off a real board they score
 * > RMSE 137 W, median absolute error 66%, R² 0.21 (`calibration/`). They are
 * > kept as the default because they are at least a plausible *shape*, and
 * > because the alternative — a fit that failed cross-validation — would look
 * > like progress and be none.
 */
object ShippedPowerCurve : PowerCurve {

    override fun watts(cadenceRpm: Double, resistancePercent: Double): Double {
        if (cadenceRpm < PowerCurve.MIN_CADENCE_RPM) return 0.0

        val base = baseWatts(cadenceRpm)
        val resistanceFactor =
            1.0 + (resistancePercent.coerceIn(0.0, 100.0) / RESISTANCE_DIVISOR)

        return (base * resistanceFactor).coerceIn(0.0, PowerCurve.MAX_PLAUSIBLE_WATTS)
    }

    override fun resistanceForWatts(targetWatts: Double, cadenceRpm: Double): Double? {
        if (cadenceRpm < PowerCurve.MIN_CADENCE_RPM || targetWatts <= 0.0) return null

        val base = baseWatts(cadenceRpm)
        // Below roughly 23 rpm the cubic is at or under zero: no amount of
        // resistance produces power because the flywheel is barely turning.
        if (base <= 0.0) return null

        val resistance = RESISTANCE_DIVISOR * ((targetWatts / base) - 1.0)
        return resistance.takeIf { it in 0.0..100.0 }
    }

    private fun baseWatts(cadenceRpm: Double): Double =
        (C3 * cadenceRpm.pow(3)) + (C2 * cadenceRpm.pow(2)) + (C1 * cadenceRpm) + C0

    private const val C3 = 0.000185
    private const val C2 = -0.0125
    private const val C1 = 0.85
    private const val C0 = -15.0
    private const val RESISTANCE_DIVISOR = 50.0
}

/**
 * A curve fitted to one bike's own measured rides.
 *
 * `P = (a + b·R^k)·rpm + c·rpm³`
 *
 * The form is chosen for two properties rather than for fit quality. It is
 * **monotone in resistance** whenever `b > 0` and `k > 0`, so turning the knob
 * up can never be predicted to produce less power and [resistanceForWatts]
 * stays single-valued. And it is **linear in `a`, `b` and `c` for a fixed
 * `k`**, which is what makes it fittable in closed form on a tablet without
 * an optimiser (see [PowerCurveFitter]).
 */
data class CalibratedPowerCurve(
    val a: Double,
    val b: Double,
    val k: Double,
    val c: Double
) : PowerCurve {

    override fun watts(cadenceRpm: Double, resistancePercent: Double): Double {
        if (cadenceRpm < PowerCurve.MIN_CADENCE_RPM) return 0.0

        val resistance = resistancePercent.coerceIn(0.0, 100.0)
        val loaded = a + b * resistance.pow(k)
        val estimate = loaded * cadenceRpm + c * cadenceRpm.pow(3)

        return estimate.coerceIn(0.0, PowerCurve.MAX_PLAUSIBLE_WATTS)
    }

    override fun resistanceForWatts(targetWatts: Double, cadenceRpm: Double): Double? {
        if (cadenceRpm < PowerCurve.MIN_CADENCE_RPM || targetWatts <= 0.0) return null
        if (b <= 0.0 || k <= 0.0) return null

        // P = (a + b·R^k)·rpm + c·rpm³  ⇒  R = (((P − c·rpm³)/rpm − a) / b)^(1/k)
        val perRev = (targetWatts - c * cadenceRpm.pow(3)) / cadenceRpm
        val loadedTerm = (perRev - a) / b
        if (loadedTerm <= 0.0) return null

        val resistance = loadedTerm.pow(1.0 / k)
        return resistance.takeIf { it.isFinite() && it in 0.0..100.0 }
    }
}
