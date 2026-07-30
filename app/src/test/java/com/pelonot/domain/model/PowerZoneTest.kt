package com.pelonot.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerZoneTest {

    private val ftp = 200.0

    @Test
    fun `assigns the documented zone for a representative power in each band`() {
        val expectations = mapOf(
            100.0 to PowerZone.Z1, //  50% of FTP
            120.0 to PowerZone.Z2, //  60%
            160.0 to PowerZone.Z3, //  80%
            200.0 to PowerZone.Z4, // 100%
            230.0 to PowerZone.Z5, // 115%
            260.0 to PowerZone.Z6, // 130%
            350.0 to PowerZone.Z7  // 175%
        )

        expectations.forEach { (power, expected) ->
            assertEquals("power $power", expected, PowerZone.forPower(power, ftp))
        }
    }

    @Test
    fun `zone bands are contiguous so no power falls between them`() {
        // Regression: the old ranges were 0.0..0.55, 0.56..0.75, … and a lookup
        // that missed every range fell through to Z7. A rider at 55.5% of FTP —
        // an easy warmup — was therefore reported as Neuromuscular Power.
        assertEquals(PowerZone.Z1, PowerZone.forPower(ftp * 0.555, ftp))
        assertEquals(PowerZone.Z2, PowerZone.forPower(ftp * 0.755, ftp))
        assertEquals(PowerZone.Z3, PowerZone.forPower(ftp * 0.905, ftp))
        assertEquals(PowerZone.Z4, PowerZone.forPower(ftp * 1.055, ftp))
        assertEquals(PowerZone.Z5, PowerZone.forPower(ftp * 1.205, ftp))
        assertEquals(PowerZone.Z6, PowerZone.forPower(ftp * 1.505, ftp))
    }

    @Test
    fun `every zone boundary belongs to the higher zone`() {
        PowerZone.entries.forEach { zone ->
            assertEquals(
                "lower bound of $zone",
                zone,
                PowerZone.forPower(ftp * zone.lowerBound + 0.001, ftp)
            )
        }
    }

    @Test
    fun `sweeping the whole power range never skips or repeats a zone`() {
        var previous = PowerZone.Z1
        var watts = 1.0
        while (watts <= ftp * 2.0) {
            val zone = PowerZone.forPower(watts, ftp)
            assertTrue(
                "zone went backwards at ${watts}W: $previous -> $zone",
                zone.number >= previous.number
            )
            assertTrue(
                "zone skipped at ${watts}W: $previous -> $zone",
                zone.number - previous.number <= 1
            )
            previous = zone
            watts += 0.5
        }
        assertEquals(PowerZone.Z7, previous)
    }

    @Test
    fun `falls back to Z1 when FTP is unknown`() {
        assertEquals(PowerZone.Z1, PowerZone.forPower(200.0, 0.0))
        assertEquals(PowerZone.Z1, PowerZone.forPower(200.0, -100.0))
    }

    @Test
    fun `zero power is Z1 regardless of FTP`() {
        assertEquals(PowerZone.Z1, PowerZone.forPower(0.0, ftp))
    }

    @Test
    fun `reaching for milestones scales targets up by five percent`() {
        val range = PowerZone.Z4.targetPowerRange(ftp, RideIntent.ReachNewMilestones)
        assertEquals(200 * 0.91 * 1.05, range.start, 0.01)
        assertEquals(200 * 1.06 * 1.05, range.endInclusive, 0.01)
    }

    @Test
    fun `staying fit scales targets down by five percent`() {
        val range = PowerZone.Z4.targetPowerRange(ftp, RideIntent.JustStayFit)
        assertEquals(200 * 0.91 * 0.95, range.start, 0.01)
        assertEquals(200 * 1.06 * 0.95, range.endInclusive, 0.01)
    }

    @Test
    fun `an unrecognised intent id falls back to the default rather than 1x`() {
        assertEquals(RideIntent.DEFAULT, RideIntent.fromId("typo"))
        assertEquals(RideIntent.DEFAULT, RideIntent.fromId(null))
        assertEquals(RideIntent.ReachNewMilestones, RideIntent.fromId("reach_new_milestones"))
    }
}
