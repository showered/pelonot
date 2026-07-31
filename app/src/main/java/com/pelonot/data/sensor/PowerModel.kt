package com.pelonot.data.sensor

import com.pelonot.domain.calibration.PowerCurve
import com.pelonot.domain.calibration.ShippedPowerCurve

/**
 * Estimates crank power from cadence and resistance.
 *
 * The Gen 1/Gen 2 Peloton sensor board reports flywheel ticks and a resistance
 * potentiometer position; it does **not** report power over the serial
 * protocol. Every subscription-free client therefore models it.
 *
 * > **On a real bike this does not run.** Peloton's own sensor service reports
 * > measured watts (PLAN 2.1a), and `SensorReading.powerIsMeasured` says which
 * > is which. What this governs is **simulated rides** and the **prescribed
 * > resistance band** (11.2.1) — the quality of a suggestion, never the
 * > integrity of a record.
 *
 * > **The shipped coefficients are not merely unvalidated, they are measurably
 * > wrong**: RMSE 137 W, median absolute error 66%, R² 0.21 against 310
 * > steady-state samples off a real board (`calibration/`). Never present a
 * > modelled watt as measured.
 *
 * Which curve is in force is [curve], set from
 * [com.pelonot.data.repository.CalibrationRepository] at ride start. This file
 * stays free of Android imports so it remains JVM-testable, which is why the
 * curve is pushed in rather than read from a repository here.
 */
object PowerModel {

    /**
     * The curve in force. Defaults to the shipped one and is replaced only by
     * a fit that beat it out of sample on this bike's own rides (2.2a.3).
     */
    @Volatile
    var curve: PowerCurve = ShippedPowerCurve

    fun estimateWatts(cadenceRpm: Double, resistancePercent: Double): Double =
        curve.watts(cadenceRpm, resistancePercent)

    /**
     * The inverse: what resistance would produce [targetWatts] at [cadenceRpm].
     *
     * This is the number a rider can actually *do* something with. Power is an
     * output — the knob and their legs are the only two inputs on the machine —
     * so a prescription of "250 W" is really "hold this cadence and set the
     * resistance to about here", and until 11.2.1 the app made them work that
     * out themselves.
     *
     * @return null when the target cannot be reached at that cadence — the
     *   answer is then "change your legs, not the knob", which is a different
     *   instruction and must not be disguised as a clamped percentage.
     */
    fun resistanceForWatts(targetWatts: Double, cadenceRpm: Double): Double? =
        curve.resistanceForWatts(targetWatts, cadenceRpm)

    /** Restores the shipped curve. For tests, and for the reset in Settings. */
    fun useShippedCurve() {
        curve = ShippedPowerCurve
    }
}
