package com.pelonot.data.sensor

/**
 * Raw sensor tick from the Peloton sensor board.
 *
 * The sensor board sends cadence and resistance tick events
 * via serial port. Each tick represents a magnetic hall-effect
 * pulse from the flywheel magnet ring.
 *
 * @property timestampMs System clock when tick was received
 * @property tickType 'C' for cadence tick, 'R' for resistance tick
 * @property rawValue The raw resistance value (0-100 scale) if this is a resistance tick
 */
data class SensorTick(
    val timestampMs: Long,
    val tickType: Char,
    val rawValue: Int = 0
)

/**
 * Parsed sensor reading combining cadence, resistance, power, and heart rate.
 * This is the unified data class exposed by SensorRepository.
 *
 * @property cadenceRpm Current cadence in revolutions per minute
 * @property resistancePercent Current resistance as a percentage (0-100)
 * @property powerWatts Instantaneous power output in watts
 * @property heartRateBpm Current heart rate in beats per minute (null if no HR monitor connected)
 * @property timestampMs System clock when this reading was generated
 */
data class SensorReading(
    val cadenceRpm: Double,
    val resistancePercent: Double,
    val powerWatts: Double,
    val heartRateBpm: Int? = null,
    val timestampMs: Long = System.currentTimeMillis()
)
