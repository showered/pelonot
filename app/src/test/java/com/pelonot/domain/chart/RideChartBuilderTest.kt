package com.pelonot.domain.chart

import com.pelonot.domain.model.Interval
import com.pelonot.domain.model.PowerZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideChartBuilderTest {

    private fun ride(
        seconds: Int,
        power: (Int) -> Double = { 150.0 },
        cadence: (Int) -> Double = { 85.0 },
        heartRate: (Int) -> Int? = { null }
    ) = (0 until seconds).map { sec ->
        ChartSample(sec, power(sec), cadence(sec), heartRate(sec))
    }

    @Test
    fun `an empty ride draws nothing rather than a flat line at zero`() {
        val charts = RideChartBuilder.build(emptyList(), ftpWatts = 200)
        assertFalse(charts.hasAnything)
        assertTrue(charts.power.isEmpty)
        assertTrue(charts.heartRate.isEmpty)
    }

    @Test
    fun `a long ride is reduced to a drawable number of points`() {
        val charts = RideChartBuilder.build(ride(2_700), ftpWatts = 200, buckets = 300)
        assertTrue(
            "expected ~300 buckets, got ${charts.power.buckets.size}",
            charts.power.buckets.size in 250..300
        )
    }

    /**
     * The reason buckets carry min and max at all. Averaging a 9-second bucket
     * turns a one-second 600 W sprint into a 250 W bump, and the sprint is the
     * single thing a rider most wants to see afterwards.
     */
    @Test
    fun `a one-second sprint survives downsampling`() {
        val samples = ride(2_700, power = { if (it == 1_234) 600.0 else 150.0 })
        val charts = RideChartBuilder.build(samples, ftpWatts = 200, buckets = 300)

        assertEquals(600.0, charts.power.maxValue, 0.001)
        assertTrue(charts.power.buckets.any { it.max == 600.0 })
        // And the mean of that bucket does *not* reach it, which is the point.
        assertTrue(charts.power.buckets.single { it.max == 600.0 }.mean < 300.0)
    }

    /**
     * 16.1.2. Null heart rate means *unknown*, and a line dropping to the axis
     * says the rider's heart stopped.
     */
    @Test
    fun `a ride with no strap has no heart-rate trace at all`() {
        val charts = RideChartBuilder.build(ride(600), ftpWatts = 200)
        assertTrue(charts.heartRate.isEmpty)
        assertEquals(
            "No heart rate was recorded for this ride — no strap was paired.",
            RideChartSummaries.heartRate(charts.heartRate)
        )
    }

    @Test
    fun `a strap that connects mid-ride charts only the part it saw`() {
        // The strap pairs at 5:00 of a 10:00 ride.
        val samples = ride(600, heartRate = { sec -> if (sec >= 300) 140 else null })
        val charts = RideChartBuilder.build(samples, ftpWatts = 200, buckets = 60)

        assertFalse(charts.heartRate.isEmpty)
        assertEquals(140.0, charts.heartRate.minValue, 0.001)
        // Every bucket sits in the second half of the ride.
        assertTrue(charts.heartRate.buckets.all { it.startSec >= 290 })
    }

    @Test
    fun `time in zone counts seconds, and only where there is an FTP`() {
        // 100 s in Z1 (50 W against 200 FTP), 200 s in Z4 (200 W).
        val samples = ride(300, power = { if (it < 100) 50.0 else 200.0 })

        val charts = RideChartBuilder.build(samples, ftpWatts = 200)
        assertEquals(100, charts.timeInZone.secondsByZone[PowerZone.Z1])
        assertEquals(200, charts.timeInZone.secondsByZone[PowerZone.Z4])
        assertEquals(300, charts.timeInZone.totalSeconds)

        // Without an FTP, zones are meaningless rather than all Z1.
        val noFtp = RideChartBuilder.build(samples, ftpWatts = 0)
        assertEquals(0, noFtp.timeInZone.totalSeconds)
        assertTrue(RideChartSummaries.timeInZone(noFtp.timeInZone).contains("needs an FTP"))
    }

    @Test
    fun `coasting is not a cadence`() {
        // Half the ride freewheeling. Without the filter, the 0-9 band would be
        // the biggest thing on the chart and say nothing about the riding.
        val samples = ride(200, cadence = { if (it < 100) 0.0 else 90.0 })
        val charts = RideChartBuilder.build(samples, ftpWatts = 200)

        assertEquals(100, charts.cadence.totalSeconds)
        assertEquals(100, charts.cadence.secondsByBand[9])
    }

    @Test
    fun `cadence bands include the gaps between them`() {
        val samples = ride(60, cadence = { if (it < 30) 45.0 else 95.0 })
        val charts = RideChartBuilder.build(samples, ftpWatts = 200)

        val bands = charts.cadence.bands
        // 40-49 through 90-99, with the untouched bands in between present and
        // empty rather than missing — a histogram with holes punched out of it
        // is a different shape from the ride.
        assertEquals(40, bands.first().first)
        assertEquals(100, bands.last().second)
        assertTrue(bands.any { it.third == 0 })
    }

    /**
     * A recovered ride has gaps in its series. Bucketing by sample index would
     * compress the gap out and draw a ride that looks continuous.
     */
    @Test
    fun `a gap in the samples stays a gap on the time axis`() {
        val before = (0 until 60).map { ChartSample(it, 150.0, 85.0, null) }
        val after = (600 until 660).map { ChartSample(it, 150.0, 85.0, null) }

        val charts = RideChartBuilder.build(before + after, ftpWatts = 200, buckets = 60)

        assertEquals(659, charts.power.durationSec)
        // Nothing charted in the middle ten minutes.
        assertTrue(charts.power.buckets.none { it.startSec in 100..500 })
    }

    @Test
    fun `samples out of order are put back in order`() {
        val shuffled = ride(120).shuffled(java.util.Random(20260731))
        val charts = RideChartBuilder.build(shuffled, ftpWatts = 200, buckets = 12)
        val starts = charts.power.buckets.map { it.startSec }
        assertEquals(starts.sorted(), starts)
    }

    @Test
    fun `the power summary says whether the watts were measured or modelled`() {
        val charts = RideChartBuilder.build(ride(600), ftpWatts = 200, powerIsMeasured = true)
        assertTrue(RideChartSummaries.power(charts.power, isMeasured = true).contains("measured"))
        assertTrue(RideChartSummaries.power(charts.power, isMeasured = false).contains("estimated"))
    }

    // ---- 16.1.5: what you were asked for, against what you did ----

    /** Against an FTP of 200: Z2 is 112–152 W and Z4 is 182–212 W. */
    private fun interval(startSec: Int, endSec: Int, zone: Int) = Interval(
        startSec = startSec,
        endSec = endSec,
        cadenceMin = 80,
        cadenceMax = 90,
        powerZoneNumber = zone
    )

    @Test
    fun `a free ride is not marked down against a class it never rode`() {
        val charts = RideChartBuilder.build(ride(600), ftpWatts = 200)

        assertTrue(charts.prescribed.isEmpty)
        // And says nothing at all rather than "0% of nothing".
        assertEquals("", RideChartSummaries.prescribed(charts.prescribed))
    }

    @Test
    fun `the prescription is counted against the seconds actually ridden`() {
        // Five minutes of Endurance ridden at 130 W — inside the band — then ten
        // minutes of Threshold ridden at 250 W, which is over it. 901 samples,
        // second 0 to second 900, is 900 seconds of riding.
        val samples = ride(901, power = { if (it < 300) 130.0 else 250.0 })
        val charts = RideChartBuilder.build(
            samples,
            ftpWatts = 200,
            intervals = listOf(interval(0, 300, zone = 2), interval(300, 900, zone = 4))
        )

        val segments = charts.prescribed.segments
        assertEquals(2, segments.size)
        assertEquals(300, segments[0].secondsRidden)
        assertEquals(300, segments[0].secondsInBand)
        assertEquals(600, segments[1].secondsRidden)
        // Over the target is still off the target.
        assertEquals(0, segments[1].secondsInBand)

        assertEquals(1f / 3f, charts.prescribed.fractionInBand, 0.001f)
        assertTrue(charts.prescribed.finishedClass)
        assertTrue(RideChartSummaries.prescribed(charts.prescribed).contains("33%"))
    }

    /**
     * A rider who abandons a 30-minute class at 2 minutes gets 2 minutes of
     * prescription, not 28 minutes of ghost plan hanging off the end of a
     * 2-minute axis.
     */
    @Test
    fun `a class abandoned part way is clipped to what was ridden and says so`() {
        val charts = RideChartBuilder.build(
            ride(121),
            ftpWatts = 200,
            intervals = listOf(interval(0, 900, zone = 2), interval(900, 1_800, zone = 4))
        )

        val segments = charts.prescribed.segments
        assertEquals(1, segments.size)
        assertEquals(120, segments.single().endSec)
        assertEquals(1_800, charts.prescribed.classDurationSec)
        assertFalse(charts.prescribed.finishedClass)

        val summary = RideChartSummaries.prescribed(charts.prescribed)
        assertTrue(summary, summary.contains("30 minutes") && summary.contains("stopped at"))
    }

    /**
     * The band is the one the ride was *given*, not one re-derived from
     * whatever the rider would pick today. 220 W is over the top of Z4 at face
     * value and inside it for someone who asked to be pushed 5% harder.
     */
    @Test
    fun `the intent the ride was ridden with scales the target band`() {
        val samples = ride(301, power = { 220.0 })
        val intervals = listOf(interval(0, 300, zone = 4))

        val asPrescribed = RideChartBuilder.build(samples, ftpWatts = 200, intervals = intervals)
        assertEquals(0, asPrescribed.prescribed.secondsInBand)

        val pushed = RideChartBuilder.build(
            samples,
            ftpWatts = 200,
            intervals = intervals,
            intentMultiplier = 1.05
        )
        assertEquals(300, pushed.prescribed.secondsInBand)
    }

    @Test
    fun `without an FTP there is no band to be inside`() {
        val charts = RideChartBuilder.build(
            ride(300),
            ftpWatts = 0,
            intervals = listOf(interval(0, 300, zone = 4))
        )
        assertTrue(charts.prescribed.isEmpty)
    }

    @Test
    fun `time in zone fractions add up to one`() {
        val samples = ride(300, power = { if (it < 100) 50.0 else 200.0 })
        val charts = RideChartBuilder.build(samples, ftpWatts = 200)

        val total = PowerZone.entries.map { charts.timeInZone.fractionOf(it) }.sum()
        assertEquals(1.0f, total, 0.0001f)
    }
}
