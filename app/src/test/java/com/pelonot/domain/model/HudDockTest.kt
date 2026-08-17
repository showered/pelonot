package com.pelonot.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The four edges (11.1b.4) and the drag rule that reaches them.
 *
 * The drag is the only part of the overlay that is a decision rather than a
 * drawing, which is why it lives here and not in the composable: a rule that
 * decides where a rider's HUD goes should be checkable without a window.
 */
class HudDockTest {

    private val threshold = 40f

    @Test
    fun `a drag past the threshold reaches the edge it points at`() {
        assertEquals(HudDock.Bottom, HudDock.dragTarget(HudDock.Top, 0f, 60f, threshold))
        assertEquals(HudDock.Top, HudDock.dragTarget(HudDock.Bottom, 0f, -60f, threshold))
        assertEquals(HudDock.Right, HudDock.dragTarget(HudDock.Top, 60f, 0f, threshold))
        assertEquals(HudDock.Left, HudDock.dragTarget(HudDock.Right, -60f, 0f, threshold))
    }

    @Test
    fun `a side is reachable from a side, not only from a band`() {
        assertEquals(HudDock.Right, HudDock.dragTarget(HudDock.Left, 200f, 0f, threshold))
        assertEquals(HudDock.Top, HudDock.dragTarget(HudDock.Left, 0f, -90f, threshold))
    }

    @Test
    fun `brushing the handle moves nothing`() {
        assertNull(HudDock.dragTarget(HudDock.Top, 0f, 12f, threshold))
        assertNull(HudDock.dragTarget(HudDock.Top, 39f, 39f, threshold))
    }

    /**
     * The whole reason the dominant axis decides. A rider dragging the strip
     * down the screen is not asking for the left edge because their hand
     * wandered 20 dp on the way.
     */
    @Test
    fun `a drag that wanders is read as where it mostly went`() {
        assertEquals(HudDock.Bottom, HudDock.dragTarget(HudDock.Top, 20f, 120f, threshold))
        assertEquals(HudDock.Left, HudDock.dragTarget(HudDock.Top, -120f, 20f, threshold))
    }

    /**
     * A dead-level diagonal has to resolve to something rather than flicker
     * between two answers on adjacent frames; horizontal wins, and it is
     * written down rather than left to `>=` in a comparison.
     */
    @Test
    fun `an exactly diagonal drag resolves horizontally`() {
        assertEquals(HudDock.Right, HudDock.dragTarget(HudDock.Top, 80f, 80f, threshold))
    }

    @Test
    fun `a drag back towards the strip's own edge asks for nothing`() {
        assertNull(HudDock.dragTarget(HudDock.Bottom, 0f, 200f, threshold))
        assertNull(HudDock.dragTarget(HudDock.Left, -200f, 0f, threshold))
    }

    @Test
    fun `only the sides are vertical`() {
        assertTrue(HudDock.Left.isVertical)
        assertTrue(HudDock.Right.isVertical)
        assertFalse(HudDock.Top.isVertical)
        assertFalse(HudDock.Bottom.isVertical)
    }

    /**
     * Time runs left to right, so the timeline is a horizontal bar on every
     * dock — which means a vertical strip does *not* send it to the opposite
     * side. It takes the top edge, because the bottom is where subtitles are.
     */
    @Test
    fun `the timeline never shares an edge with the strip`() {
        HudDock.entries.forEach { dock ->
            assertTrue(
                "$dock puts the timeline on its own edge",
                dock.timelineEdge() != dock
            )
        }
        assertEquals(HudDock.Bottom, HudDock.Top.timelineEdge())
        assertEquals(HudDock.Top, HudDock.Bottom.timelineEdge())
        assertEquals(HudDock.Top, HudDock.Left.timelineEdge())
        assertEquals(HudDock.Top, HudDock.Right.timelineEdge())
    }

    @Test
    fun `opposite is a reflection and every edge has one`() {
        HudDock.entries.forEach { dock ->
            assertEquals(dock, dock.opposite().opposite())
            assertTrue(dock.opposite() != dock)
            assertEquals(
                "$dock's opposite changes edge without changing axis",
                dock.isVertical,
                dock.opposite().isVertical
            )
        }
    }

    /**
     * The stored value is a name, and an unknown one has to land somewhere
     * sensible: a rider whose preference file predates the two new edges, or
     * postdates a renaming, gets the default rather than a crash.
     */
    @Test
    fun `an unknown stored name falls back to the default`() {
        assertEquals(HudDock.DEFAULT, HudDock.fromName(null))
        assertEquals(HudDock.DEFAULT, HudDock.fromName("Sideways"))
        HudDock.entries.forEach { assertEquals(it, HudDock.fromName(it.name)) }
    }

    /**
     * Two widths down a side, one of them much narrower (11.1b.11).
     *
     * The rule is small and the thing it protects is not: the timeline bar
     * insets itself by whatever this returns, so a width that did not follow the
     * collapse would leave the bar making room for a strip that is no longer
     * there.
     */
    @Test
    fun `a collapsed vertical strip is much narrower, and a horizontal one has no width of its own`() {
        listOf(HudDock.Left, HudDock.Right).forEach { dock ->
            assertEquals(
                HudDock.VERTICAL_WIDTH_DP,
                HudDock.widthDp(dock, collapsed = false)
            )
            assertEquals(
                HudDock.VERTICAL_COLLAPSED_WIDTH_DP,
                HudDock.widthDp(dock, collapsed = true)
            )
        }
        assertTrue(
            "collapsing has to buy back real width, not a few dp",
            HudDock.VERTICAL_COLLAPSED_WIDTH_DP < HudDock.VERTICAL_WIDTH_DP * 0.75
        )

        // A horizontal dock spans the edge, so it has no width to report and
        // nothing insets itself from it.
        listOf(HudDock.Top, HudDock.Bottom).forEach { dock ->
            assertEquals(0, HudDock.widthDp(dock, collapsed = false))
            assertEquals(0, HudDock.widthDp(dock, collapsed = true))
        }
    }
}
