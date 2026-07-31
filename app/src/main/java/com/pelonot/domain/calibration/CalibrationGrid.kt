package com.pelonot.domain.calibration

import kotlin.math.abs

/** One measured operating point: what the rider did, and what the board read. */
data class CalibrationSample(
    val cadenceRpm: Double,
    val resistancePercent: Double,
    val measuredWatts: Double
)

/**
 * One cell of the resistance × cadence grid, holding a running mean.
 *
 * @property weight Decays as the cell ages, so a re-fit tracks the mechanism as
 *   it is now rather than averaging a worn machine together with how it behaved
 *   when it was new (2.2a.5).
 */
data class CalibrationCell(
    val resistanceBin: Int,
    val cadenceBin: Int,
    val meanCadenceRpm: Double,
    val meanResistancePercent: Double,
    val meanWatts: Double,
    val samples: Int,
    val weight: Double
) {
    val point: CalibrationSample
        get() = CalibrationSample(meanCadenceRpm, meanResistancePercent, meanWatts)
}

/**
 * What the app remembers about this bike's own power curve.
 *
 * **A grid of binned means, not a sample log.** On real hardware every ride is
 * already a calibration dataset — the board reports measured watts beside the
 * cadence and resistance the model takes as inputs (2.1a) — so a 45-minute
 * ride contributes ~2,700 samples and a year of riding would contribute
 * millions. Binning collapses that to at most a few dozen numbers that do not
 * grow with use, and a mean over a cell is a better operating point than any
 * single second of it.
 *
 * This is **device state, not rider state** (2.2a.2): a household bike has
 * several profiles and one resistance mechanism.
 */
data class CalibrationGrid(
    val cells: Map<CellKey, CalibrationCell> = emptyMap()
) {

    data class CellKey(val resistanceBin: Int, val cadenceBin: Int)

    val sampleCount: Int get() = cells.values.sumOf { it.samples }

    /** Distinct resistance levels ridden — the axis the fit is weakest on. */
    val resistanceLevelsCovered: Int
        get() = cells.keys.map { it.resistanceBin }.distinct().size

    /**
     * Resistance levels that have been ridden at three or more cadences.
     *
     * A level ridden at one cadence pins a point, not a slope, and the
     * 31 July sweep's failure was exactly this: six levels, and the exponent
     * still undetermined.
     */
    val wellSampledResistanceLevels: Int
        get() = cells.keys
            .groupBy { it.resistanceBin }
            .count { (_, keys) -> keys.size >= MIN_CADENCES_PER_LEVEL }

    /** Whether there is enough of the range to fit anything honest (2.2a.4). */
    val hasEnoughCoverage: Boolean
        get() = wellSampledResistanceLevels >= MIN_RESISTANCE_LEVELS &&
            resistanceSpan >= MIN_RESISTANCE_SPAN

    /** Percentage points between the lowest and highest resistance ridden. */
    val resistanceSpan: Double
        get() {
            val means = cells.values.map { it.meanResistancePercent }
            if (means.isEmpty()) return 0.0
            return means.max() - means.min()
        }

    /** 0..1, for telling the rider how much of the range this bike has seen. */
    val coverageFraction: Float
        get() = (wellSampledResistanceLevels.toFloat() / MIN_RESISTANCE_LEVELS)
            .coerceIn(0f, 1f)

    /**
     * Folds in one steady-state sample.
     *
     * Rejects anything that is not an operating point. The board reports
     * during transitions too, and a sample taken while the knob is mid-turn
     * pairs one instant's resistance with the flywheel's response to a
     * different one — which is exactly the noise the 31 July sweep had to be
     * filtered for by hand.
     */
    fun plus(sample: CalibrationSample): CalibrationGrid {
        if (!sample.isUsable()) return this

        val key = CellKey(
            resistanceBin = (sample.resistancePercent / RESISTANCE_BIN_WIDTH).toInt(),
            cadenceBin = (sample.cadenceRpm / CADENCE_BIN_WIDTH).toInt()
        )

        val existing = cells[key]
        val updated = if (existing == null) {
            CalibrationCell(
                resistanceBin = key.resistanceBin,
                cadenceBin = key.cadenceBin,
                meanCadenceRpm = sample.cadenceRpm,
                meanResistancePercent = sample.resistancePercent,
                meanWatts = sample.measuredWatts,
                samples = 1,
                weight = 1.0
            )
        } else {
            val n = existing.samples + 1
            existing.copy(
                // Incremental means: the whole point of the grid is that the
                // samples themselves are not kept.
                meanCadenceRpm = existing.meanCadenceRpm +
                    (sample.cadenceRpm - existing.meanCadenceRpm) / n,
                meanResistancePercent = existing.meanResistancePercent +
                    (sample.resistancePercent - existing.meanResistancePercent) / n,
                meanWatts = existing.meanWatts +
                    (sample.measuredWatts - existing.meanWatts) / n,
                samples = n,
                weight = existing.weight + 1.0
            )
        }

        return copy(cells = cells + (key to updated))
    }

    /** Folds in a whole ride, dropping transitions between operating points. */
    fun plusRide(samples: List<CalibrationSample>): CalibrationGrid =
        steadyStateOf(samples).fold(this) { grid, sample -> grid.plus(sample) }

    /**
     * Ages every cell by [factor], so a re-fit follows drift (2.2a.5).
     *
     * Applied once per ride rather than per sample: a long ride should not
     * discount its own beginning.
     */
    fun decayed(factor: Double = DECAY_PER_RIDE): CalibrationGrid = copy(
        cells = cells.mapValues { (_, cell) -> cell.copy(weight = cell.weight * factor) }
            // A cell nobody has ridden for a long time stops being evidence
            // about a mechanism that has since worn.
            .filterValues { it.weight >= MIN_CELL_WEIGHT }
    )

    val points: List<CalibrationSample> get() = cells.values.map { it.point }

    companion object {
        const val RESISTANCE_BIN_WIDTH = 10.0
        const val CADENCE_BIN_WIDTH = 10.0

        /**
         * How many resistance levels, each ridden at three or more cadences,
         * before a fit is allowed at all.
         *
         * Six was not enough on the 31 July sweep — the exponent stayed
         * undetermined and the fit failed cross-validation — so this asks for
         * more than that sweep had, which is the whole point of 2.2a.4.
         */
        const val MIN_RESISTANCE_LEVELS = 7
        const val MIN_CADENCES_PER_LEVEL = 3

        /** Levels clustered together determine an exponent no better than one. */
        const val MIN_RESISTANCE_SPAN = 40.0

        const val DECAY_PER_RIDE = 0.97
        const val MIN_CELL_WEIGHT = 0.5

        /**
         * Steady state, by the same rule that was applied by hand to the
         * 31 July sweep: neither input may be moving.
         *
         * Cadence swings a few rpm every pedal stroke, so the threshold is on
         * the *trend* across a second, not on stroke-to-stroke variation.
         */
        fun steadyStateOf(samples: List<CalibrationSample>): List<CalibrationSample> {
            if (samples.size < 3) return emptyList()

            return samples.filterIndexed { index, sample ->
                if (index == 0 || index == samples.lastIndex) return@filterIndexed false
                val before = samples[index - 1]
                val after = samples[index + 1]

                sample.isUsable() &&
                    abs(after.resistancePercent - before.resistancePercent) <=
                    MAX_RESISTANCE_DRIFT &&
                    abs(after.cadenceRpm - before.cadenceRpm) <= MAX_CADENCE_DRIFT
            }
        }

        private const val MAX_RESISTANCE_DRIFT = 1.5
        private const val MAX_CADENCE_DRIFT = 6.0
    }
}

/**
 * Whether a sample describes a rider pedalling under load at all.
 *
 * Coasting, freewheeling and the seconds either side of a stop are not
 * operating points, and a cell full of them would drag its own mean towards
 * zero watts at a cadence the rider never actually held.
 */
internal fun CalibrationSample.isUsable(): Boolean =
    cadenceRpm >= MIN_USABLE_CADENCE &&
        cadenceRpm <= MAX_USABLE_CADENCE &&
        resistancePercent in 0.0..100.0 &&
        measuredWatts > MIN_USABLE_WATTS &&
        measuredWatts <= PowerCurve.MAX_PLAUSIBLE_WATTS

private const val MIN_USABLE_CADENCE = 30.0
private const val MAX_USABLE_CADENCE = 140.0
private const val MIN_USABLE_WATTS = 10.0
