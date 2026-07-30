import org.junit.Test
import org.junit.Assert.*
import com.pelonot.data.sensor.PowerZoneCalculator

class PowerZoneCalculatorTest {

    @Test
    fun `getZoneForPower returns correct zone for power less than 55% of FTP`() {
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
        val targetRange = PowerZoneCalculator.getTargetPower(
            zone, ftp, "Reach New Milestones"
        )
        
        // Z4 base range is 91-105% of FTP
        // Start: 200 * 0.91 = 182, scaled by 1.05 = 191.1
        // End: 200 * 1.05 = 210, scaled by 1.05 = 220.5
        assertEquals(191.1, targetRange.start, 0.01)
        assertEquals(220.5, targetRange.endInclusive, 0.01)
    }

    @Test
    fun `getTargetPower with Just Stay Fit intent scales down by 5 percent`() {
        val ftp = 200.0
        val zone = PowerZoneCalculator.PowerZone.Z4
        val targetRange = PowerZoneCalculator.getTargetPower(
            zone, ftp, "Just Stay Fit"
        )
        
        // Z4 base range is 91-105% of FTP
        // Start: 200 * 0.91 = 182, scaled by 0.95 = 172.9
        // End: 200 * 1.05 = 210, scaled by 0.95 = 199.5
        assertEquals(172.9, targetRange.start, 0.01)
        assertEquals(199.5, targetRange.endInclusive, 0.01)
    }

    @Test
    fun `getTargetPower with unknown intent uses 1x multiplier`() {
        val ftp = 200.0
        val zone = PowerZoneCalculator.PowerZone.Z4
        val targetRange = PowerZoneCalculator.getTargetPower(
            zone, ftp, "Unknown Intent"
        )
        
        // Z4 base range is 91-105% of FTP
        assertEquals(182.0, targetRange.start, 0.01)
        assertEquals(210.0, targetRange.endInclusive, 0.01)
    }
}
