package com.pelonot.domain.retention

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What survives a trim (PLAN 23.4.2).
 *
 * The tests that matter here are about what is *not* lost: the peak, the ends
 * of the ride, and the gap a stop left behind. A downsample that quietly closed
 * a gap up or shaved a sprint would draw a ride the rider did not do, and it
 * would be permanent.
 */
class MetricTrimTest {

    private data class Sample(val sec: Int, val watts: Double)

    private fun keep(samples: List<Sample>, bucketSec: Int = MetricTrim.BUCKET_SEC) =
        MetricTrim.keep(samples, bucketSec, { it.sec }, { it.watts })

    private fun steady(seconds: Int, watts: (Int) -> Double) =
        (0 until seconds).map { Sample(it, watts(it)) }

    @Test
    fun `keeps the highest watt of every bucket`() {
        // A 30-second effort with a single 600 W second buried in the middle of
        // the second bucket, which is exactly the thing 16.2.2 says a mean
        // erases.
        val samples = steady(30) { if (it == 14) 600.0 else 200.0 }

        val kept = keep(samples)

        assertTrue(kept.any { it.sec == 14 && it.watts == 600.0 })
        assertEquals(600.0, kept.maxOf { it.watts }, 0.0)
    }

    @Test
    fun `keeps the lowest watt of every bucket`() {
        val samples = steady(30) { if (it == 22) 0.0 else 200.0 }

        val kept = keep(samples)

        assertTrue(kept.any { it.sec == 22 && it.watts == 0.0 })
    }

    @Test
    fun `keeps the first and last second of the ride`() {
        // Both are flat and neither is a bucket extreme on its own merit; they
        // are kept because they are the axis, and a trace missing them draws a
        // ride that started late and finished early.
        val samples = steady(60) { second -> if (second in 20..40) 400.0 else 100.0 }

        val kept = keep(samples)

        assertEquals(0, kept.first().sec)
        assertEquals(59, kept.last().sec)
    }

    @Test
    fun `leaves the gap a stop left behind`() {
        // Forty seconds of riding, forty-nine seconds of bottle stop, forty
        // more. Buckets are cut by the clock, so nothing here may invent a
        // sample in the hole or slide the second half earlier.
        val ridden = steady(40) { 180.0 } + (89 until 129).map { Sample(it, 180.0) }

        val kept = keep(ridden)

        assertTrue(kept.none { it.sec in 40..88 })
        assertEquals(128, kept.last().sec)
    }

    @Test
    fun `is about five times smaller on a real-length ride`() {
        // 25 minutes of ordinary riding — the reduction the item is sized on,
        // measured rather than promised. Two rows per ten seconds is the price
        // of keeping the peaks, and it is the honest number to write down.
        val samples = steady(1_500) { 150.0 + (it % 37) }

        val kept = keep(samples)

        assertEquals(300, kept.size)
        assertTrue(samples.size / kept.size == 5)
    }

    @Test
    fun `a bucket whose min and max are the same second keeps one row`() {
        val samples = steady(20) { 200.0 }

        val kept = keep(samples)

        // Flat ten seconds: `minBy` and `maxBy` both land on the first sample,
        // so a steady ride condenses harder than a varied one.
        assertEquals(kept.map { it.sec }.distinct().size, kept.size)
        assertTrue(kept.size <= 4)
    }

    @Test
    fun `a ride of two samples is left alone`() {
        val samples = listOf(Sample(0, 100.0), Sample(1, 110.0))

        assertEquals(samples, keep(samples))
    }

    @Test
    fun `trimming an outline again takes nothing more`() {
        // Not a thing the repository ever does — `metrics_detail_sec IS NULL`
        // gates it — but the arithmetic should be stable rather than
        // load-bearing on that gate.
        val once = keep(steady(600) { 100.0 + (it % 53) })
        val twice = keep(once)

        assertEquals(once.size, twice.size)
    }
}
