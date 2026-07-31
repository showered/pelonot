package com.pelonot.data.sensor

/**
 * One instant of bike telemetry, merged from every active sensor.
 *
 * @property heartRateBpm Null means *unknown* — no strap paired or no packet
 *   yet. It must not be conflated with a measured zero, which is why this is
 *   nullable rather than defaulting to 0 as it did previously; a 0 propagated
 *   into the workout record as a genuine heart-rate sample and dragged every
 *   average down.
 * @property powerIsMeasured True when [powerWatts] came off the bike's own
 *   sensor board, false when [PowerModel] inferred it from cadence and
 *   resistance. The distinction matters because the model's coefficients are
 *   unvalidated: inferred watts are comparable only between one rider's own
 *   rides, and nothing should present them as measured.
 */
data class SensorReading(
    val powerWatts: Double,
    val cadenceRpm: Double,
    val resistancePercent: Double,
    val heartRateBpm: Int? = null,
    val powerIsMeasured: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis()
) {
    companion object {
        /** The resting state shown before any sensor has reported. */
        val EMPTY = SensorReading(
            powerWatts = 0.0,
            cadenceRpm = 0.0,
            resistancePercent = 0.0,
            heartRateBpm = null,
            timestampMs = 0L
        )
    }
}
