package com.pelonot.data.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.7.1b — the decoder that makes `msg.what` irrelevant.
 *
 * Every frame below was captured off the real board on 1 August 2026 and is
 * paired with the value the service itself reported for it, so these assert
 * agreement with the bike rather than with a theory. The captures span a
 * stationary rider, a steady 45–64 rpm, the resistance knob swept 27→83%, and
 * a deliberate bout of contention with a second sensor app.
 */
class PelotonFrameParserTest {

    /** frame → the value Peloton's own service reported alongside it. */
    private val realFrames = listOf(
        "F1 41 03 30 30 30 C5 F6" to 0.0,
        "F1 41 03 30 37 30 CC F6" to 70.0,
        "F1 41 03 32 35 30 CC F6" to 52.0,
        "F1 41 03 33 36 30 CE F6" to 63.0,
        "F1 41 03 35 34 30 CE F6" to 45.0,
        "F1 41 03 36 34 30 CF F6" to 46.0,
        "F1 41 03 37 34 30 D0 F6" to 47.0,
        "F1 41 03 38 35 30 D2 F6" to 58.0,
        "F1 44 05 30 30 30 30 30 2A F6" to 0.0,
        "F1 44 05 30 38 33 30 30 35 F6" to 38.0,
        "F1 44 05 31 36 36 33 30 3A F6" to 366.1,
        "F1 44 05 33 33 34 31 30 35 F6" to 143.3,
        "F1 44 05 34 34 35 32 30 39 F6" to 254.4,
        "F1 44 05 35 38 39 30 30 40 F6" to 98.5,
        "F1 44 05 36 36 34 30 30 3A F6" to 46.6,
        "F1 44 05 37 36 31 32 30 3A F6" to 216.7,
        "F1 49 03 30 34 30 D1 F6" to 40.0,
        "F1 49 03 30 38 30 D5 F6" to 80.0,
        "F1 49 03 31 38 30 D6 F6" to 81.0,
        "F1 49 03 32 37 30 D6 F6" to 72.0,
        "F1 49 03 33 37 30 D7 F6" to 73.0,
        "F1 49 03 34 37 30 D8 F6" to 74.0,
        "F1 49 03 36 33 30 D6 F6" to 36.0,
        "F1 49 03 37 34 30 D8 F6" to 47.0,
        "F1 4A 04 33 34 35 30 0B F6" to 543.0,
        "F1 4A 04 34 34 35 30 0C F6" to 544.0
    )

    @Test
    fun `every frame captured off the bike decodes to the value the board meant`() {
        realFrames.forEach { (hex, expected) ->
            val frame = PelotonFrameParser.parse(hex)
            assertEquals("failed to parse $hex", expected, frame!!.value, 0.001)
        }
    }

    @Test
    fun `the digits are least significant first`() {
        // "330" is 33% and not 330%. Reading it forwards is how a resistance
        // of 33 becomes an impossible one, and the fence would then be asked
        // to clean up after the parser.
        assertEquals(33.0, PelotonFrameParser.parse("F1 49 03 33 33 30 D3 F6")!!.value, 0.0)
    }

    @Test
    fun `power is reported in tenths of a watt and the others are whole`() {
        assertEquals(366.1, PelotonFrameParser.parse("F1 44 05 31 36 36 33 30 3A F6")!!.value, 0.001)
        assertEquals(58.0, PelotonFrameParser.parse("F1 41 03 38 35 30 D2 F6")!!.value, 0.0)
        assertEquals(80.0, PelotonFrameParser.parse("F1 49 03 30 38 30 D5 F6")!!.value, 0.0)
    }

    @Test
    fun `each frame names its own metric`() {
        assertEquals(TelemetryField.Cadence, PelotonFrameParser.parse("F1 41 03 38 35 30 D2 F6")!!.field)
        assertEquals(TelemetryField.Power, PelotonFrameParser.parse("F1 44 05 30 38 33 30 30 35 F6")!!.field)
        assertEquals(TelemetryField.Resistance, PelotonFrameParser.parse("F1 49 03 30 38 30 D5 F6")!!.field)
    }

    /**
     * The intruder of 2.7b, now identified by name rather than caught by a
     * bound. It is a real reading — the resistance potentiometer before
     * scaling — and the app has no column for it.
     */
    @Test
    fun `the raw resistance frame is recognised and has no field`() {
        val frame = PelotonFrameParser.parse("F1 4A 04 33 34 35 30 0B F6")!!

        assertEquals(PelotonFrameParser.ID_RAW_RESISTANCE, frame.id)
        assertNull("raw resistance must not be filed as a metric", frame.field)
        assertEquals(543.0, frame.value, 0.0)
        assertTrue(PelotonFrameParser.isKnownButUnused(frame.id))
    }

    /**
     * The one that matters most: a frame decoded from its own bytes is immune
     * to the label the service puts on it. This is the same resistance frame
     * the bike delivered under `what=8`, which means *power*.
     */
    @Test
    fun `a frame decodes the same however the service labels it`() {
        val frame = PelotonFrameParser.parse("F1 49 03 37 32 30 D6 F6")!!

        assertEquals(TelemetryField.Resistance, frame.field)
        assertEquals(27.0, frame.value, 0.0)
    }

    // ── Frames that must not be trusted ─────────────────────────────

    @Test
    fun `a frame failing its checksum is not a frame`() {
        // Same resistance frame with the checksum off by one.
        assertNull(PelotonFrameParser.parse("F1 49 03 33 33 30 D4 F6"))
    }

    @Test
    fun `a truncated frame is rejected`() {
        assertNull(PelotonFrameParser.parse("F1 49 03 33 33"))
    }

    @Test
    fun `a length that disagrees with the frame is rejected`() {
        assertNull(PelotonFrameParser.parse("F1 49 05 33 33 30 D3 F6"))
    }

    @Test
    fun `non-digit payload bytes are rejected`() {
        assertNull(PelotonFrameParser.parse("F1 49 03 41 42 43 E6 F6"))
    }

    @Test
    fun `missing start or end markers are rejected`() {
        assertNull(PelotonFrameParser.parse("F2 49 03 33 33 30 D3 F6"))
        assertNull(PelotonFrameParser.parse("F1 49 03 33 33 30 D3 F5"))
    }

    @Test
    fun `absent or unparseable text is rejected rather than guessed`() {
        assertNull(PelotonFrameParser.parse(null))
        assertNull(PelotonFrameParser.parse(""))
        assertNull(PelotonFrameParser.parse("   "))
        assertNull(PelotonFrameParser.parse("TIME_OUT"))
        assertNull(PelotonFrameParser.parse("not hex at all"))
    }

    /**
     * Every value the parser produces still has to survive the fence. A frame
     * decoding cleanly to 543 is a *correct* decode of the wrong stream, and
     * the raw-resistance case above is why it is dropped by identity first.
     */
    @Test
    fun `real frames all decode inside their physical bounds`() {
        realFrames.forEach { (hex, _) ->
            val frame = PelotonFrameParser.parse(hex)!!
            val field = frame.field ?: return@forEach
            assertTrue(
                "$hex decoded to an impossible ${field.label} of ${frame.value}",
                TelemetryBounds.accepts(field, frame.value)
            )
        }
    }
}
