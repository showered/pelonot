package com.pelonot.data.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SerialProtocolParserTest {

    private val parser = SerialProtocolParser()

    private fun bytes(vararg values: Any): ByteArray = values.map { value ->
        when (value) {
            is Char -> value.code.toByte()
            is Int -> value.toByte()
            else -> error("unsupported $value")
        }
    }.toByteArray()

    private fun parse(vararg values: Any): List<SerialEvent> {
        val buffer = bytes(*values)
        return parser.parse(buffer, buffer.size)
    }

    @Test
    fun `decodes a cadence tick`() {
        assertEquals(listOf(SerialEvent.CadenceTick), parse('C'))
    }

    @Test
    fun `decodes a resistance update from its value byte`() {
        assertEquals(listOf(SerialEvent.ResistanceUpdate(42.0)), parse('R', 42))
    }

    @Test
    fun `decodes several commands from one buffer`() {
        val events = parse('C', 'C', 'R', 55, 'C')

        assertEquals(
            listOf(
                SerialEvent.CadenceTick,
                SerialEvent.CadenceTick,
                SerialEvent.ResistanceUpdate(55.0),
                SerialEvent.CadenceTick
            ),
            events
        )
    }

    @Test
    fun `carries a split resistance command across read boundaries`() {
        // Regression: the parser used to treat each buffer independently, so a
        // trailing 'R' was dropped and its value byte in the next read was
        // then misread as a command. Turning the resistance knob at the wrong
        // instant meant the update was lost entirely.
        val first = parse('C', 'R')
        assertEquals(listOf(SerialEvent.CadenceTick), first)

        val second = parse(72, 'C')
        assertEquals(
            listOf(SerialEvent.ResistanceUpdate(72.0), SerialEvent.CadenceTick),
            second
        )
    }

    @Test
    fun `a value byte that looks like a command is still treated as a value`() {
        // 'C' is 67. As the byte after 'R' it means resistance 67, not a tick.
        assertEquals(listOf(SerialEvent.ResistanceUpdate(67.0)), parse('R', 'C'))
    }

    @Test
    fun `clamps out of range resistance values`() {
        assertEquals(listOf(SerialEvent.ResistanceUpdate(100.0)), parse('R', 255))
        assertEquals(listOf(SerialEvent.ResistanceUpdate(0.0)), parse('R', 0))
    }

    @Test
    fun `ignores line noise between commands`() {
        val events = parse(0x00, 'C', 0xFF, 0x7F, 'C')

        assertEquals(listOf(SerialEvent.CadenceTick, SerialEvent.CadenceTick), events)
    }

    @Test
    fun `reset discards a partially read command`() {
        parse('R') // leaves the parser awaiting a value byte
        parser.reset()

        // After a reconnect the stream restarts, so the stale 'R' must not
        // swallow the first byte of the new stream.
        assertEquals(listOf(SerialEvent.CadenceTick), parse('C'))
    }

    @Test
    fun `only reads up to the reported length`() {
        val buffer = bytes('C', 'C', 'C')

        // Simulates a short read into a longer reusable buffer: the trailing
        // bytes are stale data from a previous read.
        assertEquals(listOf(SerialEvent.CadenceTick), parser.parse(buffer, length = 1))
    }

    @Test
    fun `an empty read yields nothing`() {
        assertTrue(parser.parse(ByteArray(0), 0).isEmpty())
    }
}
