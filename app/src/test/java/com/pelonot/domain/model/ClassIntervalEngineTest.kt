package com.pelonot.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassIntervalEngineTest {

    private fun interval(start: Int, end: Int, zone: Int, cadence: IntRange = 80..90) =
        Interval(
            startSec = start,
            endSec = end,
            cadenceMin = cadence.first,
            cadenceMax = cadence.last,
            powerZoneNumber = zone
        )

    /** Warmup → tempo → two hard efforts → cooldown; the common class shape. */
    private val standardClass = listOf(
        interval(0, 60, 1),
        interval(60, 120, 3),
        interval(120, 180, 5),
        interval(180, 240, 2),
        interval(240, 300, 5),
        interval(300, 360, 1)
    )

    // ── Position ────────────────────────────────────────────────────

    @Test
    fun `reports the interval containing the current second`() {
        val engine = ClassIntervalEngine(standardClass)

        assertEquals(0, engine.stateAt(0).index)
        assertEquals(0, engine.stateAt(59).index)
        assertEquals(1, engine.stateAt(60).index)
        assertEquals(2, engine.stateAt(179).index)
        assertEquals(3, engine.stateAt(180).index)
    }

    @Test
    fun `elapsed and remaining are relative to the current interval`() {
        val state = ClassIntervalEngine(standardClass).stateAt(75)

        assertEquals(15, state.elapsedInIntervalSec)
        assertEquals(45, state.remainingInIntervalSec)
        assertEquals(75, state.classElapsedSec)
        assertEquals(360, state.classDurationSec)
        assertEquals(285, state.classRemainingSec)
    }

    @Test
    fun `class duration comes from the final interval's end`() {
        assertEquals(360, ClassIntervalEngine(standardClass).durationSec)
    }

    @Test
    fun `sorts an out-of-order template rather than trusting the file`() {
        val shuffled = listOf(
            interval(120, 180, 5),
            interval(0, 60, 1),
            interval(60, 120, 3)
        )

        val engine = ClassIntervalEngine(shuffled)

        assertEquals(listOf(0, 60, 120), engine.intervals.map { it.startSec })
        assertEquals(1, engine.stateAt(90).index)
    }

    @Test
    fun `holds the previous interval across a gap in the template`() {
        // A hand-authored class with a one-second hole in it should not blank
        // the HUD's targets while the rider is still pedalling.
        val gappy = listOf(interval(0, 60, 2), interval(65, 120, 4))

        val state = ClassIntervalEngine(gappy).stateAt(62)

        assertEquals(0, state.index)
        assertEquals(0, state.remainingInIntervalSec)
    }

    @Test
    fun `a class starting after zero prescribes nothing until it begins`() {
        val late = listOf(interval(30, 90, 3))

        val state = ClassIntervalEngine(late).stateAt(10)

        assertNull(state.current)
        assertEquals(late.first(), state.next)
        assertEquals(-1, state.index)
        assertEquals(20, state.remainingInIntervalSec)
    }

    // ── Completion ──────────────────────────────────────────────────

    @Test
    fun `is not complete while the final interval is still running`() {
        assertFalse(ClassIntervalEngine(standardClass).stateAt(359).isComplete)
    }

    @Test
    fun `completes on the second the class timer runs out`() {
        val state = ClassIntervalEngine(standardClass).stateAt(360)

        assertTrue(state.isComplete)
        assertEquals(360, state.classElapsedSec)
        assertEquals(0, state.classRemainingSec)
        // The final interval is held rather than nulled, so the last frame
        // before the ride is finalised still has targets to draw.
        assertEquals(5, state.index)
    }

    @Test
    fun `stays complete past the end rather than wrapping`() {
        val state = ClassIntervalEngine(standardClass).stateAt(10_000)

        assertTrue(state.isComplete)
        assertEquals(1f, state.classProgress, 0.0001f)
    }

    @Test
    fun `a free ride has no class and never completes`() {
        val state = ClassIntervalEngine(emptyList()).stateAt(3_600)

        assertFalse(state.hasClass)
        assertFalse(state.isComplete)
        assertNull(state.current)
        assertNull(state.next)
    }

    // ── Preview ─────────────────────────────────────────────────────

    @Test
    fun `previews the upcoming interval`() {
        val state = ClassIntervalEngine(standardClass).stateAt(100)

        assertEquals(standardClass[1], state.current)
        assertEquals(standardClass[2], state.next)
    }

    @Test
    fun `never previews a next interval on the final one`() {
        // Item 9.3a.5: a "next up" card on the last interval promises something
        // the class cannot deliver.
        val state = ClassIntervalEngine(standardClass).stateAt(330)

        assertEquals(5, state.index)
        assertNull(state.next)
    }

    // ── Change warning ──────────────────────────────────────────────

    @Test
    fun `warns for the five seconds before an interval change`() {
        val engine = ClassIntervalEngine(standardClass)

        assertFalse(engine.stateAt(54).isChangeImminent) // 6s to go
        assertTrue(engine.stateAt(55).isChangeImminent) // 5s
        assertTrue(engine.stateAt(59).isChangeImminent) // 1s
    }

    @Test
    fun `does not warn of a change that will never come`() {
        // The final interval has nothing to warn about.
        assertFalse(ClassIntervalEngine(standardClass).stateAt(359).isChangeImminent)
    }

    // ── Coaching cues (9_3a_5) ──────────────────────────────────────

    @Test
    fun `the closing effort cue lands on the last hard interval, not the last one`() {
        val engine = ClassIntervalEngine(standardClass)

        // Interval 4 (240-300s, Z5) is the last hard effort; interval 5 is a
        // Z1 spin-down. Shouting "give it everything" over a cooldown is worse
        // than saying nothing.
        assertEquals(RideCue.FinalPush, engine.stateAt(250).cue)
        assertEquals(RideCue.CoolDown, engine.stateAt(310).cue)
    }

    @Test
    fun `an earlier hard interval gets no closing cue`() {
        assertEquals(RideCue.None, ClassIntervalEngine(standardClass).stateAt(150).cue)
    }

    @Test
    fun `a class ending on a hard effort gets the cue on its final interval`() {
        val sprintFinish = listOf(
            interval(0, 60, 1),
            interval(60, 120, 4),
            interval(120, 180, 6)
        )

        val engine = ClassIntervalEngine(sprintFinish)

        assertEquals(RideCue.None, engine.stateAt(90).cue)
        assertEquals(RideCue.FinalPush, engine.stateAt(150).cue)
    }

    @Test
    fun `a class with no hard interval at all gets no push cue`() {
        val recoveryRide = listOf(interval(0, 60, 2), interval(60, 120, 1))

        val engine = ClassIntervalEngine(recoveryRide)

        assertEquals(RideCue.None, engine.stateAt(30).cue)
        assertEquals(RideCue.CoolDown, engine.stateAt(90).cue)
    }

    @Test
    fun `a tempo finish is neither a push nor a cooldown`() {
        // Z3 is neither hard enough to empty the tank on nor easy enough to
        // call a cooldown.
        val tempoFinish = listOf(interval(0, 60, 2), interval(60, 120, 3))

        assertEquals(RideCue.None, ClassIntervalEngine(tempoFinish).stateAt(90).cue)
    }

    // ── Progress ────────────────────────────────────────────────────

    @Test
    fun `interval progress runs from zero to one within the segment`() {
        val engine = ClassIntervalEngine(standardClass)

        assertEquals(0f, engine.stateAt(60).intervalProgress, 0.0001f)
        assertEquals(0.5f, engine.stateAt(90).intervalProgress, 0.0001f)
        assertEquals(0.25f, engine.stateAt(75).intervalProgress, 0.0001f)
    }

    @Test
    fun `class progress is measured against the whole class`() {
        val engine = ClassIntervalEngine(standardClass)

        assertEquals(0f, engine.stateAt(0).classProgress, 0.0001f)
        assertEquals(0.5f, engine.stateAt(180).classProgress, 0.0001f)
    }

    @Test
    fun `a negative clock is clamped rather than indexing backwards`() {
        val state = ClassIntervalEngine(standardClass).stateAt(-5)

        assertEquals(0, state.index)
        assertEquals(0, state.elapsedInIntervalSec)
    }

    // ── Recovery ────────────────────────────────────────────────────

    @Test
    fun `flags recovery intervals so the UI can show the big countdown`() {
        val engine = ClassIntervalEngine(standardClass)

        assertTrue(engine.stateAt(30).isRecovering) // Z1 warmup
        assertTrue(engine.stateAt(200).isRecovering) // Z2 float
        assertFalse(engine.stateAt(150).isRecovering) // Z5 effort
    }

    @Test
    fun `exposes the prescribed zone for the target readout`() {
        assertEquals(PowerZone.Z5, ClassIntervalEngine(standardClass).stateAt(150).targetZone)
        // A free ride has no prescription, so the UI falls back to tempo.
        assertEquals(PowerZone.Z3, IntervalState.NONE.targetZone)
    }
}
