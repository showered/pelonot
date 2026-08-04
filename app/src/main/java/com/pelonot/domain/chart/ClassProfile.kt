package com.pelonot.domain.chart

import com.pelonot.core.Formatters
import com.pelonot.domain.model.Interval
import com.pelonot.domain.model.PowerZone
import com.pelonot.domain.model.RidePosition

/**
 * The shape of a class, as something to look at rather than read (PLAN 22.7.2).
 *
 * The class detail screen is the last screen between a rider and a ride, and
 * until this it showed a class as a stack of rows: seven of them for a
 * thirty-minute climb, each a full-width card carrying four facts, with the
 * seventh below the fold. A rider asking *do I want this tonight* had to read
 * every row and do the arithmetic — and the one thing they were actually
 * asking, **where is the work**, was the one thing the screen never said.
 *
 * A profile answers it without a number: height for zone, width for time. The
 * long threshold block in the middle of `The Long Climb 30` is visible from
 * across the room; so are the four spikes of a `4×2`.
 *
 * **Pure, and here rather than in the composable, because the summary sentence
 * is the part worth being sure of.** *"four 2:00 efforts"* against *"one 15:00
 * effort"* against *"5 efforts"* when they are not all the same length is
 * three cases and an easy one to get subtly wrong, and none of it needs a
 * canvas to check.
 */
data class ClassProfileBlock(
    val zone: PowerZone,
    val startSec: Int,
    val durationSec: Int,
    val position: RidePosition?,
    /** Left edge as a fraction of the whole class, 0..1. */
    val startFraction: Float,
    /** Width as a fraction of the whole class, 0..1. */
    val widthFraction: Float,
    /** Bar height as a fraction of the plot, 0..1. */
    val heightFraction: Float
)

data class ClassProfile(
    val blocks: List<ClassProfileBlock>,
    val totalSec: Int,
    /** The hardest zone the class asks for, or null when it asks for nothing. */
    val hardest: PowerZone?,
    /**
     * How long each run at [hardest] lasts, in the order they are ridden.
     *
     * Adjacent blocks at the hardest zone are **one** effort: a class that
     * splits a fifteen-minute threshold block in two to change the cadence has
     * not asked the rider to stop, and calling that two efforts would describe
     * a different workout.
     */
    val efforts: List<Int>
) {
    /** How long the class is. */
    val minutesLabel: String get() = "${(totalSec + 30) / 60} min"

    /**
     * Where the work is, in the words a rider would use out loud — *"four
     * 2 min efforts at VO2 Max"* — or null when the class asks for nothing.
     *
     * **Minutes rather than `mm:ss`.** A clock format is for reading a
     * measurement; this is describing a shape to somebody deciding what to
     * ride, and 15:00 is a worse way of saying fifteen minutes to them (Phase
     * 26). The interval tiles below still carry the exact figures.
     */
    val shape: String?
        get() {
            val zone = hardest ?: return null
            if (efforts.isEmpty()) return null
            return when {
                efforts.size == 1 ->
                    "one ${length(efforts.first())} effort at ${zone.displayName}"
                // Every effort the same length is the thing a rider recognises
                // as a workout — "4 × 2 min" is how the class would be described
                // out loud, and it is what the library's own titles already say.
                efforts.distinct().size == 1 ->
                    "${efforts.size} × ${length(efforts.first())} at ${zone.displayName}"
                else -> "${efforts.size} efforts at ${zone.displayName}"
            }
        }

    /**
     * The whole thing in one line — and the sentence a screen reader gets in
     * place of the canvas (16.2.4), which is why it lives here rather than
     * being assembled in the composable.
     */
    val summary: String
        get() = listOfNotNull(minutesLabel, shape).joinToString(" · ")

    companion object {
        /** `15 min`, `45 sec`, or the clock when it is neither. */
        private fun length(seconds: Int): String = when {
            seconds >= 60 && seconds % 60 == 0 -> "${seconds / 60} min"
            seconds < 60 -> "$seconds sec"
            else -> Formatters.duration(seconds)
        }

        /**
         * Zone 1 still draws a bar.
         *
         * A strictly proportional height would put recovery at a seventh of the
         * plot and a warm-up would read as a gap rather than as riding. The
         * floor keeps the low blocks legible while leaving the ordering and the
         * spacing between zones exactly as they are.
         */
        private const val MIN_HEIGHT = 0.12f

        fun of(intervals: List<Interval>): ClassProfile {
            val ordered = intervals.sortedBy { it.startSec }
            val total = ordered.maxOfOrNull { it.endSec } ?: 0
            if (ordered.isEmpty() || total <= 0) {
                return ClassProfile(emptyList(), 0, null, emptyList())
            }

            val blocks = ordered.map { interval ->
                val zone = interval.powerZone
                val duration = (interval.endSec - interval.startSec).coerceAtLeast(0)
                ClassProfileBlock(
                    zone = zone,
                    startSec = interval.startSec,
                    durationSec = duration,
                    position = interval.position,
                    startFraction = interval.startSec.toFloat() / total,
                    widthFraction = duration.toFloat() / total,
                    heightFraction = MIN_HEIGHT +
                        (1f - MIN_HEIGHT) * (zone.number - 1) / (PowerZone.entries.size - 1)
                )
            }

            val hardest = blocks.maxByOrNull { it.zone.number }?.zone
            return ClassProfile(
                blocks = blocks,
                totalSec = total,
                hardest = hardest,
                efforts = effortsAt(blocks, hardest)
            )
        }

        /** Runs of consecutive blocks at [zone], merged, as their durations. */
        private fun effortsAt(blocks: List<ClassProfileBlock>, zone: PowerZone?): List<Int> {
            if (zone == null) return emptyList()
            val efforts = mutableListOf<Int>()
            var run = 0
            for (block in blocks) {
                if (block.zone == zone) {
                    run += block.durationSec
                } else if (run > 0) {
                    efforts += run
                    run = 0
                }
            }
            if (run > 0) efforts += run
            return efforts
        }
    }
}
