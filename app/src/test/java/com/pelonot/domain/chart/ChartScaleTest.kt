package com.pelonot.domain.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 16.1.7 — the numbers a rider is shown down the side of a chart.
 *
 * The ranges here are the ones off the first real ride: heart rate 88–170, and
 * a power chart scaled to a 200 W FTP.
 */
class ChartScaleTest {

    @Test
    fun `a heart-rate range gets round numbers a person would have chosen`() {
        // 88-170 bpm off the first real ride, padded as the chart pads it.
        val ticks = ChartScale(78.0, 180.0).ticks()

        // Every 25 bpm, which is what a person would have picked for this
        // range — not every 34, and not two labels where four fit.
        assertEquals(listOf(100.0, 125.0, 150.0, 175.0), ticks)
    }

    @Test
    fun `a power range from zero is labelled in round watts`() {
        val ticks = ChartScale(0.0, 240.0).ticks()

        assertTrue("expected round watts, got $ticks", ticks.all { it % 50.0 == 0.0 })
        assertTrue(ticks.isNotEmpty())
    }

    @Test
    fun `no label is placed where the trace will already be`() {
        val scale = ChartScale(0.0, 240.0)

        // Nothing within 4% of either end: a label at the very top collides
        // with the peak that reached it, and the caption states the peak
        // exactly anyway.
        assertTrue(scale.ticks().all { it > 9.6 && it < 230.4 })
    }

    @Test
    fun `a flat ride labels nothing rather than dividing by zero`() {
        assertTrue(ChartScale(120.0, 120.0).ticks().isEmpty())
        assertEquals(0f, ChartScale(120.0, 120.0).fractionOf(120.0), 0.001f)
    }

    @Test
    fun `a value maps to its height in the plot, bottom to top`() {
        val scale = ChartScale(60.0, 180.0)

        assertEquals(0f, scale.fractionOf(60.0), 0.001f)
        assertEquals(0.5f, scale.fractionOf(120.0), 0.001f)
        assertEquals(1f, scale.fractionOf(180.0), 0.001f)
        // Off the top of the chart is the top of the chart, not off the card.
        assertEquals(1f, scale.fractionOf(500.0), 0.001f)
    }

    @Test
    fun `the labels are free of floating-point dust`() {
        ChartScale(0.0, 3.0).ticks().forEach {
            assertEquals(it, Math.round(it * 100) / 100.0, 0.0)
        }
    }
}
