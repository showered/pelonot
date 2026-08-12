package com.pelonot.domain.chart

import com.pelonot.domain.model.PowerZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The counts a trimmed ride keeps (PLAN 23.4.2).
 *
 * The point of every test here is that the two charts made of *counts of
 * seconds* survive housekeeping. Recomputing them from what a trim leaves
 * behind is the failure this exists to prevent, and it is a failure that does
 * not look like one: a 45-minute ride would simply report nine minutes of
 * pedalling, in the same layout, with no sign anything was missing.
 */
class RideDistributionsTest {

    private fun samples(count: Int, watts: (Int) -> Double, cadence: (Int) -> Double) =
        (0 until count).map {
            ChartSample(
                timestampSec = it,
                powerWatts = watts(it),
                cadenceRpm = cadence(it),
                heartRateBpm = null,
                resistancePercent = 40.0
            )
        }

    @Test
    fun `survives a round trip through JSON`() {
        val charts = RideChartBuilder.build(
            samples = samples(600, { 200.0 }, { 85.0 }),
            ftpWatts = 200
        )

        val stored = RideDistributions.decode(RideDistributions.of(charts).encode())

        assertEquals(charts.timeInZone.secondsByZone, stored?.timeInZone()?.secondsByZone)
        assertEquals(charts.cadence.secondsByBand, stored?.cadence()?.secondsByBand)
        assertEquals(200, stored?.ftpWatts)
    }

    @Test
    fun `a trimmed ride still says how long it spent in each zone`() {
        // Ten minutes at 200 W against a 200 W FTP, then the trim: the ride
        // itself is reduced to a hundredth of its rows, and the count is not.
        val full = samples(600, { 200.0 }, { 85.0 })
        val stored = RideDistributions.of(RideChartBuilder.build(full, ftpWatts = 200))

        val trimmed = RideChartBuilder.build(
            samples = full.filter { it.timestampSec % 100 == 0 },
            ftpWatts = 200,
            detailSec = 10,
            stored = stored
        )

        assertEquals(600, trimmed.timeInZone.totalSeconds)
        assertEquals(600, trimmed.cadence.totalSeconds)
    }

    @Test
    fun `without the stored counts a trimmed ride would report a tenth of itself`() {
        // The control for the test above, and the reason the column exists.
        val full = samples(600, { 200.0 }, { 85.0 })

        val trimmed = RideChartBuilder.build(
            samples = full.filter { it.timestampSec % 100 == 0 },
            ftpWatts = 200,
            detailSec = 10
        )

        assertEquals(6, trimmed.timeInZone.totalSeconds)
    }

    @Test
    fun `an untrimmed ride counts its own seconds and ignores nothing`() {
        val full = samples(120, { 200.0 }, { 85.0 })

        val charts = RideChartBuilder.build(full, ftpWatts = 200)

        assertEquals(120, charts.timeInZone.totalSeconds)
        assertNull(charts.zoneFtpWatts)
        assertTrue(!charts.isTrimmed)
    }

    @Test
    fun `a zone name this build does not know is dropped rather than thrown`() {
        val stored = RideDistributions(
            secondsByZone = mapOf(PowerZone.entries.first().name to 60, "Zone9" to 30)
        )

        assertEquals(1, stored.timeInZone().secondsByZone.size)
        assertEquals(60, stored.timeInZone().totalSeconds)
    }

    @Test
    fun `nonsense in the column reads as no summary rather than taking the screen down`() {
        assertNull(RideDistributions.decode("{not json"))
        assertNull(RideDistributions.decode(null))
        assertNull(RideDistributions.decode(""))
    }
}
