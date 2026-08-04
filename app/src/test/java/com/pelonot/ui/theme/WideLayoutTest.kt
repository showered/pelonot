package com.pelonot.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The arithmetic behind [WideGrid] (PLAN 22.4.1).
 *
 * Worth a test of its own because the failure is silent: a grid that forgets
 * the gaps between its cells comes out one column too wide and every cell ends
 * up *under* the minimum it was given, which looks like a slightly cramped
 * layout rather than like a bug.
 */
class WideLayoutTest {

    @Test
    fun `the bike's width takes five cells of 220`() {
        // 1280 dp, the panel's whole width, at the summary screen's tile size.
        // 5 × 220 + 4 × 12 = 1148; a sixth would need 1380.
        assertEquals(5, columnsFor(1280.dp, minCellWidth = 220.dp, spacing = 12.dp))
    }

    @Test
    fun `spacing is counted, not ignored`() {
        // Exactly four cells wide if the gaps were free; three once they cost.
        assertEquals(4, columnsFor(880.dp, minCellWidth = 220.dp, spacing = 0.dp))
        assertEquals(3, columnsFor(880.dp, minCellWidth = 220.dp, spacing = 24.dp))
    }

    @Test
    fun `a phone gets one column`() {
        assertEquals(1, columnsFor(392.dp, minCellWidth = 220.dp, spacing = 12.dp))
    }

    @Test
    fun `never fewer than one, however narrow`() {
        assertEquals(1, columnsFor(0.dp, minCellWidth = 220.dp, spacing = 12.dp))
        assertEquals(1, columnsFor(80.dp, minCellWidth = 220.dp, spacing = 12.dp))
    }

    @Test
    fun `a cap on columns is honoured`() {
        assertEquals(2, columnsFor(1280.dp, minCellWidth = 220.dp, spacing = 12.dp, maxColumns = 2))
    }

    @Test
    fun `a zero minimum does not divide by zero`() {
        assertEquals(1, columnsFor(1280.dp, minCellWidth = 0.dp, spacing = 12.dp))
    }

    // ── Balancing (the stray tile) ──────────────────────────────────

    @Test
    fun `six items that fit five wide become two rows of three`() {
        // Not five and a stray. This is the case seen on the tablet AVD.
        assertEquals(3, balancedColumns(itemCount = 6, fits = 5))
    }

    @Test
    fun `everything that fits in one row stays in one row`() {
        assertEquals(6, balancedColumns(itemCount = 6, fits = 6))
        assertEquals(5, balancedColumns(itemCount = 5, fits = 5))
        assertEquals(2, balancedColumns(itemCount = 2, fits = 6))
    }

    @Test
    fun `seven items that fit five wide become four and three`() {
        assertEquals(4, balancedColumns(itemCount = 7, fits = 5))
    }

    @Test
    fun `a single column stays a single column`() {
        assertEquals(1, balancedColumns(itemCount = 6, fits = 1))
    }

    @Test
    fun `it never returns zero`() {
        assertEquals(1, balancedColumns(itemCount = 0, fits = 5))
        assertEquals(1, balancedColumns(itemCount = 6, fits = 0))
    }
}
