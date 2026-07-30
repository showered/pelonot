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
