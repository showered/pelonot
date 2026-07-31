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
    /**
     * Whether this reading is too old to stand for what the bike is doing now
     * (2.4.4).
     *
     * `SensorRepository` publishes readings through a `StateFlow`, which holds
     * its last value indefinitely — so when the board stops answering, the last
     * reading it sent stays on the flow, and a recorder that reads `.value`
     * once a second writes that same frozen sample into the rider's permanent
     * record for as long as the dropout lasts. Nothing about the reading itself
     * says it is old except this timestamp, and until now nothing asked.
     *
     * A stale reading is an absence, not a measurement. It is the same argument
     * as nullable `heartRateBpm` and the `TIME_OUT` payload the sensor source
     * drops rather than records (2.1a.3): the honest answer is a gap in the
     * series, which every chart already draws correctly, and not a plausible
     * number nobody can later tell apart from a real one.
     */
    fun isStaleAt(nowMs: Long, maxAgeMs: Long): Boolean = nowMs - timestampMs > maxAgeMs

    companion object {
        /**
         * How old a reading may be and still count as live.
         *
         * The bike's board and the simulator both report several times a
         * second, so three seconds is many missed reports rather than one
         * unlucky one — long enough never to punch holes in a healthy ride,
         * short enough that a stall is caught while the rider is still in the
         * interval it happened in.
         */
        const val MAX_AGE_MS = 3_000L

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
