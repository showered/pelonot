package com.pelonot.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneScaleTest {

    private val ftp = 200

    @Test
    fun `boundaries are the watts each zone starts at`() {
        val scale = ZoneScale.forRider(ftp = ftp, zone = PowerZone.Z2, powerWatts = 130.0)

        assertEquals(
            listOf(0, 112, 152, 182, 212, 242, 302),
            scale.segments.map { it.startWatts }
        )
        assertEquals(PowerZone.entries, scale.segments.map { it.zone })
    }

    @Test
    fun `each segment ends where the next one starts`() {
        val scale = ZoneScale.forRider(ftp = ftp, zone = null, powerWatts = null)

        scale.segments.zipWithNext { lower, upper ->
            assertEquals(
                "${lower.zone} ends where ${upper.zone} starts",
                lower.endWatts,
                upper.startWatts
            )
        }
    }

    @Test
    fun `position within a zone distinguishes just-in from nearly-out`() {
        // Z2 on a 200 W FTP runs 112..152 W.
        val justIn = ZoneScale.forRider(ftp = ftp, zone = PowerZone.Z2, powerWatts = 114.0)
        val nearlyOut = ZoneScale.forRider(ftp = ftp, zone = PowerZone.Z2, powerWatts = 150.0)

        assertEquals(0.05f, justIn.fractionThroughZone, 0.02f)
        assertEquals(0.95f, nearlyOut.fractionThroughZone, 0.02f)
    }

    @Test
    fun `the watts that reach the next zone are the instruction the label carries`() {
        val scale = ZoneScale.forRider(ftp = ftp, zone = PowerZone.Z2, powerWatts = 130.0)

        assertEquals(152, scale.wattsToNextZone)
    }

    @Test
    fun `the top zone has nothing above it`() {
        val scale = ZoneScale.forRider(ftp = ftp, zone = PowerZone.Z7, powerWatts = 340.0)

        assertNull(scale.wattsToNextZone)
    }

    @Test
    fun `no reading means no position and no percentage, not a zero one`() {
        // 2.4.4's rule in another costume: a bike nobody is pedalling must not
        // be drawn as a rider sitting at the bottom of Z1.
        val scale = ZoneScale.forRider(ftp = ftp, zone = null, powerWatts = null)

        assertNull(scale.current)
        assertNull(scale.ftpPercent)
        assertEquals(0f, scale.fractionThroughZone, 0f)
    }

    @Test
    fun `FTP percentage is the reading against the rider's own threshold`() {
        val scale = ZoneScale.forRider(ftp = ftp, zone = PowerZone.Z4, powerWatts = 196.0)

        assertEquals(98, scale.ftpPercent)
    }

    @Test
    fun `an unknown FTP yields a ladder with no position on it`() {
        val scale = ZoneScale.forRider(ftp = 0, zone = PowerZone.Z3, powerWatts = 180.0)

        assertEquals(0f, scale.fractionThroughZone, 0f)
        assertNull(scale.ftpPercent)
        assertEquals(List(7) { 0 }, scale.segments.map { it.startWatts })
    }

    @Test
    fun `a stalled board is not a rider in zone 1`() {
        // The one rule both surfaces ask through: a frozen reading is an
        // absence, and an absence has no zone (2.4.4).
        assertNull(ZoneScale.currentZone(ftp, powerWatts = 180.0, telemetryLive = false))
        assertNull(ZoneScale.currentZone(ftp, powerWatts = 0.0, telemetryLive = true))
        assertNull(ZoneScale.currentZone(ftp = 0, powerWatts = 180.0, telemetryLive = true))
        assertEquals(
            PowerZone.Z4,
            ZoneScale.currentZone(ftp, powerWatts = 195.0, telemetryLive = true)
        )
    }

    @Test
    fun `a reading with no zone carries no position onto the ladder`() {
        val scale = ZoneScale.forReading(ftp, powerWatts = 180.0, telemetryLive = false)

        assertNull(scale.current)
        assertNull(scale.ftpPercent)
        assertEquals(0f, scale.fractionThroughZone, 0f)
        // The ladder itself still draws — the boundaries do not depend on a reading.
        assertEquals(152, scale.segments[2].startWatts)
    }

    // ── The ladder's single coordinate (11.6.11) ────────────────────
    //
    // The defect these hold the line against: the scale used to be drawn from
    // `fractionThroughZone`, which resets to zero at every boundary, so the
    // animated fill was driven *backwards* across a whole segment on the way
    // into the next zone. Monotonicity is the property that says a rider
    // pushing harder never sees the bar go back.

    @Test
    fun `the ladder position never goes backwards as the rider pushes harder`() {
        // Every watt from a standstill to well past Z7's floor, which crosses
        // all six boundaries. Not a sampled few: the recoil lived exactly at
        // the boundaries and a coarse sweep steps over it.
        var previous = -1f
        for (watts in 1..400) {
            val scale = ZoneScale.forReading(
                ftp = ftp,
                powerWatts = watts.toDouble(),
                telemetryLive = true
            )
            val position = scale.ladderPosition
            assertTrue(
                "$watts W moved the ladder backwards: $previous -> $position",
                position >= previous
            )
            previous = position
        }
    }

    @Test
    fun `crossing a boundary moves the ladder by a sliver, not by a rung`() {
        // Z2 on a 200 W FTP ends at 152. One watt either side of it is one
        // watt of effort and must be one watt of movement — under the old
        // per-zone fraction these two differed by the full width of a segment.
        val below = ZoneScale.forReading(ftp, 151.0, telemetryLive = true)
        val above = ZoneScale.forReading(ftp, 153.0, telemetryLive = true)

        assertEquals(PowerZone.Z2, below.current)
        assertEquals(PowerZone.Z3, above.current)

        val step = above.ladderPosition - below.ladderPosition
        assertTrue("the boundary should be invisible, moved $step", step in 0f..0.02f)
    }

    @Test
    fun `the ladder spans the whole scale`() {
        // Nothing lit at the bottom of Z1, and the top rung full at 2x FTP,
        // where Z7's own range is capped.
        assertEquals(0f, ZoneScale.forReading(ftp, 0.5, telemetryLive = true).ladderPosition, 0.01f)
        assertEquals(1f, ZoneScale.forReading(ftp, 500.0, telemetryLive = true).ladderPosition, 0.001f)
        // And the rung boundaries land where the segments are drawn: seven
        // equal widths, so Z4 starts three sevenths along.
        assertEquals(
            3f / 7f,
            ZoneScale.forReading(ftp, 182.0, telemetryLive = true).ladderPosition,
            0.005f
        )
    }

    @Test
    fun `no reading empties the ladder rather than leaving it where it was`() {
        // 2.4.4 reaching the drawing: a bar holding its place over a board that
        // has gone quiet is the frozen-cadence lie in another costume.
        val dead = ZoneScale.forReading(ftp, powerWatts = 240.0, telemetryLive = false)

        assertNull(dead.current)
        assertEquals(0f, dead.ladderPosition, 0f)
    }

    @Test
    fun `a free ride prescribes nothing`() {
        val scale = ZoneScale.forRider(ftp = ftp, zone = PowerZone.Z3, powerWatts = 170.0)

        assertNull(scale.prescribed)
    }
}
