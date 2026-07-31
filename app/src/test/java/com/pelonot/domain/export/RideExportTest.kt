package com.pelonot.domain.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class RideExportTest {

    /** 2026-07-31T18:24:00Z. */
    private val startedAt = 1_785_522_240_000L

    private fun ride(title: String = "Torque Repeats 20") = ExportRide(
        id = "ride-1",
        title = title,
        startedAtMillis = startedAt,
        durationSec = 3,
        totalOutputKj = 87.7,
        totalDistanceKm = 1.76,
        avgPower = 139.0,
        avgCadence = 80.0,
        avgHeartRate = 127.0
    )

    private val samples = listOf(
        ExportSample(0, 80.0, 35.0, 120.0, 96),
        ExportSample(1, 82.5, 36.0, 131.5, null),
        ExportSample(2, 85.0, 37.0, 145.0, 99)
    )

    @Test
    fun `csv carries one row per recorded second, in order`() {
        val lines = RideExport.csv(ride(), samples.reversed()).trim().lines()
        val rows = lines.filterNot { it.startsWith("#") }

        assertEquals("elapsed_sec,cadence_rpm,resistance_percent,power_watts,heart_rate_bpm", rows[0])
        assertEquals(4, rows.size)
        assertEquals(listOf("0", "1", "2"), rows.drop(1).map { it.substringBefore(',') })
    }

    /**
     * The rule this project has already fixed twice: null means the strap was
     * not reporting, and a zero in that column is a fabricated reading that
     * drags down any average taken of the file afterwards.
     */
    @Test
    fun `an absent heart rate is blank, never zero`() {
        val row = RideExport.csv(ride(), samples).lines().first { it.startsWith("1,") }

        assertTrue(row, row.endsWith(","))
        assertFalse(row, row.endsWith(",0"))
    }

    @Test
    fun `tcx carries a trackpoint per sample, with watts and cadence`() {
        val xml = RideExport.tcx(ride(), samples)

        assertEquals(3, Regex("<Trackpoint>").findAll(xml).count())
        assertTrue(xml.contains("<ns3:Watts>120</ns3:Watts>"))
        assertTrue(xml.contains("<Cadence>85</Cadence>"))
        // Trackpoint times are the ride's start plus the sample's own offset.
        assertTrue(xml.contains("<Time>2026-07-31T18:24:00Z</Time>"))
        assertTrue(xml.contains("<Time>2026-07-31T18:24:02Z</Time>"))
    }

    @Test
    fun `a sample with no strap gets no heart rate element at all`() {
        val xml = RideExport.tcx(ride(), samples)

        // Two of the three samples have one, and the third is simply absent
        // rather than present and zero.
        assertEquals(2, Regex("<HeartRateBpm>").findAll(xml).count())
        assertFalse(xml.contains("<Value>0</Value>"))
    }

    /**
     * The distance is on the lap and nowhere else, and that is deliberate:
     * `workout_metrics` has no speed or distance column, so a per-second figure
     * could only be invented. 12.4.3a.
     */
    @Test
    fun `distance is stated once, for the ride, and never per second`() {
        val xml = RideExport.tcx(ride(), samples)

        assertEquals(1, Regex("<DistanceMeters>").findAll(xml).count())
        assertTrue(xml.contains("<DistanceMeters>1760.00</DistanceMeters>"))
    }

    /**
     * A French device formats 91.5 as "91,5", which in a comma-separated file
     * is two columns and in XML is a parse error at the far end.
     */
    @Test
    fun `numbers are written in the file's grammar, not the rider's locale`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)
            val csv = RideExport.csv(ride(), samples)
            assertTrue(csv, csv.contains("1,82.50,36.00,131.50,"))
            assertTrue(RideExport.tcx(ride(), samples).contains("1760.00"))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `the filename sorts by date and survives a hostile class title`() {
        val name = RideExport.filename(ride("Hills / Climbs: 30%"), ExportFormat.Tcx)

        assertFalse(name, name.contains('/'))
        assertFalse(name, name.contains(':'))
        // Dated in the rider's own clock rather than UTC — it is their file —
        // so the assertion is on the shape, not on a timezone.
        assertTrue(name, name.startsWith("pelonot-2026-0"))
        assertTrue(name, name.endsWith("-Hills-Climbs-30.tcx"))
    }

    @Test
    fun `a ride with no title still produces a usable filename`() {
        val name = RideExport.filename(ride("///"), ExportFormat.Csv)
        assertTrue(name, name.endsWith("-ride.csv"))
    }

    @Test
    fun `an empty ride exports its header rather than nothing`() {
        val csv = RideExport.csv(ride(), emptyList())
        assertTrue(csv.contains("elapsed_sec"))

        val xml = RideExport.tcx(ride(), emptyList())
        assertTrue(xml.contains("</TrainingCenterDatabase>"))
        assertEquals(0, Regex("<Trackpoint>").findAll(xml).count())
    }
}
