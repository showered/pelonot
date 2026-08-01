package com.pelonot.domain.chart

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * The value axis of a trace, and the round numbers to label it with (16.1.7).
 *
 * The charts had no scale at all: the first real ride produced a green line
 * rising left to right with *Heart rate from 88 to 170 beats per minute*
 * underneath it, and a rider could not answer "what was I at during the second
 * climb". The shape was legible and the values were not.
 *
 * **The brief is beautiful, not scientific.** So this returns a *few* labels at
 * numbers a person would have chosen — 100, 150, 200, never 137.4 — and the
 * drawing puts them inside the plot rather than building a boxed axis with tick
 * forests around it. Being pure and off in `domain` is what lets the choosing
 * be tested; the drawing is a separate job.
 */
data class ChartScale(val min: Double, val max: Double) {

    /** Where a value sits, 0 at the bottom of the plot and 1 at the top. */
    fun fractionOf(value: Double): Float {
        val span = max - min
        if (span <= 0.0) return 0f
        return ((value - min) / span).toFloat().coerceIn(0f, 1f)
    }

    /**
     * Round values inside the range — [count] of them, give or take one.
     *
     * A target rather than a cap: forcing an exact number would mean either
     * dropping a label and leaving one end of the chart bare, or moving one off
     * its round value, and both read as a drawing error rather than a choice.
     *
     * The step is a 1, 2 or 5 times a power of ten — the steps people use for
     * everything from watts to heartbeats — chosen as the smallest that keeps
     * the label count down. The endpoints are deliberately *not* forced in: a
     * label pinned to the very top of the plot collides with the trace that
     * reached it, and the caption already states the peak exactly.
     */
    fun ticks(count: Int = 3): List<Double> {
        val span = max - min
        if (span <= 0.0 || count <= 0) return emptyList()

        val step = niceStep(span / count)
        if (step <= 0.0) return emptyList()

        val first = floor(min / step) * step
        return generateSequence(first) { it + step }
            .takeWhile { it <= max + step * 0.001 }
            // Dropped rather than clamped, for the same reason the telemetry
            // fence rejects rather than clamps: a label moved to fit is a label
            // pointing at the wrong height.
            .filter { it > min + span * 0.04 && it < max - span * 0.04 }
            .take(count + 1)
            .map { snap(it, step) }
            .toList()
    }

    private companion object {
        /** 1, 2 or 5 × a power of ten — the steps a person would pick. */
        fun niceStep(rough: Double): Double {
            if (rough <= 0.0 || !rough.isFinite()) return 0.0
            val magnitude = 10.0.pow(floor(log10(rough)))
            val normalised = rough / magnitude
            // Nearest rather than next-above, and 2.5 is in the set: an
            // 88-170 bpm ride wants labels every 25, and rounding 3.4 up to 5
            // gives it two labels where three would fit comfortably.
            val nice = listOf(10.0, 5.0, 2.5, 2.0, 1.0).minByOrNull {
                abs(it - normalised)
            } ?: 1.0
            return nice * magnitude
        }

        /** Kills the floating-point dust that turns 150 into 149.99999999. */
        fun snap(value: Double, step: Double): Double {
            val rounded = Math.round(value / step) * step
            return if (abs(rounded) < step * 1e-6) 0.0 else rounded
        }
    }
}
