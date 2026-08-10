package com.pelonot.domain.progress

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * PLAN 22.1.5 and 22.1.7.
 *
 * The comparison itself is one `>`; every test here is about a **refusal**,
 * because that is where this can go wrong in a way nobody sees — a claim drawn
 * on the first screen of the app, about the rider's own record, on evidence
 * that does not support it.
 */
class LastRideStandingTest {

    private val measured = listOf(180.0, 210.0, 195.0)

    @Test
    fun `beating every earlier measured ride of the class is a best`() {
        assertEquals(
            RideStanding.Best,
            LastRideStanding.of("CLB-01", outputKj = 211.0, isMeasured = true, measured)
        )
    }

    @Test
    fun `not beating them is not a best, and is still a comparison`() {
        assertEquals(
            RideStanding.NotBest,
            LastRideStanding.of("CLB-01", outputKj = 209.0, isMeasured = true, measured)
        )
    }

    /**
     * Equalling the best is not beating it. The `>` is deliberate: a rider who
     * matches their own record to the kilojoule has not set a new one, and
     * `ownTotalsForClassExcluding` exists so that the tying ride is genuinely
     * still on the list to be tied with.
     */
    @Test
    fun `matching the best exactly is not a best`() {
        assertEquals(
            RideStanding.NotBest,
            LastRideStanding.of("CLB-01", outputKj = 210.0, isMeasured = true, measured)
        )
    }

    // ── The three refusals ──────────────────────────────────────────

    /** A free ride is not a repeat of anything. */
    @Test
    fun `a ride with no class makes no claim`() {
        assertEquals(
            RideStanding.Unclaimed,
            LastRideStanding.of(null, outputKj = 500.0, isMeasured = true, measured)
        )
    }

    /**
     * 22.1.7. `PowerModel` scores RMSE 137 W, so a simulated ride placed above
     * a measured one is the app inventing a personal best — and this is the
     * branch every emulator ride takes, which is why nothing here can be
     * confirmed by looking at an AVD.
     */
    @Test
    fun `modelled watts make no claim, however big the number`() {
        assertEquals(
            RideStanding.Unclaimed,
            LastRideStanding.of("CLB-01", outputKj = 900.0, isMeasured = false, measured)
        )
    }

    /** A best computed from one ride is noise wearing a trophy (22.1.6). */
    @Test
    fun `the first ride of a class makes no claim`() {
        assertEquals(
            RideStanding.Unclaimed,
            LastRideStanding.of("CLB-01", outputKj = 210.0, isMeasured = true, emptyList())
        )
    }

    /**
     * The earlier rides are already filtered to measured ones by the query, so
     * an empty list here is also what a rider gets when every previous attempt
     * was simulated — the same refusal, arriving from the other side.
     */
    @Test
    fun `earlier rides that were all modelled leave nothing to beat`() {
        assertEquals(
            RideStanding.Unclaimed,
            LastRideStanding.of("CLB-01", outputKj = 210.0, isMeasured = true, emptyList())
        )
    }
}
