package com.pelonot.data.sensor

import kotlinx.coroutines.flow.Flow

/**
 * A source of bike telemetry (cadence, resistance, power).
 *
 * Implementations expose a **cold** flow: collection opens the underlying
 * device and cancellation closes it. That inverts the previous design, where
 * `open()`/`close()`/`reconnect()` called into each other — `close()` kicked
 * off a reconnect, and `reconnect()` called `close()` — so ending a workout
 * started an endless reconnect loop against a device nobody was reading.
 *
 * Reconnection is deliberately *not* implemented here. [SensorRepository]
 * owns retry policy for every source, so there is exactly one backoff schedule
 * rather than two competing ones.
 */
interface SensorSource {

    /** Stable identifier, used in logs and in the settings UI. */
    val id: String

    /** Human-readable name for the settings screen. */
    val displayName: String

    /**
     * True when this source can supply data on this device right now — for
     * the serial port, whether the character device exists and is readable.
     */
    fun isAvailable(): Boolean

    /**
     * Cold flow of telemetry. Throws on transport failure so the collector can
     * apply a retry policy; it must not swallow errors and complete silently.
     */
    fun readings(): Flow<SensorReading>
}
