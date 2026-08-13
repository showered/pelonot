package com.pelonot.data.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The two schedules in [ReconnectPolicy], and the line between them (2.7.7,
 * 2.7.8).
 *
 * The behaviour being pinned is not "does it back off" — it is that a source
 * which delivered nothing is treated differently from one that delivered
 * something and then stopped, because on this bike the first case is a leaked
 * serial port and every rebind makes it worse.
 */
class ReconnectPolicyTest {

    private fun quiet() = SensorBoardNotAnswering(4_000)

    @Test
    fun `a dropout after real readings retries quickly and forever`() {
        val policy = ReconnectPolicy()

        // Twenty dropouts, each after a bind that worked. Nothing here is the
        // leaked-port condition, so nothing here may give up: on the bike the
        // board went quiet at 86 s and picked up again at 122 s.
        repeat(20) {
            val decision = policy.onFailure(TelemetrySilence(6_000), producedReadings = true)
            assertEquals(Reconnect.After(1_000, 1), decision)
        }
        assertEquals(0, policy.barrenAttempts)
    }

    @Test
    fun `a bind that delivered nothing waits longer than one that worked`() {
        val productive = ReconnectPolicy()
            .onFailure(TelemetrySilence(6_000), producedReadings = true)
        val barren = ReconnectPolicy().onFailure(quiet(), producedReadings = false)

        assertEquals(1_000L, (productive as Reconnect.After).delayMs)
        assertEquals(3_000L, (barren as Reconnect.After).delayMs)
    }

    @Test
    fun `five barren attempts give up, and say the board is not answering`() {
        val policy = ReconnectPolicy()

        val delays = (1..4).map {
            (policy.onFailure(quiet(), producedReadings = false) as Reconnect.After).delayMs
        }
        assertEquals(listOf(3_000L, 6_000L, 12_000L, 24_000L), delays)

        val decision = policy.onFailure(quiet(), producedReadings = false)
        assertEquals(
            Reconnect.GiveUp(5, SensorUnavailableReason.BoardNotAnswering),
            decision
        )
    }

    @Test
    fun `one good bind clears the barren run`() {
        val policy = ReconnectPolicy()
        repeat(4) { policy.onFailure(quiet(), producedReadings = false) }
        assertEquals(4, policy.barrenAttempts)

        // The rider closed whatever had the port; the source bound, delivered,
        // and then dropped out. That is a different problem and the count that
        // was about to end the ride must not be carried into it.
        policy.onFailure(TelemetrySilence(6_000), producedReadings = true)
        assertEquals(0, policy.barrenAttempts)

        val next = policy.onFailure(quiet(), producedReadings = false)
        assertTrue(next is Reconnect.After)
    }

    @Test
    fun `a missing service gives up at once rather than waiting`() {
        val decision = ReconnectPolicy()
            .onFailure(SensorServiceMissing("not installed"), producedReadings = false)

        assertEquals(
            Reconnect.GiveUp(1, SensorUnavailableReason.ServiceMissing),
            decision
        )
    }

    @Test
    fun `a barren run of unexplained failures gives up without blaming the board`() {
        val policy = ReconnectPolicy()
        repeat(4) { policy.onFailure(IOException("bind refused"), producedReadings = false) }

        assertEquals(
            Reconnect.GiveUp(5, SensorUnavailableReason.NeverStarted),
            policy.onFailure(IOException("bind refused"), producedReadings = false)
        )
    }

    @Test
    fun `the wait is capped so a dead board does not drift into minutes`() {
        val policy = ReconnectPolicy(maxBarrenAttempts = Int.MAX_VALUE)
        var last = 0L
        repeat(30) {
            last = (policy.onFailure(quiet(), producedReadings = false) as Reconnect.After).delayMs
        }
        assertEquals(30_000L, last)
    }

    @Test
    fun `reset forgets the run`() {
        val policy = ReconnectPolicy()
        repeat(4) { policy.onFailure(quiet(), producedReadings = false) }
        policy.reset()

        assertEquals(
            Reconnect.After(3_000, 1),
            policy.onFailure(quiet(), producedReadings = false)
        )
    }
}
