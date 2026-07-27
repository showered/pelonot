package com.pelonot.data.sensor

import org.junit.Test
import org.junit.Assert.*

class PowerZoneCalculatorTest {

    @Test
    fun `getZoneForPower returns Z1 for power below 55 percent of FTP`() {
        val ftp = 200.0
        val power = 100.0 // 50% of FTP
        val zone = PowerZoneCalculator.getZoneForPower(power, ftp)
        assertEquals(PowerZoneCalculator.PowerZone.Z1, zone)
    }

    @Test
    fun `getZoneForPower returns Z2 for power 56 to 75 percent of FTP`() {
        val ftp = 200.0
        val power = 120.0 // 60% of FTP
        val zone = PowerZoneCalculator.getZoneForPower(power, ftp)
        assertEquals(PowerZoneCalculator.PowerZone.Z2, zone)
    }

    @Test
    fun `getZoneForPower returns Z3 for power 76 to 90 percent of FTP`() {
        val ftp = 200.0
        val power = 160.0 // 80% of FTP
        val zone = PowerZoneCalculator.getZoneForPower(power, ftp)
        assertEquals(PowerZoneCalculator.PowerZone.Z3, zone)
    }

    @Test
    fun `getZoneForPower returns Z4 for power 91 to 105 percent of FTP`() {
        val ftp = 200.0
        val power = 200.0 // 100% of FTP
        val zone = PowerZoneCalculator.getZoneForPower(power, ftp)
        assertEquals(PowerZoneCalculator.PowerZone.Z4, zone)
    }

    @Test
    fun `getZoneForPower returns Z5 for power 106 to 120 percent of FTP`() {
        val ftp = 200.0
        val power = 230.0 // 115% of FTP
        val zone = PowerZoneCalculator.getZoneForPower(power, ftp)
        assertEquals(PowerZoneCalculator.PowerZone.Z5, zone)
    }

    @Test
    fun `getZoneForPower returns Z6 for power 121 to 150 percent of FTP`() {
        val ftp = 200.0
        val power = 260.0 // 130% of FTP
        val zone = PowerZoneCalculator.getZoneForPower(power, ftp)
        assertEquals(PowerZoneCalculator.PowerZone.Z6, zone)
    }

    @Test
    fun `getZoneForPower returns Z7 for power above 150 percent of FTP`() {
        val ftp = 200.0
        val power = 350.0 // 175% of FTP
        val zone = PowerZoneCalculator.getZoneForPower(power, ftp)
        assertEquals(PowerZoneCalculator.PowerZone.Z7, zone)
    }

    @Test
    fun `getZoneForPower returns Z1 for zero or negative FTP`() {
        val power = 200.0
        val zone = PowerZoneCalculator.getZoneForPower(power, 0.0)
        assertEquals(PowerZoneCalculator.PowerZone.Z1, zone)
        
        val zone2 = PowerZoneCalculator.getZoneForPower(power, -100.0)
        assertEquals(PowerZoneCalculator.PowerZone.Z1, zone2)
    }

    @Test
    fun `getTargetPower with Reach New Milestones intent scales up by 5 percent`() {
        val ftp = 200.0
        val zone = PowerZoneCalculator.PowerZone.Z4
        val targetRange = PowerZoneCalculator.getTargetPower(zone, ftp, "Reach New Milestones")
        
        // Z4 range: 91-105% of FTP, scaled by 1.05
        assertEquals(182.0, targetRange.start, 0.01) // 200 * 0.91 * 1.05
        assertEquals(220.5, targetRange.endInclusive, 0.01) // 200 * 1.05 * 1.05
    }

    @Test
    fun `getTargetPower with Just Stay Fit intent scales down by 5 percent`() {
        val ftp = 200.0
        val zone = PowerZoneCalculator.PowerZone.Z4
        val targetRange = PowerZoneCalculator.getTargetPower(zone, ftp, "Just Stay Fit")
        
        // Z4 range: 91-105% of FTP, scaled by 0.95
        assertEquals(171.8, targetRange.start, 0.01) // 200 * 0.91 * 0.95
        assertEquals(199.0, targetRange.endInclusive, 0.01) // 200 * 1.05 * 0.95
    }

    @Test
    fun `getTargetPower with unknown intent uses 1x multiplier`() {
        val ftp = 200.0
        val zone = PowerZoneCalculator.PowerZone.Z4
        val targetRange = PowerZoneCalculator.getTargetPower(zone, ftp, "Unknown Intent")
        
        // Z4 range: 91-105% of FTP, no scaling
        assertEquals(182.0, targetRange.start, 0.01) // 200 * 0.91
        assertEquals(210.0, targetRange.endInclusive, 0.01) // 200 * 1.05
    }
}