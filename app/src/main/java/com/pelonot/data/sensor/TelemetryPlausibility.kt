package com.pelonot.data.sensor

/**
 * Which of the board's three streams a value came from.
 *
 * Named rather than positional on purpose: the defect this file exists for
 * (2.7) is values arriving in each other's columns, and every hop they make
 * from here on carries the name of the thing it measures.
 */
enum class TelemetryField(val label: String, val unit: String) {
    Cadence("cadence", "rpm"),
    Resistance("resistance", "%"),
    Power("power", "W")
}

/** A value the fence would not accept, kept whole so it can be logged. */
data class ImplausibleValue(val field: TelemetryField, val value: Double) {
    override fun toString(): String = "${field.label} $value ${field.unit}"
}

/**
 * The plausibility fence (2.7.3).
 *
 * On the first real ride the bike reported cadence 603 rpm and resistance
 * 602% while the rider was turning the cranks at 61 rpm, and those values were
 * **recorded** — 41 of 53 samples while the overlay was up. A number that
 * cannot be true is not a measurement, and the honest answer is the same one
 * [SensorReading.isStaleAt] gives for a frozen reading: a gap in the series.
 *
 * **Reject, never clamp.** Clamping 603 rpm to 200 would write a plausible lie
 * exactly where a gap belongs, and nobody reading the record later could tell
 * it from a real sprint. The bounds are therefore *physical* rather than
 * athletic — well past anything a rider can do, so a genuine effort is never
 * dropped and only nonsense is.
 *
 * This is a fence and not the fix. Rotation *within* the plausible range — 33
 * and 52 swapping columns — passes every bound there is, which is why
 * [TelemetryAssembler] exists beside it.
 */
object TelemetryBounds {

    /**
     * A track sprinter tops out near 160 rpm and the bike's own display stops
     * at 200; anything above is the board or our parsing, not a leg.
     */
    val CADENCE_RPM = 0.0..200.0

    /** The knob's own scale. There is no 101%. */
    val RESISTANCE_PERCENT = 0.0..100.0

    /**
     * Track-standing-start territory, several times anything this bike will
     * ever see, and still an order of magnitude below the 636 W spike the
     * first ride recorded off a rider averaging 47 W.
     */
    val POWER_WATTS = 0.0..2500.0

    fun accepts(field: TelemetryField, value: Double): Boolean {
        if (value.isNaN() || value.isInfinite()) return false
        return when (field) {
            TelemetryField.Cadence -> value in CADENCE_RPM
            TelemetryField.Resistance -> value in RESISTANCE_PERCENT
            TelemetryField.Power -> value in POWER_WATTS
        }
    }
}

/**
 * Every field of this reading a bike could not have produced, in field order.
 *
 * Empty for a reading worth recording.
 */
fun SensorReading.implausibleValues(): List<ImplausibleValue> = buildList {
    if (!TelemetryBounds.accepts(TelemetryField.Cadence, cadenceRpm)) {
        add(ImplausibleValue(TelemetryField.Cadence, cadenceRpm))
    }
    if (!TelemetryBounds.accepts(TelemetryField.Resistance, resistancePercent)) {
        add(ImplausibleValue(TelemetryField.Resistance, resistancePercent))
    }
    if (!TelemetryBounds.accepts(TelemetryField.Power, powerWatts)) {
        add(ImplausibleValue(TelemetryField.Power, powerWatts))
    }
}

/** True when nothing in this reading is physically impossible. */
val SensorReading.isPlausible: Boolean
    get() = implausibleValues().isEmpty()
