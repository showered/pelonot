package com.pelonot.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a ride's watts came from (PLAN 16.1.6, and the column 7.10.7 and
 * 24.4.2 were waiting on).
 *
 * The rule underneath all of it: **a modelled watt is never presented as a
 * measured one.** Everything here is that rule asked of the four states a
 * ride's samples can be in.
 */
class PowerProvenanceTest {

    @Test
    fun `all off the board is measured`() {
        assertEquals(
            PowerProvenance.Measured,
            PowerProvenance.of(measured = 900, modelled = 0, unknown = 0)
        )
    }

    @Test
    fun `all out of the model is modelled`() {
        assertEquals(
            PowerProvenance.Modelled,
            PowerProvenance.of(measured = 0, modelled = 900, unknown = 0)
        )
    }

    /** A board that drops out mid-ride and comes back leaves exactly this. */
    @Test
    fun `some of each is mixed`() {
        assertEquals(
            PowerProvenance.Mixed,
            PowerProvenance.of(measured = 800, modelled = 100, unknown = 0)
        )
    }

    @Test
    fun `a ride recorded before the column existed is unknown`() {
        assertEquals(
            PowerProvenance.Unknown,
            PowerProvenance.of(measured = 0, modelled = 0, unknown = 900)
        )
    }

    /**
     * The case that decides whether the column is honest. A ride upgraded
     * mid-way through — old samples with no answer, new ones with — cannot be
     * shown to be measurement all the way through, and "mostly measured" is
     * not a claim this app is allowed to round up.
     */
    @Test
    fun `one unrecorded sample makes the whole ride unknown`() {
        assertEquals(
            PowerProvenance.Unknown,
            PowerProvenance.of(measured = 899, modelled = 0, unknown = 1)
        )
    }

    @Test
    fun `a ride with no samples claims nothing`() {
        assertEquals(
            PowerProvenance.Unknown,
            PowerProvenance.of(measured = 0, modelled = 0, unknown = 0)
        )
    }

    /**
     * The single question everything downstream actually asks: may this ride
     * become a *fact* — an FTP change (7.10.7), a place on a leaderboard
     * beside somebody's real ride (24.4.2)?
     */
    @Test
    fun `only a wholly measured ride may be treated as measurement`() {
        assertTrue(PowerProvenance.Measured.isTrustworthyAsMeasured)
        assertFalse(PowerProvenance.Modelled.isTrustworthyAsMeasured)
        assertFalse(
            "half a ride of invented watts still moves a twenty-minute peak",
            PowerProvenance.Mixed.isTrustworthyAsMeasured
        )
        assertFalse(PowerProvenance.Unknown.isTrustworthyAsMeasured)
    }
}
