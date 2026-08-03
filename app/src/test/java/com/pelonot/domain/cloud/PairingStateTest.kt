package com.pelonot.domain.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The clock on the pairing screen (PLAN 15.6.6).
 *
 * Small, and worth having because the whole of this flow's honesty is in
 * *when it stops*: a code that has quietly expired while the rider went to
 * fetch their phone is the most confusing thing the feature can do, and the
 * boundary is exactly the sort of thing that is off by one until asked.
 */
class PairingStateTest {

    private fun waiting(expiresAtMs: Long) = PairingState.Waiting(
        code = "ABCD2345",
        url = "https://pelonot.example/link.html#ABCD2345",
        expiresAtMs = expiresAtMs
    )

    @Test
    fun `eight characters are read as two groups of four`() {
        assertEquals("ABCD 2345", waiting(0).formattedCode)
    }

    /**
     * A code of an unexpected length is shown as it came rather than sliced
     * into a shape it does not have. The server owns the alphabet and the
     * length; a client that reformats what it does not recognise is a client
     * that will one day display a code nobody can type.
     */
    @Test
    fun `a code of another length is left alone`() {
        assertEquals(
            "ABC123",
            PairingState.Waiting("ABC123", "https://x/#ABC123", 0).formattedCode
        )
    }

    @Test
    fun `the countdown reaches zero and stops there`() {
        val expiry = 300_000L
        assertEquals(300, waiting(expiry).secondsLeft(0L))
        assertEquals(150, waiting(expiry).secondsLeft(150_000L))
        assertEquals(0, waiting(expiry).secondsLeft(expiry))
        // Never negative: a screen counting backwards past zero is a screen
        // that should have stopped a minute ago.
        assertEquals(0, waiting(expiry).secondsLeft(expiry + 60_000L))
    }

    /**
     * Expiry is inclusive at the boundary, which is the safe direction: the
     * bike gives up a moment early rather than showing a code the server has
     * already forgotten.
     */
    @Test
    fun `the boundary counts as expired`() {
        val expiry = 300_000L
        assertFalse(waiting(expiry).hasExpired(expiry - 1))
        assertTrue(waiting(expiry).hasExpired(expiry))
        assertTrue(waiting(expiry).hasExpired(expiry + 1))
    }
}
