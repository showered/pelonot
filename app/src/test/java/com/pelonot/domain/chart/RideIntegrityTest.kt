package com.pelonot.domain.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.7.5 — the rides recorded before the frame fix.
 *
 * The numbers in the first three tests are the ones off the bike on 1 August
 * 2026, not invented: a rider turning the cranks at 61 rpm and averaging 47 W
 * whose ride summary reported 109 rpm and 137 W, because 41 of 53 samples had
 * cadence, resistance and power in each other's columns.
 */
class RideIntegrityTest {

    /** A second of an honest ride. */
    private fun clean(sec: Int) = ChartSample(
        timestampSec = sec,
        powerWatts = 47.0,
        cadenceRpm = 61.0,
        heartRateBpm = 100,
        resistancePercent = 33.0
    )

    /** The signature of 2.7: the ghost near 602 lands in one of the columns. */
    private fun corrupt(sec: Int) = ChartSample(
        timestampSec = sec,
        powerWatts = 33.0,
        cadenceRpm = 603.0,
        heartRateBpm = 100,
        resistancePercent = 47.0
    )

    @Test
    fun `a ride recorded since the frame fix is not suspect`() {
        val integrity = RideIntegrity.of((0 until 200).map(::clean))

        assertFalse(integrity.isSuspect)
        assertEquals(0, integrity.impossibleSamples)
        assertEquals(200, integrity.cleanSamples)
    }

    @Test
    fun `the first real ride's corrupted samples are counted, not guessed at`() {
        val samples = (0 until 12).map(::clean) + (12 until 53).map(::corrupt)

        val integrity = RideIntegrity.of(samples)

        assertTrue(integrity.isSuspect)
        assertEquals(41, integrity.impossibleSamples)
        assertEquals(12, integrity.cleanSamples)
        assertEquals(77.0, integrity.percentImpossible, 1.0)
    }

    @Test
    fun `the corrected average is taken over the samples that survive`() {
        val samples = (0 until 12).map(::clean) + (12 until 53).map(::corrupt)

        val integrity = RideIntegrity.of(samples)

        // 61 rpm and 47 W — what the rider was actually doing, rather than the
        // 109 rpm and 137 W the summary reported on the day.
        assertEquals(61.0, integrity.cleanAvgCadenceRpm!!, 0.001)
        assertEquals(47.0, integrity.cleanAvgPowerWatts!!, 0.001)
    }

    /**
     * The rule the assembler follows live, applied afterwards: one impossible
     * field condemns the whole row. A 602% resistance means the labels slid,
     * and the cadence and power beside it are then in range and in the wrong
     * columns — which no bound can catch and no average should include.
     */
    @Test
    fun `an impossible resistance discredits the whole sample, not just itself`() {
        val samples = listOf(
            clean(0),
            ChartSample(
                timestampSec = 1,
                powerWatts = 61.0,
                cadenceRpm = 47.0,
                heartRateBpm = 100,
                resistancePercent = 602.0
            )
        )

        val integrity = RideIntegrity.of(samples)

        assertEquals(1, integrity.impossibleSamples)
        assertEquals(61.0, integrity.cleanAvgCadenceRpm!!, 0.001)
    }

    @Test
    fun `a ride with nothing left offers no corrected average rather than zero`() {
        val integrity = RideIntegrity.of((0 until 10).map(::corrupt))

        assertTrue(integrity.isSuspect)
        assertEquals(0, integrity.cleanSamples)
        assertNull(integrity.cleanAvgPowerWatts)
        assertNull(integrity.cleanAvgCadenceRpm)
    }

    @Test
    fun `a ride with no samples at all is not suspect`() {
        val integrity = RideIntegrity.of(emptyList())

        assertFalse(integrity.isSuspect)
        assertEquals(0.0, integrity.percentImpossible, 0.001)
    }

    @Test
    fun `the charts leave the impossible samples out and say how many`() {
        val samples = (0 until 60).map(::clean) + (60 until 80).map(::corrupt)

        val charts = RideChartBuilder.build(samples, ftpWatts = 200)

        assertEquals(20, charts.integrity.impossibleSamples)
        // 603 rpm would otherwise own the cadence distribution and 636 W the
        // power axis; the trace is drawn from the honest sixty seconds.
        assertEquals(47.0, charts.power.maxValue, 0.001)
        assertEquals(60, charts.cadence.totalSeconds)
    }

    @Test
    fun `a ride that is impossible throughout draws nothing rather than nonsense`() {
        val charts = RideChartBuilder.build((0 until 30).map(::corrupt), ftpWatts = 200)

        assertFalse(charts.hasAnything)
        assertEquals(30, charts.integrity.impossibleSamples)
    }
}
