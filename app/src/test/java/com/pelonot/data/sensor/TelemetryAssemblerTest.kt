package com.pelonot.data.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        // Nothing is published afterwards either. 603 is nowhere, and neither
        // are the plausible values that were sitting beside it — they were
        // filed by the same labelling that produced 603.
        assertTrue(assembler.onValue(TelemetryField.Power, 54.0, late) is Intake.Quarantined)
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

    /**
     * The defect as it actually happened, replayed.
     *
     * On the bike the board's events arrive labelled, and during a burst a
     * **fourth value joins the cycle** — the raw resistance reading, which is
     * `≈ 11.13 × resistance% + 229` and was 636 when the knob was at 37%. Three
     * fields filled from a four-value stream means every label after the
     * intruder is wrong, and the recorded ride shows exactly that: the triple
     * slides along by one place per sample.
     *
     * The intruder itself is easy to catch. **What this test is about is its
     * neighbours**: `resistance = 37` when the real resistance is 37% is not
     * an error any bound can see — it is right by luck, and the power beside
     * it is a cadence.
     */
    @Test
    fun `an intruder in the stream discredits the plausible values around it`() {
        // The true state: 78 rpm, 37%, 80 W. The stream: cadence, resistance,
        // power, RAW, repeating — read three at a time into three fields.
        val stream = listOf(78.0, 37.0, 80.0, 636.0)
        val fields = TelemetryField.entries
        var atMs = 1_000L
        var seenIntruder = false
        var emittedBefore = 0
        var emittedAfter = 0
        var quarantined = 0

        repeat(24) { i ->
            atMs += 100L
            when (assembler.onValue(fields[i % 3], stream[i % 4], atMs)) {
                is Intake.Emit -> if (seenIntruder) emittedAfter++ else emittedBefore++
                is Intake.Quarantined -> quarantined++
                is Intake.Rejected -> seenIntruder = true
                is Intake.Held -> Unit
            }
        }

        // 636 arrives every fourth value and re-arms the quarantine each time,
        // so from the moment the intruder is first seen nothing is published at
        // all — the burst becomes a gap, which is the honest record of it.
        assertEquals("nothing may be published during a desync", 0, emittedAfter)
        assertTrue(quarantined > 0)

        // **The intruder is invisible in one column of the three.** It arrives
        // six times here and is caught four: 636 is an impossible cadence and
        // an impossible resistance, but a perfectly possible *power* — which is
        // exactly the 636 W spike sitting on the first real ride's power chart.
        // The raw-resistance value spans 229 to 1342 across the knob's travel,
        // so no power bound worth having excludes it.
        //
        // It does not matter here, and the reason is the point of this design:
        // the misalignment walks the intruder through all three columns, so it
        // is caught within a cycle or two whatever it does in the power column,
        // and the quarantine then covers the samples it was invisible in.
        assertEquals(4, assembler.rejectedCount)

        // **The limitation, stated as a test rather than a hope.** One reading
        // gets out before the intruder is ever seen — the three values that
        // arrived ahead of it. Here they happen to be the correctly aligned
        // triple, so it is a true reading; but nothing in this class *knows*
        // that, and if a burst began mid-cycle the first reading out would be
        // wrong. Detection cannot precede evidence. Closing that needs the
        // labelling fixed at the source, which is 2.7.1 and needs the bike.
        assertEquals(1, emittedBefore)
    }

    @Test
    fun `the stream is trusted again once the intruder stops`() {
        assembler.onValue(TelemetryField.Cadence, 636.0, 1_000L)

        // Still inside the quiet period: a complete, coherent, entirely
        // plausible triple, and it is not to be believed.
        assembler.onValue(TelemetryField.Cadence, 78.0, 2_000L)
        assembler.onValue(TelemetryField.Resistance, 37.0, 2_000L)
        assertTrue(assembler.onValue(TelemetryField.Power, 80.0, 2_000L) is Intake.Quarantined)

        val after = 1_000L + TelemetryAssembler.DEFAULT_RESYNC_QUIET_MS + 1
        assembler.onValue(TelemetryField.Cadence, 78.0, after)
        assembler.onValue(TelemetryField.Resistance, 37.0, after)
        val reading = (assembler.onValue(TelemetryField.Power, 80.0, after) as Intake.Emit).reading

        assertEquals(78.0, reading.cadenceRpm, 0.0)
        assertEquals(37.0, reading.resistancePercent, 0.0)
        assertEquals(80.0, reading.powerWatts, 0.0)
    }

    @Test
    fun `a healthy stream is never quarantined`() {
        var atMs = 1_000L
        repeat(30) {
            atMs += 100L
            TelemetryField.entries.forEach { assembler.onValue(it, 50.0, atMs) }
        }

        assertEquals(0, assembler.quarantinedCount)
        assertFalse(assembler.isQuarantinedAt(atMs))
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
