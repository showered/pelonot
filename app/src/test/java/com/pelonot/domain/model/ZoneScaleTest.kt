package com.pelonot.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `a free ride prescribes nothing`() {
        val scale = ZoneScale.forRider(ftp = ftp, zone = PowerZone.Z3, powerWatts = 170.0)

        assertNull(scale.prescribed)
    }
}
