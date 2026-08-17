package com.pelonot.domain.coach

import com.pelonot.domain.model.GovernedBy
import com.pelonot.domain.model.IntervalState
import com.pelonot.domain.model.PowerZone
import com.pelonot.domain.model.RideCue
import com.pelonot.domain.model.RidePosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 11.8.4 — the coach's cues, printed.
 *
 * The rule this pins hardest is that **a caption is not a transcript**. The
 * speech strings are written for a synthesiser and one of them spells `rpm` out
 * as three letters; printing that would put a spelling mistake on the ride
 * screen, and it is the kind of thing that survives a diff review and does not
 * survive a rider.
 */
class RideCaptionTest {

    private val intervalChange = RideAlert.IntervalChange(
        zone = PowerZone.Z4,
        cadenceMin = 80,
        cadenceMax = 90
    )

    // ---- a caption is not a transcript ----

    @Test
    fun `a cadence-governed block spells rpm for the eye, not for the engine`() {
        val alert = intervalChange.copy(governedBy = GovernedBy.Cadence)

        assertTrue("80 to 90 R P M." in alert.speech)
        assertEquals("Zone 4 · Lactate Threshold · 80–90 rpm", alert.caption)
    }

    @Test
    fun `a power-governed block names the zone and nothing about cadence`() {
        assertEquals("Zone 4 · Lactate Threshold", intervalChange.caption)
    }

    /**
     * The zone's *name* is on the caption for 11.8's own reason: a first-time
     * rider cannot read `Z4`, and the word is what the number means.
     */
    @Test
    fun `every interval caption carries the zone's name`() {
        PowerZone.entries.forEach { zone ->
            val caption = intervalChange.copy(zone = zone).caption

            assertTrue(zone.displayName in caption)
        }
    }

    @Test
    fun `a position change leads the caption the way it leads the speech`() {
        val alert = intervalChange.copy(positionChange = RidePosition.Standing)

        assertTrue(alert.caption.startsWith(RidePosition.Standing.instruction))
    }

    // ---- silence ----

    /**
     * The countdown buzzes every second and speaks once. A line that changed
     * four times in five seconds is exactly the moving text 11.8.4 argues
     * against, so the silent ticks are silent here too.
     */
    @Test
    fun `only the first countdown tick has anything to print`() {
        val first = RideAlert.ChangeWarning(IntervalState.WARNING_SEC, PowerZone.Z5)
        val later = RideAlert.ChangeWarning(IntervalState.WARNING_SEC - 1, PowerZone.Z5)

        assertEquals("Zone 5 in five", first.caption)
        assertNull(later.caption)
    }

    /** And a tick with nothing to print does not wipe a sentence mid-read. */
    @Test
    fun `a silent alert leaves the line alone`() {
        val silent = listOf(RideAlert.ChangeWarning(IntervalState.WARNING_SEC - 1, PowerZone.Z5))

        assertNull(RideCaption.latest(silent, elapsedSec = 100))
    }

    // ---- which one wins ----

    /**
     * The tick a block ends on can raise both, and the sentence a rider needs
     * then is the block they are now in.
     */
    @Test
    fun `the last alert with something to say wins the line`() {
        val alerts = listOf(
            RideAlert.ChangeWarning(IntervalState.WARNING_SEC, PowerZone.Z5),
            intervalChange
        )

        assertEquals("Zone 4 · Lactate Threshold", RideCaption.latest(alerts, 100)?.text)
    }

    // ---- and it goes away ----

    @Test
    fun `a caption is visible for its window and not after it`() {
        val caption = RideCaption("Zone 4 · Threshold", raisedAtSec = 100)

        assertTrue(caption.isVisibleAt(100))
        assertTrue(caption.isVisibleAt(100 + RideCaption.SHOW_SEC - 1))
        assertFalse(caption.isVisibleAt(100 + RideCaption.SHOW_SEC))
    }

    /**
     * Everything the coach says can be printed, and nothing else can — the
     * caption set is the speech set, which is what keeps this a caption track
     * rather than a slot that must be filled.
     */
    @Test
    fun `every alert that speaks also prints`() {
        val speaking = listOf(
            intervalChange,
            RideAlert.ChangeWarning(IntervalState.WARNING_SEC, PowerZone.Z5),
            RideAlert.Cue(RideCue.FinalPush),
            RideAlert.OffTarget("Ease off a little"),
            RideAlert.ClassComplete
        )

        speaking.forEach { alert ->
            assertEquals(alert.speech != null, alert.caption != null)
        }
    }
}
