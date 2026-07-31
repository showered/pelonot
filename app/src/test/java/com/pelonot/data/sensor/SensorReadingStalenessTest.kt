package com.pelonot.data.sensor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.4.4 — the rule that decides whether a reading is a measurement or the
 * memory of one.
 *
 * `SensorRepository` publishes through a `StateFlow`, so a stalled board leaves
 * its last reading sitting on the flow looking exactly like a live one. The
 * only thing that can tell them apart is the timestamp, and this is the whole
 * of that decision.
 */
class SensorReadingStalenessTest {

    private fun reading(atMs: Long) = SensorReading(
        powerWatts = 180.0,
        cadenceRpm = 88.0,
        resistancePercent = 45.0,
        timestampMs = atMs
    )

    @Test
    fun `a reading that has just arrived is live`() {
        val now = 1_000_000L
        assertFalse(reading(now).isStaleAt(now, SensorReading.MAX_AGE_MS))
    }

    @Test
    fun `a reading a couple of seconds old is still live`() {
        val now = 1_000_000L
        // Several missed reports at the board's own rate, but well inside the
        // window: a healthy ride must never grow holes.
        assertFalse(reading(now - 2_000L).isStaleAt(now, SensorReading.MAX_AGE_MS))
    }

    @Test
    fun `exactly at the window is not yet stale`() {
        val now = 1_000_000L
        assertFalse(reading(now - SensorReading.MAX_AGE_MS).isStaleAt(now, SensorReading.MAX_AGE_MS))
    }

    @Test
    fun `one millisecond past the window is stale`() {
        val now = 1_000_000L
        assertTrue(
            reading(now - SensorReading.MAX_AGE_MS - 1).isStaleAt(now, SensorReading.MAX_AGE_MS)
        )
    }

    @Test
    fun `a reading frozen for a minute is stale`() {
        val now = 1_000_000L
        assertTrue(reading(now - 60_000L).isStaleAt(now, SensorReading.MAX_AGE_MS))
    }

    /**
     * EMPTY carries `timestampMs = 0`, which is what the ride screen shows
     * before the first packet. It must read as stale, or a ride started before
     * the board answers records a second of fabricated zeros — the same
     * mistake as a measured-looking zero heart rate.
     */
    @Test
    fun `the resting reading is never mistaken for a measurement`() {
        assertTrue(SensorReading.EMPTY.isStaleAt(1_000_000L, SensorReading.MAX_AGE_MS))
    }

    /**
     * A heart-rate packet updates the merged reading with `copy()`, keeping the
     * bike's own timestamp. That is deliberate: a live strap must not make dead
     * bike telemetry look fresh.
     */
    @Test
    fun `a heart rate update does not refresh stale bike telemetry`() {
        val now = 1_000_000L
        val stale = reading(now - 10_000L).copy(heartRateBpm = 142)

        assertTrue(stale.isStaleAt(now, SensorReading.MAX_AGE_MS))
    }
}
