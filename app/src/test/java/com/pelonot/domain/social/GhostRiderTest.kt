package com.pelonot.domain.social

import com.pelonot.domain.model.Interval
import com.pelonot.domain.model.LiveLeaderboard
import com.pelonot.domain.model.PowerZone
import com.pelonot.domain.model.RideIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Generated targets on the live board (PLAN 24.3.18).
 *
 * The properties worth holding are the owner's two sentences: **there is
 * always something ahead**, and **humans are not crowded out**.
 */
class GhostRiderTest {

    private fun block(startSec: Int, endSec: Int, zone: Int) = Interval(
        startSec = startSec,
        endSec = endSec,
        cadenceMin = 80,
        cadenceMax = 90,
        powerZoneNumber = zone
    )

    // ---- the pace trace -------------------------------------------------

    @Test
    fun `a pace ghost finishes exactly on its total, at the final second`() {
        val trace = GhostRider.paceTrace(total = 300.0, durationSec = 1200)

        assertEquals(300.0, trace.finalValue, 0.001)
        assertEquals(1200, trace.finalSecond)
        assertEquals(150.0, trace.valueAt(600)!!, 1.0)
    }

    /** 24.3.6: past its own last second a competitor stops rather than runs on. */
    @Test
    fun `a pace ghost stops when the class does`() {
        val trace = GhostRider.paceTrace(total = 300.0, durationSec = 1200)

        assertNotNull(trace.valueAt(1200))
        assertNull(trace.valueAt(1201))
    }

    @Test
    fun `a ghost with nothing to ride for is empty rather than flat`() {
        assertTrue(GhostRider.paceTrace(total = 0.0, durationSec = 1200).isEmpty)
        assertTrue(GhostRider.paceTrace(total = 300.0, durationSec = 0).isEmpty)
    }

    // ---- the class as written -------------------------------------------

    /**
     * Z2 is 56–76% of FTP, so at 200 W FTP and the +5% goal the middle of the
     * band is 138.6 W, and ten minutes of it is 83.16 kJ.
     *
     * Asserted from the band rather than as a golden number, because the thing
     * worth holding is *where the watts came from*: this must never reach for
     * `PowerModel` — 2.2a.8 makes a third consumer of that curve a test failure
     * — and a zone target is watts already. Reading `PowerZone`'s own bounds
     * here is what makes the test fail if somebody ever routes it through the
     * model instead.
     */
    @Test
    fun `the plan is the prescribed watts, not a modelled guess`() {
        val zone = PowerZone.Z2
        val kj = GhostRider.prescribedTotalKj(
            intervals = listOf(block(0, 600, zone = zone.number)),
            ftpWatts = 200.0,
            intent = RideIntent.ReachNewMilestones
        )

        val bandMiddle = (zone.lowerBound + zone.upperBound) / 2 * 200.0 *
            RideIntent.ReachNewMilestones.multiplier
        assertEquals(bandMiddle * 600 / 1000.0, kj, 0.001)
    }

    @Test
    fun `the plan scales with the goal the rider chose`() {
        val intervals = listOf(block(0, 600, zone = 3))
        val harder = GhostRider.prescribedTotalKj(intervals, 200.0, RideIntent.ReachNewMilestones)
        val easier = GhostRider.prescribedTotalKj(intervals, 200.0, RideIntent.JustStayFit)

        assertTrue("the +5% goal must prescribe more work", harder > easier)
    }

    @Test
    fun `no FTP means no plan rather than a plan of zero`() {
        assertEquals(0.0, GhostRider.prescribedTotalKj(listOf(block(0, 600, 2)), 0.0), 0.0)
    }

    // ---- the ladder ------------------------------------------------------

    @Test
    fun `the next rung is strictly above where you already are`() {
        assertEquals(350.0, GhostRider.nextMilestone(300.0), 0.0)
        assertEquals(350.0, GhostRider.nextMilestone(301.0), 0.0)
        assertEquals(50.0, GhostRider.nextMilestone(0.0), 0.0)
    }

    /** The owner's requirement, as a property rather than an example. */
    @Test
    fun `there is a rung above every total`() {
        listOf(0.0, 1.0, 49.9, 50.0, 512.5, 10_000.0).forEach { total ->
            assertTrue(
                "nothing ahead of $total",
                GhostRider.nextMilestone(total) > total
            )
        }
    }

    @Test
    fun `the ladder ignores the rider's pace until it has settled`() {
        val pacer = LiveLeaderboard.Pacer(durationSec = 1200, floor = 100.0)

        // Ten seconds in and briefly flying: projecting would ask for 12,000 kJ.
        assertEquals(150.0, pacer.targetAt(second = 10, yourValue = 100.0), 0.0)
    }

    @Test
    fun `once settled the ladder rises with the rider`() {
        val pacer = LiveLeaderboard.Pacer(durationSec = 1200, floor = 100.0)

        // Half way, on 200 — projecting 400, so the rung above that is 450.
        assertEquals(450.0, pacer.targetAt(second = 600, yourValue = 200.0), 0.0)
    }

    // ---- selection and the cap ------------------------------------------

    @Test
    fun `a class nobody has ridden still has the plan on it`() {
        val ghosts = GhostRider.ghostsFor(
            intervals = listOf(block(0, 1200, zone = 3)),
            durationSec = 1200,
            ftpWatts = 200.0
        )

        assertEquals(listOf(GhostKind.Prescribed), ghosts.map { it.kind })
    }

    @Test
    fun `a rider with history gets all three, and never more`() {
        val ghosts = GhostRider.ghostsFor(
            intervals = listOf(block(0, 1200, zone = 3)),
            durationSec = 1200,
            ftpWatts = 200.0,
            personalBestKj = 300.0,
            ownTotalsKj = listOf(200.0, 250.0, 300.0)
        )

        assertEquals(GhostRider.MAX_GHOSTS, ghosts.size)
        assertEquals(
            listOf(GhostKind.Prescribed, GhostKind.Stretch, GhostKind.Usual),
            ghosts.map { it.kind }
        )
    }

    @Test
    fun `the stretch is five per cent past the rider's best`() {
        val ghosts = GhostRider.ghostsFor(
            intervals = emptyList(),
            durationSec = 1200,
            ftpWatts = 200.0,
            personalBestKj = 300.0
        )

        assertEquals(315.0, ghosts.single().trace.finalValue, 0.001)
    }

    @Test
    fun `two rides are not a usual`() {
        assertNull(GhostRider.usualTotal(listOf(200.0, 300.0)))
        assertEquals(250.0, GhostRider.usualTotal(listOf(200.0, 250.0, 300.0))!!, 0.0)
    }

    @Test
    fun `every generated row knows it is not a person`() {
        val ghosts = GhostRider.ghostsFor(
            intervals = listOf(block(0, 1200, zone = 3)),
            durationSec = 1200,
            ftpWatts = 200.0,
            personalBestKj = 300.0,
            ownTotalsKj = listOf(200.0, 250.0, 300.0)
        )

        assertTrue(ghosts.all { it.kind.isGhost })
        assertTrue(ghosts.none { it.kind.isPerson })
        assertTrue(ghosts.all { it.name.isNotBlank() })
    }
}
