package com.pelonot.domain.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The dashboard household panel's ceiling (24.1.8, applied to the panel).
 *
 * The defect this holds shut was measured rather than imagined: twelve profiles
 * on the tablet AVD produced twelve rows, because nothing between the query and
 * the card bounded the list.
 */
class HouseholdPanelTest {

    private fun rider(id: Int, rides: Int) = HouseholdRider(
        localUserId = id,
        name = "Rider $id",
        rides = rides,
        outputKj = rides * 100.0,
        lastRideAt = 0L,
        streakWeeks = 0
    )

    /** Query order, most active first — what the DAO already returns. */
    private fun household(size: Int) = (1..size).map { rider(it, rides = size - it + 1) }

    @Test
    fun `a household that fits is drawn whole`() {
        val panel = HouseholdPanel.of(household(4), youId = 2)

        assertEquals(4, panel.rows.size)
        assertEquals(0, panel.hidden)
        assertNull(panel.breakAfter)
    }

    @Test
    fun `exactly the maximum is not windowed`() {
        val panel = HouseholdPanel.of(household(HouseholdPanel.MAX_ROWS), youId = 1)

        assertEquals(HouseholdPanel.MAX_ROWS, panel.rows.size)
        assertEquals(0, panel.hidden)
        assertNull(panel.breakAfter)
    }

    @Test
    fun `a long household is capped and says how many it dropped`() {
        val panel = HouseholdPanel.of(household(12), youId = 2)

        assertEquals(HouseholdPanel.MAX_ROWS, panel.rows.size)
        assertEquals(6, panel.hidden)
        // The rider is inside the window already, so nothing is skipped.
        assertNull(panel.breakAfter)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), panel.rows.map { it.localUserId })
    }

    @Test
    fun `the rider's own row survives from below the cut`() {
        val panel = HouseholdPanel.of(household(12), youId = 11)

        assertEquals(HouseholdPanel.MAX_ROWS, panel.rows.size)
        assertEquals(11, panel.rows.last().localUserId)
        // Five from the top, then a jump to the rider.
        assertEquals(listOf(1, 2, 3, 4, 5, 11), panel.rows.map { it.localUserId })
        assertEquals(4, panel.breakAfter)
        assertEquals(6, panel.hidden)
    }

    @Test
    fun `the last row before the cut needs no break marker`() {
        val panel = HouseholdPanel.of(household(12), youId = 6)

        assertEquals(listOf(1, 2, 3, 4, 5, 6), panel.rows.map { it.localUserId })
        assertNull(panel.breakAfter)
    }

    @Test
    fun `a guest keeps no row of their own`() {
        val panel = HouseholdPanel.of(household(12), youId = null)

        assertEquals(listOf(1, 2, 3, 4, 5, 6), panel.rows.map { it.localUserId })
        assertEquals(6, panel.hidden)
        assertNull(panel.breakAfter)
    }

    /**
     * The count is of riders, not of rows: it is what the card prints, and
     * getting it from `size - MAX_ROWS` would be wrong by one whenever the
     * rider's own row was lifted.
     */
    @Test
    fun `the hidden count excludes the lifted row`() {
        val panel = HouseholdPanel.of(household(8), youId = 8)

        assertEquals(listOf(1, 2, 3, 4, 5, 8), panel.rows.map { it.localUserId })
        assertEquals(2, panel.hidden)
    }
}
