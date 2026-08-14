package com.pelonot.domain.chart

import com.pelonot.domain.model.HeartRateZone
import com.pelonot.domain.model.Interval
import com.pelonot.domain.model.PowerProvenance
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
    fun `the power summary says where the watts came from`() {
        val charts = RideChartBuilder.build(
            ride(600),
            ftpWatts = 200,
            powerProvenance = PowerProvenance.Measured
        )
        assertEquals(PowerProvenance.Measured, charts.powerProvenance)
        assertTrue(
            RideChartSummaries.power(charts.power, PowerProvenance.Measured).contains("measured")
        )
        assertTrue(
            RideChartSummaries.power(charts.power, PowerProvenance.Modelled).contains("estimated")
        )
        assertTrue(
            RideChartSummaries.power(charts.power, PowerProvenance.Mixed).contains("partly measured")
        )
        // A ride from before the column existed is described the safe way
        // round: never as a measurement.
        assertTrue(
            RideChartSummaries.power(charts.power, PowerProvenance.Unknown).contains("estimated")
        )
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

    // ---- 16.1.5a: the cadence the class asked for ----

    @Test
    fun `a coast is drawn on the cadence trace and left out of the spread`() {
        // The two cadence charts disagree on purpose. The distribution excludes
        // coasting because every ride would otherwise have a meaningless spike
        // in the 0-9 band; the trace keeps it, because a stop is a measured
        // thing that happened at a moment — and it is *measured*, which is what
        // separates it from a null heart rate.
        val samples = ride(600, cadence = { if (it in 200..259) 0.0 else 85.0 })
        val charts = RideChartBuilder.build(samples, ftpWatts = 200)

        assertEquals(0.0, charts.cadenceTrace.minValue, 0.001)
        assertTrue(charts.cadenceTrace.buckets.any { it.min == 0.0 })
        // 540 pedalling seconds, and not one of them in a band below 20 rpm.
        assertEquals(540, charts.cadence.totalSeconds)
        assertTrue(charts.cadence.secondsByBand.keys.none { it * 10 < 20 })
    }

    @Test
    fun `the prescribed cadence is counted separately from the prescribed power`() {
        // A torque block ridden at spinning cadence: the watts are dead on and
        // the legs are wrong, which is a different session from the one that was
        // written and is invisible on a histogram.
        val samples = ride(601, power = { 195.0 }, cadence = { if (it < 300) 62.0 else 95.0 })
        val charts = RideChartBuilder.build(
            samples,
            ftpWatts = 200,
            intervals = listOf(
                Interval(0, 300, cadenceMin = 60, cadenceMax = 70, powerZoneNumber = 4),
                Interval(300, 600, cadenceMin = 60, cadenceMax = 70, powerZoneNumber = 4)
            )
        )

        // Every prescribed second was inside the power band...
        assertEquals(600, charts.prescribed.secondsRidden)
        assertEquals(600, charts.prescribed.secondsInBand)
        // ...and only half of them inside the cadence.
        assertEquals(300, charts.prescribed.secondsInCadenceBand)
        assertEquals(0.5f, charts.prescribed.fractionInCadenceBand, 0.001f)

        val summary = RideChartSummaries.cadenceOverTime(charts.cadenceTrace, charts.prescribed)
        assertTrue(summary, summary.contains("50%"))
        assertTrue(summary, summary.contains("95 rpm"))
    }

    @Test
    fun `the prescribed cadence is not scaled by the ride's intent`() {
        // Riding a class easier means fewer watts, not slower legs. The power
        // band moves with the multiplier and the cadence does not.
        val charts = RideChartBuilder.build(
            ride(301, cadence = { 85.0 }),
            ftpWatts = 200,
            intervals = listOf(Interval(0, 300, cadenceMin = 80, cadenceMax = 90, powerZoneNumber = 4)),
            intentMultiplier = 0.8
        )

        val segment = charts.prescribed.segments.single()
        assertEquals(80, segment.targetCadenceLow)
        assertEquals(90, segment.targetCadenceHigh)
        assertEquals(300, segment.secondsInCadenceBand)
        // The watts did move, which is what makes the comparison meaningful.
        assertEquals(182.0 * 0.8, segment.targetLowWatts, 0.5)
    }

    @Test
    fun `a free ride's cadence trace says nothing about compliance`() {
        val charts = RideChartBuilder.build(ride(600), ftpWatts = 200)
        val summary = RideChartSummaries.cadenceOverTime(charts.cadenceTrace, charts.prescribed)

        assertTrue(summary, summary.contains("Cadence over"))
        assertFalse(summary, summary.contains("target cadence"))
    }

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

    /**
     * What a trimmed ride is allowed to claim (PLAN 23.4.3).
     *
     * The bands behind the trace come from the class and are as true as they
     * ever were; *"inside the target for 14 of 20 minutes"* is a count of
     * seconds that are no longer on disk, and a percentage derived from a fifth
     * of a ride sitting under a chart that looks complete is the whole reason
     * `metrics_detail_sec` exists.
     */
    @Test
    fun `a trimmed ride keeps the prescription and withdraws the compliance`() {
        val charts = RideChartBuilder.build(
            ride(300),
            ftpWatts = 200,
            intervals = listOf(interval(0, 300, zone = 4)),
            detailSec = 10
        )

        assertFalse(charts.prescribed.isEmpty)
        assertEquals(0, charts.prescribed.secondsRidden)
        assertEquals("", RideChartSummaries.prescribed(charts.prescribed))
    }

    @Test
    fun `the same ride untrimmed does count its seconds`() {
        val charts = RideChartBuilder.build(
            ride(300),
            ftpWatts = 200,
            intervals = listOf(interval(0, 300, zone = 4))
        )

        // 299 rather than 300: the prescription is clipped to the ride's last
        // recorded second, which is one less than its sample count.
        assertEquals(299, charts.prescribed.secondsRidden)
    }

    /**
     * The peak survives a trim exactly — `MetricTrim` keeps each bucket's
     * highest second — and the average does not, because what is left is the
     * extremes of every bucket and nothing in between. So the sentence says the
     * peak and stops.
     */
    @Test
    fun `the power sentence says it is an outline instead of averaging one`() {
        val charts = RideChartBuilder.build(ride(300), ftpWatts = 200, detailSec = 10)

        val said = RideChartSummaries.power(charts.power, charts.powerProvenance, charts.detailSec)

        assertTrue(said, said.contains("10-second outline"))
        assertFalse(said, said.contains("average"))
    }

    // 21.4.1 — the same question asked of the heart.

    @Test
    fun `time in heart-rate zone counts seconds against the rider's maximum`() {
        // 190 max: H2 is 114-132, H4 is 152-170.
        val samples = ride(300, heartRate = { if (it < 100) 120 else 160 })

        val charts = RideChartBuilder.build(samples, ftpWatts = 200, maxHrBpm = 190)

        assertEquals(100, charts.timeInHeartRateZone.secondsByZone[HeartRateZone.H2])
        assertEquals(200, charts.timeInHeartRateZone.secondsByZone[HeartRateZone.H4])
        assertEquals(300, charts.timeInHeartRateZone.totalSeconds)
        assertFalse(charts.timeInHeartRateZone.isPartial)
    }

    /**
     * 21.2.4, and the reason this is not [TimeInZone] with a different enum in
     * it. Power is recorded every second and a heart rate is not, so a maximum
     * the app does not have has to mean *no zones* rather than five zones drawn
     * off a default — the same rule `HeartRateZone.forHeartRate` follows.
     */
    @Test
    fun `no maximum heart rate means no heart-rate zones at all`() {
        val samples = ride(300, heartRate = { 150 })

        val charts = RideChartBuilder.build(samples, ftpWatts = 200, maxHrBpm = null)

        assertEquals(0, charts.timeInHeartRateZone.totalSeconds)
        assertTrue(charts.timeInHeartRateZone.occupied.isEmpty())
    }

    /**
     * The defect this project has had to refuse three times: a missing heart
     * rate is not a zero. Counted as H1 it would put a strapless rider in
     * Recovery for the whole ride, and the percentages under it would be the
     * shape of the ride rather than the shape of the coverage.
     */
    @Test
    fun `seconds with no heart rate are unknown rather than Recovery`() {
        // The strap pairs at 5:00 of a 10:00 ride and reads 160 (H4) after it.
        val samples = ride(600, heartRate = { sec -> if (sec >= 300) 160 else null })

        val zones = RideChartBuilder.build(samples, ftpWatts = 200, maxHrBpm = 190)
            .timeInHeartRateZone

        assertEquals(null, zones.secondsByZone[HeartRateZone.H1])
        assertEquals(300, zones.secondsByZone[HeartRateZone.H4])
        assertEquals(300, zones.totalSeconds)
        assertEquals(300, zones.secondsUnrecorded)
        assertEquals(600, zones.recordedSeconds)
        assertTrue(zones.isPartial)

        // The zones are 100% of the time a heart was heard, and the sentence
        // says what that was out of rather than leaving it to be assumed.
        assertEquals(1.0f, zones.fractionOf(HeartRateZone.H4), 0.0001f)
        val said = RideChartSummaries.timeInHeartRateZone(zones)
        assertTrue(said, said.contains("recorded for 5 minutes of 10 minutes"))
    }

    @Test
    fun `a ride with no strap has nothing to say about heart-rate zones`() {
        val zones = RideChartBuilder.build(ride(300), ftpWatts = 200, maxHrBpm = 190)
            .timeInHeartRateZone

        assertEquals(0, zones.totalSeconds)
        assertFalse(zones.isPartial)
        assertTrue(RideChartSummaries.timeInHeartRateZone(zones).contains("neither"))
    }

    /**
     * 23.4.3, for the third count of seconds. A trimmed ride has a fifth of its
     * rows left, so recounting them would draw a 45-minute ride that spent nine
     * minutes with a heart rate — and nothing about the result would look
     * coarse.
     */
    @Test
    fun `a trimmed ride reads its stored heart-rate zones rather than recounting`() {
        val stored = RideDistributions(
            secondsByHrZone = mapOf("H2" to 1_200, "H3" to 600),
            secondsWithoutHr = 60,
            maxHrBpm = 185
        )

        val charts = RideChartBuilder.build(
            // What a trim left behind: six seconds, all of them H5 at this max.
            ride(6, heartRate = { 180 }),
            ftpWatts = 200,
            maxHrBpm = 190,
            detailSec = 10,
            stored = stored
        )

        assertEquals(1_200, charts.timeInHeartRateZone.secondsByZone[HeartRateZone.H2])
        assertEquals(600, charts.timeInHeartRateZone.secondsByZone[HeartRateZone.H3])
        assertEquals(null, charts.timeInHeartRateZone.secondsByZone[HeartRateZone.H5])
        assertEquals(60, charts.timeInHeartRateZone.secondsUnrecorded)
        // And the denominator those counts were made against, which is not the
        // one the bands are drawn from today.
        assertEquals(185, charts.zoneMaxHrBpm)
    }

    /**
     * A ride trimmed by a build that did not count heart-rate zones has none,
     * and that is read as *never counted* rather than as zero — the same answer
     * a strapless ride gets, and better than recounting six surviving seconds.
     */
    @Test
    fun `a ride trimmed before this existed draws no heart-rate zones`() {
        val charts = RideChartBuilder.build(
            ride(6, heartRate = { 180 }),
            ftpWatts = 200,
            maxHrBpm = 190,
            detailSec = 10,
            stored = RideDistributions(secondsByZone = mapOf("Z2" to 1_800), ftpWatts = 200)
        )

        assertEquals(0, charts.timeInHeartRateZone.totalSeconds)
    }

    /**
     * Every one of these sentences is read aloud as well as printed, and the
     * plural was hard-coded until a card was seen saying *"1 minutes 42
     * seconds"* on the tablet.
     */
    @Test
    fun `one minute is not one minutes`() {
        // 190 max: 160 is H4. 61 seconds of it and 60 of H2 (120 bpm).
        val zones = RideChartBuilder.build(
            ride(121, heartRate = { sec -> if (sec < 61) 160 else 120 }),
            ftpWatts = 200,
            maxHrBpm = 190
        ).timeInHeartRateZone

        val said = RideChartSummaries.timeInHeartRateZone(zones)
        assertTrue(said, said.contains("Threshold 1 minute 1 second"))
        assertTrue(said, said.contains("Aerobic 1 minute,"))
        assertFalse(said, said.contains("1 minutes"))
        assertFalse(said, said.contains("1 seconds"))
    }

    /**
     * 21.6.3 appends its sentence to this one on the card, so the zone list has
     * to end like a sentence.
     */
    @Test
    fun `the zone lists end in a full stop so the next sentence can start`() {
        val charts = RideChartBuilder.build(
            ride(300, heartRate = { 160 }),
            ftpWatts = 200,
            maxHrBpm = 190
        )

        assertTrue(RideChartSummaries.timeInZone(charts.timeInZone).endsWith("."))
        assertTrue(
            RideChartSummaries.timeInHeartRateZone(charts.timeInHeartRateZone).endsWith(".")
        )
    }
}
