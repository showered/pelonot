package com.pelonot.domain.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "It failed" against "it will always fail" (PLAN 14.2.7).
 *
 * The distinction is worth a test because getting it backwards is expensive in
 * both directions: call a transient failure permanent and a ride is skipped
 * over a router reboot; call a permanent one transient and — this is the one
 * that actually happened — **one unacceptable row blocks every ride behind it
 * for ever**, with the rider told their backup is failing and no action
 * available to them that could fix it.
 */
class SyncFailureTest {

    @Test
    fun `nothing answering at all is the clearest transient signal there is`() {
        assertFalse(SyncFailure.isPermanent(null))
    }

    @Test
    fun `a malformed row will be just as malformed tomorrow`() {
        // The real one: `invalid input syntax for type uuid: "r1"`.
        assertTrue(SyncFailure.isPermanent(400))
        assertTrue(SyncFailure.isPermanent(409))
        assertTrue(SyncFailure.isPermanent(413))
        assertTrue(SyncFailure.isPermanent(422))
    }

    /**
     * The two 4xx codes that are **not** permanent, and both matter here.
     * An expired session is fixed by a refresh, and a policy refusal is what
     * 15.2.8's mismatch looks like from the wire — a state that ends when the
     * rider signs in.
     */
    @Test
    fun `an expired session and a policy refusal are not permanent`() {
        assertFalse(SyncFailure.isPermanent(401))
        assertFalse(SyncFailure.isPermanent(403))
    }

    @Test
    fun `the endpoint asking to be left alone is not permanent`() {
        assertFalse(SyncFailure.isPermanent(408))
        assertFalse(SyncFailure.isPermanent(429))
        assertFalse(SyncFailure.isPermanent(500))
        assertFalse(SyncFailure.isPermanent(502))
        assertFalse(SyncFailure.isPermanent(503))
    }

    /**
     * What the tablet actually rendered: eight lines of red, most of it a
     * percent-encoded column list, with the button that fixes the problem
     * pushed off the bottom of the card.
     */
    @Test
    fun `the rider gets the sentence, not the request`() {
        val raw = """
            invalid input syntax for type uuid: "r1"
            URL: https://x.supabase.co/rest/v1/workouts?columns=id%2Cuser_id%2Cduration_sec
            Headers:
        """.trimIndent()

        assertEquals("invalid input syntax for type uuid: \"r1\"", SyncFailure.riderFacing(raw))
    }

    @Test
    fun `a URL on the same line is cut too`() {
        assertEquals(
            "Bad Request",
            SyncFailure.riderFacing("Bad Request URL: https://x.supabase.co/rest/v1/workouts")
        )
    }

    @Test
    fun `a short honest message survives intact`() {
        assertEquals("Network is unreachable", SyncFailure.riderFacing("Network is unreachable"))
    }

    @Test
    fun `something rather than nothing when there is no message`() {
        assertTrue(SyncFailure.riderFacing(null).isNotEmpty())
        assertTrue(SyncFailure.riderFacing("   \n  ").isNotEmpty())
    }

    @Test
    fun `a very long single line is capped`() {
        val long = "x".repeat(400)
        assertTrue(SyncFailure.riderFacing(long).length < 200)
        assertTrue(SyncFailure.riderFacing(long).endsWith("…"))
    }
}
