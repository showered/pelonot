package com.pelonot.data.service

import com.pelonot.data.local.entity.WorkoutMetricEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The file this replaces was committed empty, despite PLAN item 8.7 being
 * ticked as complete.
 */
class PostWorkoutAnalyzerTest {

    private val analyzer = PostWorkoutAnalyzer()

    private fun metrics(
        powers: List<Double>,
        heartRates: List<Int?> = emptyList()
    ) = powers.mapIndexed { index, power ->
        WorkoutMetricEntity(
            workoutId = "w1",
            timestampSec = index,
            cadence = 90.0,
            resistance = 40.0,
            power = power,
            heartRate = heartRates.getOrNull(index)
        )
    }

    private fun steady(watts: Double, seconds: Int) = List(seconds) { watts }

    // ── 20-minute peak ──────────────────────────────────────────────

    @Test
    fun `estimates FTP as 95 percent of the best 20 minute average`() {
        val result = analyzer.estimateFtpFrom20MinPeak(metrics(steady(200.0, 1200)))

        assertEquals(190.0, result!!, 0.01)
    }

    @Test
    fun `returns null for a ride shorter than 20 minutes`() {
        assertNull(analyzer.estimateFtpFrom20MinPeak(metrics(steady(300.0, 1199))))
    }

    @Test
    fun `finds the best window wherever it falls in the ride`() {
        // Easy warmup, then a hard 20-minute block, then easy again.
        val powers = steady(100.0, 600) + steady(250.0, 1200) + steady(100.0, 600)

        val result = analyzer.estimateFtpFrom20MinPeak(metrics(powers))

        assertEquals(250.0 * 0.95, result!!, 0.01)
    }

    @Test
    fun `a short hard finish does not inflate the estimate`() {
        // Regression: the old loop clamped the window end to the list length,
        // so near the tail it averaged progressively shorter slices. A 60s
        // sprint finish was averaged over 60 samples and reported as a
        // "20-minute average", producing a wildly optimistic FTP.
        val powers = steady(150.0, 1200) + steady(600.0, 60)

        val result = analyzer.estimateFtpFrom20MinPeak(metrics(powers))!!

        // The true best 20-minute average is only slightly above 150W.
        assertTrue("estimate $result was inflated by the sprint", result < 180.0)
        assertTrue(result >= 150.0 * 0.95)
    }

    @Test
    fun `handles a long ride without quadratic slowdown`() {
        // Two hours of samples. The previous O(n^2) implementation did roughly
        // 8.6 billion additions here.
        val powers = steady(200.0, 7200)

        val started = System.nanoTime()
        val result = analyzer.estimateFtpFrom20MinPeak(metrics(powers))
        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        assertEquals(190.0, result!!, 0.01)
        assertTrue("took ${elapsedMs}ms", elapsedMs < 1_000)
    }

    // ── Biometric decoupling ────────────────────────────────────────

    @Test
    fun `detects threshold power sustained at a low heart rate`() {
        val ftp = 200.0
        // 11 minutes in Zone 4 with HR well under 80% of max.
        val seconds = 660
        val result = analyzer.detectBiometricDecoupling(
            metrics = metrics(steady(195.0, seconds), List(seconds) { 130 }),
            ftp = ftp,
            maxHr = 190
        )

        assertTrue(result)
    }

    @Test
    fun `does not flag decoupling when heart rate is where it should be`() {
        val seconds = 660
        val result = analyzer.detectBiometricDecoupling(
            metrics = metrics(steady(195.0, seconds), List(seconds) { 175 }),
            ftp = 200.0,
            maxHr = 190
        )

        assertFalse(result)
    }

    @Test
    fun `does not flag decoupling from a brief effort`() {
        val seconds = 300 // only 5 minutes
        val result = analyzer.detectBiometricDecoupling(
            metrics = metrics(steady(195.0, seconds), List(seconds) { 120 }),
            ftp = 200.0,
            maxHr = 190
        )

        assertFalse(result)
    }

    @Test
    fun `cannot detect decoupling without a max heart rate`() {
        assertFalse(
            analyzer.detectBiometricDecoupling(
                metrics = metrics(steady(195.0, 900), List(900) { 120 }),
                ftp = 200.0,
                maxHr = null
            )
        )
    }

    @Test
    fun `missing heart rate samples do not count towards decoupling`() {
        // A rider with no strap must not be treated as having a low pulse.
        val seconds = 900
        assertFalse(
            analyzer.detectBiometricDecoupling(
                metrics = metrics(steady(195.0, seconds)),
                ftp = 200.0,
                maxHr = 190
            )
        )
    }

    // ── RPE ─────────────────────────────────────────────────────────

    @Test
    fun `proposes a bump when a hard class felt easy`() {
        assertEquals(
            206.0,
            analyzer.suggestFtpFromRpe(rpe = 3, isHardClass = true, currentFtp = 200.0)!!,
            0.01
        )
    }

    @Test
    fun `no proposal when the class felt as hard as it was`() {
        assertNull(analyzer.suggestFtpFromRpe(rpe = 8, isHardClass = true, currentFtp = 200.0))
    }

    @Test
    fun `no proposal from an easy class that felt easy`() {
        assertNull(analyzer.suggestFtpFromRpe(rpe = 2, isHardClass = false, currentFtp = 200.0))
    }

    @Test
    fun `no proposal without an RPE rating`() {
        assertNull(analyzer.suggestFtpFromRpe(rpe = null, isHardClass = true, currentFtp = 200.0))
    }

    // ── Combined analysis ───────────────────────────────────────────

    @Test
    fun `surfaces a breakthrough only when the gain is meaningful`() {
        // 20 minutes at 205W estimates FTP at 194.75 against a current 200W —
        // a decrease, so nothing should be offered.
        val result = analyzer.analyze(
            metrics = metrics(steady(205.0, 1200)),
            currentFtp = 200.0
        )

        assertNull(result.proposedFtp)
        assertFalse(result.hasBreakthrough)
        // The raw evidence is still available to callers that want it.
        assertNotNull(result.estimatedFtpFromPeak)
    }

    @Test
    fun `surfaces a breakthrough after a genuinely stronger effort`() {
        val result = analyzer.analyze(
            metrics = metrics(steady(260.0, 1200)),
            currentFtp = 200.0
        )

        assertTrue(result.hasBreakthrough)
        assertEquals(247.0, result.proposedFtp!!, 0.01)
    }

    @Test
    fun `picks the higher of the peak and RPE proposals`() {
        val result = analyzer.analyze(
            metrics = metrics(steady(260.0, 1200)),
            currentFtp = 200.0,
            rpe = 3,
            isHardClass = true
        )

        // Peak gives 247W, RPE gives 206W.
        assertEquals(247.0, result.proposedFtp!!, 0.01)
    }

    @Test
    fun `a recovery spin never triggers a breakthrough dialog`() {
        val result = analyzer.analyze(
            metrics = metrics(steady(90.0, 1800)),
            currentFtp = 200.0
        )

        assertFalse(result.hasBreakthrough)
    }

    @Test
    fun `an empty ride analyses without throwing`() {
        val result = analyzer.analyze(metrics = emptyList(), currentFtp = 200.0)

        assertNull(result.estimatedFtpFromPeak)
        assertFalse(result.hasBreakthrough)
    }

    @Test
    fun `an unknown FTP does not produce a proposal`() {
        val result = analyzer.analyze(metrics = metrics(steady(250.0, 1200)), currentFtp = 0.0)

        assertFalse(result.hasBreakthrough)
    }
}
