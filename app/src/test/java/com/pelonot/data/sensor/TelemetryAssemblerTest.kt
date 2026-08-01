package com.pelonot.data.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.7.6 — the test that would have caught it.
 *
 * Nothing in the suite could, because the simulated source is one well-behaved
 * emitter that hands over a complete, coherent triple, and the defect lives in
 * the seam where three separate streams are stitched into one. This is that
 * seam, lifted out of `PelotonSensorServiceSource` so it can be tested at all.
 */
class TelemetryAssemblerTest {

    private val assembler = TelemetryAssembler()

    private fun Intake.readingOrNull() = (this as? Intake.Emit)?.reading

    @Test
    fun `nothing is published until all three streams have reported`() {
        assertTrue(assembler.onValue(TelemetryField.Cadence, 67.0, 1_000L) is Intake.Held)
        assertTrue(assembler.onValue(TelemetryField.Power, 53.0, 1_100L) is Intake.Held)

        val reading = assembler.onValue(TelemetryField.Resistance, 33.0, 1_200L).readingOrNull()

        assertEquals(67.0, reading!!.cadenceRpm, 0.0)
        assertEquals(33.0, reading.resistancePercent, 0.0)
        assertEquals(53.0, reading.powerWatts, 0.0)
    }

    /**
     * The code this replaces started its three fields at `0.0` and emitted all
     * three whenever any one of them moved, so the first message of every
     * hardware ride published two measured-looking zeroes.
     */
    @Test
    fun `a field never seen is never published as zero`() {
        val first = assembler.onValue(TelemetryField.Cadence, 67.0, 1_000L)

        assertNull(first.readingOrNull())
    }

    @Test
    fun `a reading is only as fresh as its stalest field`() {
        assembler.onValue(TelemetryField.Cadence, 67.0, 1_000L)
        assembler.onValue(TelemetryField.Resistance, 33.0, 1_100L)
        val reading = assembler.onValue(TelemetryField.Power, 53.0, 2_000L).readingOrNull()

        // Stamped with the oldest field, not with the message that completed
        // it. Claiming 2_000 would let a half-frozen triple pass the staleness
        // check in SensorReading.isStaleAt that it should fail.
        assertEquals(1_000L, reading!!.timestampMs)
    }

    @Test
    fun `a stream that dies takes the reading down rather than riding along inside it`() {
        assembler.onValue(TelemetryField.Cadence, 67.0, 1_000L)
        assembler.onValue(TelemetryField.Resistance, 33.0, 1_000L)
        assertTrue(assembler.onValue(TelemetryField.Power, 53.0, 1_000L) is Intake.Emit)

        // Cadence and power keep arriving; the knob's stream has stopped.
        val later = 1_000L + TelemetryAssembler.DEFAULT_COHERENCE_WINDOW_MS + 1
        assertTrue(assembler.onValue(TelemetryField.Cadence, 70.0, later) is Intake.Held)
        assertTrue(assembler.onValue(TelemetryField.Power, 60.0, later) is Intake.Held)

        // And it recovers the moment the knob reports again.
        assertTrue(assembler.onValue(TelemetryField.Resistance, 34.0, later) is Intake.Emit)
    }

    @Test
    fun `fields arriving out of order still describe one moment`() {
        assembler.onValue(TelemetryField.Power, 53.0, 1_000L)
        assembler.onValue(TelemetryField.Resistance, 33.0, 1_050L)
        val reading = assembler.onValue(TelemetryField.Cadence, 67.0, 1_100L).readingOrNull()!!

        assertEquals(67.0, reading.cadenceRpm, 0.0)
        assertEquals(33.0, reading.resistancePercent, 0.0)
        assertEquals(53.0, reading.powerWatts, 0.0)
    }

    /**
     * The bike's own signature: a value near 602 arriving on one of the three
     * event types. It must not be stored, and it must not refresh the field it
     * landed in — otherwise a stream sending nothing but nonsense would keep
     * looking alive.
     */
    @Test
    fun `an impossible value is rejected and does not refresh its field`() {
        assembler.onValue(TelemetryField.Cadence, 67.0, 1_000L)
        assembler.onValue(TelemetryField.Resistance, 33.0, 1_000L)
        assembler.onValue(TelemetryField.Power, 53.0, 1_000L)

        val late = 1_000L + TelemetryAssembler.DEFAULT_COHERENCE_WINDOW_MS + 1
        val rejected = assembler.onValue(TelemetryField.Cadence, 603.0, late)

        assertEquals(Intake.Rejected(ImplausibleValue(TelemetryField.Cadence, 603.0)), rejected)
        assertEquals(1, assembler.rejectedCount)

        // Cadence is now older than the window, so nothing is coherent — the
        // gap is the honest answer, and 603 is nowhere.
        assertTrue(assembler.onValue(TelemetryField.Power, 54.0, late) is Intake.Held)
    }

    @Test
    fun `readings keep flowing while every stream is healthy`() {
        var emitted = 0
        var atMs = 1_000L
        repeat(20) {
            listOf(TelemetryField.Cadence, TelemetryField.Power, TelemetryField.Resistance)
                .forEach { field ->
                    atMs += 80L
                    if (assembler.onValue(field, 50.0, atMs) is Intake.Emit) emitted++
                }
        }

        // One per message once the triple is complete — the board reports
        // several times a second and the ride screen must not stutter.
        assertEquals(58, emitted)
    }

    @Test
    fun `power from the board is marked as measured`() {
        assembler.onValue(TelemetryField.Cadence, 67.0, 1_000L)
        assembler.onValue(TelemetryField.Resistance, 33.0, 1_000L)
        val reading = assembler.onValue(TelemetryField.Power, 53.0, 1_000L).readingOrNull()!!

        assertTrue(reading.powerIsMeasured)
    }

    @Test
    fun `reset forgets the ride that was`() {
        assembler.onValue(TelemetryField.Cadence, 67.0, 1_000L)
        assembler.onValue(TelemetryField.Resistance, 33.0, 1_000L)
        assembler.onValue(TelemetryField.Cadence, 603.0, 1_000L)
        assembler.reset()

        assertEquals(0, assembler.rejectedCount)
        assertTrue(assembler.onValue(TelemetryField.Power, 53.0, 1_000L) is Intake.Held)
    }
}
