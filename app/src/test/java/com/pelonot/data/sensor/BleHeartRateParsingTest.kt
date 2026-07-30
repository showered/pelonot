package com.pelonot.data.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Decoding of the Bluetooth SIG Heart Rate Measurement characteristic
 * (0x2A37).
 */
class BleHeartRateParsingTest {

    private fun parse(vararg bytes: Int): Int? =
        BleHeartRateManager.parseHeartRate(bytes.map { it.toByte() }.toByteArray())

    @Test
    fun `reads an 8 bit measurement when the flags bit is clear`() {
        // flags = 0x00 -> uint8 at offset 1.
        assertEquals(72, parse(0x00, 72))
    }

    @Test
    fun `reads a 16 bit little endian measurement when the flags bit is set`() {
        // flags = 0x01 -> uint16 at offsets 1..2, little endian. 0x0110 = 272.
        assertEquals(272, parse(0x01, 0x10, 0x01))
    }

    @Test
    fun `reads 8 bit values above 127 without sign extension`() {
        // A hard sprint. Naive Byte.toInt() would give -55.
        assertEquals(201, parse(0x00, 0xC9))
    }

    @Test
    fun `ignores the other flag bits`() {
        // Sensor-contact, energy-expended and RR-interval bits set, but bit 0
        // clear, so this is still an 8-bit value.
        assertEquals(65, parse(0x1E, 65, 0x00, 0x00))
    }

    @Test
    fun `returns null for a truncated packet rather than throwing`() {
        // Regression: the previous parser checked only isEmpty() and then read
        // value[1], so a one-byte packet threw IndexOutOfBounds on the Binder
        // callback thread, taking the process down.
        assertNull(parse())
        assertNull(parse(0x00))
        // Flags claim 16-bit but only one value byte is present.
        assertNull(parse(0x01, 0x10))
    }

    @Test
    fun `rejects physiologically impossible values`() {
        assertNull(parse(0x00, 0))
        assertNull(parse(0x01, 0xFF, 0xFF))
    }
}
