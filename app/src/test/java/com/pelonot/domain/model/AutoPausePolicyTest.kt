package com.pelonot.domain.model

import com.pelonot.domain.model.AutoPausePolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoPausePolicyTest {

    private val policy = AutoPausePolicy(stillnessSeconds = 20)

    private fun ride(from: Int, to: Int, cadence: Double, live: Boolean = true): Decision {
        var last = Decision.None
        for (sec in from..to) {
            last = policy.onTick(sec, cadence, telemetryLive = live, isPaused = false)
            if (last != Decision.None) return last
        }
        return last
    }

    @Test
    fun `pauses once the cranks have been still for the whole window`() {
        assertEquals(Decision.None, ride(0, 19, cadence = 0.0))
        assertEquals(Decision.Pause, policy.onTick(20, 0.0, telemetryLive = true, isPaused = false))
        assertTrue(policy.isAutoPaused)
    }

    @Test
    fun `a rider who stops for ten seconds is not interrupted`() {
        assertEquals(Decision.None, ride(0, 10, cadence = 0.0))
        // Back on the pedals: the clock starts again from scratch.
        assertEquals(Decision.None, policy.onTick(11, 84.0, telemetryLive = true, isPaused = false))
        assertEquals(Decision.None, ride(12, 30, cadence = 0.0))
    }

    @Test
    fun `resumes on the first turn of the cranks`() {
        ride(0, 20, cadence = 0.0)
        assertEquals(Decision.Resume, policy.onTick(20, 70.0, telemetryLive = true, isPaused = true))
        assertFalse(policy.isAutoPaused)
    }

    @Test
    fun `a stalled board is not a stopped rider`() {
        // 2.4.4 read the other way round: the reading is not zero, it is absent.
        assertEquals(Decision.None, ride(0, 60, cadence = 0.0, live = false))
        assertFalse(policy.isAutoPaused)
    }

    @Test
    fun `an auto-pause is not lifted by a stale reading`() {
        ride(0, 20, cadence = 0.0)
        assertTrue(policy.isAutoPaused)

        // A frozen 90 rpm arriving from a dead board must not put the ride back
        // into recording.
        assertEquals(Decision.None, policy.onTick(20, 90.0, telemetryLive = false, isPaused = true))
        assertTrue(policy.isAutoPaused)
    }

    @Test
    fun `a pause the rider asked for is theirs to lift`() {
        policy.onManualControl()

        // Pedalling again after a deliberate pause: nothing happens until they
        // press play. The app does not argue with the person on the bike.
        assertEquals(Decision.None, policy.onTick(30, 88.0, telemetryLive = true, isPaused = true))
        assertFalse(policy.isAutoPaused)
    }

    @Test
    fun `taking manual control during an auto-pause hands the pause over`() {
        ride(0, 20, cadence = 0.0)
        assertTrue(policy.isAutoPaused)

        policy.onManualControl()
        assertFalse(policy.isAutoPaused)
        assertEquals(Decision.None, policy.onTick(21, 92.0, telemetryLive = true, isPaused = true))
    }

    @Test
    fun `a stop after a stop pauses again`() {
        ride(0, 20, cadence = 0.0)
        policy.onTick(20, 70.0, telemetryLive = true, isPaused = true)

        assertEquals(Decision.None, ride(21, 40, cadence = 0.0))
        assertEquals(Decision.Pause, policy.onTick(41, 0.0, telemetryLive = true, isPaused = false))
    }

    @Test
    fun `the last fraction of a revolution does not count as pedalling`() {
        // The board reports a true zero when the cranks stop; anything under a
        // revolution a minute is the wheel coming to rest.
        assertEquals(Decision.None, ride(0, 19, cadence = 0.4))
        assertEquals(Decision.Pause, policy.onTick(20, 0.4, telemetryLive = true, isPaused = false))
    }
}
