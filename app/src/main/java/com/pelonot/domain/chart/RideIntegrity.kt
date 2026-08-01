package com.pelonot.domain.chart

import com.pelonot.data.sensor.TelemetryBounds
import com.pelonot.data.sensor.TelemetryField

/**
 * What the fence makes of a ride that was recorded before the fence existed
 * (2.7.5).
 *
 * Two rides on this bike were recorded while `msg.what` was still trusted, and
 * they contain values no bike produced — 603 rpm off a rider turning the cranks
 * at 61, a 636 W spike off a rider averaging 47 W. They are **real rides with
 * real effort in them and a minority of corrupted samples**: deleting them
 * would throw away the effort, and presenting their stored averages as fact
 * would repeat the lie the ride summary told on the day (109 rpm, 137 W).
 *
 * So nothing is rewritten. The samples stay exactly as they were recorded, the
 * `workouts.avg_*` columns stay exactly as they were computed, and this counts
 * what is wrong with them **on read** so a screen can say so out loud. It is
 * the same argument as [com.pelonot.domain.model.PowerProvenance]: the honest
 * move is to make the record's provenance visible, not to quietly improve it.
 *
 * A sample is judged **whole**. One impossible field means the whole row is
 * discarded, because the labelling defect that produced it puts the other two
 * values in each other's columns — in range, and wrong. That is the rule
 * `TelemetryAssembler` follows live, and this is the same rule applied
 * afterwards.
 *
 * From the frame fix onward no ride can accumulate any of this, so a clean
 * ride is the only kind there will be. That is why nothing here is a migration.
 */
data class RideIntegrity(
    val totalSamples: Int = 0,
    val impossibleSamples: Int = 0,
    /**
     * The average over the samples the fence accepts.
     *
     * Null when there are none left — a ride that is entirely impossible has no
     * corrected figure to offer, and inventing one from zero samples is the
     * failure `avg_hr` made for the project's whole history.
     */
    val cleanAvgPowerWatts: Double? = null,
    val cleanAvgCadenceRpm: Double? = null
) {
    val cleanSamples: Int get() = totalSamples - impossibleSamples

    /** True for a ride holding at least one value a bike could not produce. */
    val isSuspect: Boolean get() = impossibleSamples > 0

    /** 0–100, for a sentence rather than for a decision. */
    val percentImpossible: Double
        get() = if (totalSamples == 0) 0.0 else impossibleSamples * 100.0 / totalSamples

    companion object {
        /**
         * Judges a ride's own series against the same bounds the recorder uses.
         *
         * The bounds come from [TelemetryBounds] rather than being restated
         * here on purpose: a second copy of the fence is a second thing to keep
         * in step, and the whole point of counting these samples is that the
         * count means the same thing as the rejection did.
         */
        fun of(samples: List<ChartSample>): RideIntegrity {
            if (samples.isEmpty()) return RideIntegrity()

            val clean = samples.filter { it.isPlausible }
            return RideIntegrity(
                totalSamples = samples.size,
                impossibleSamples = samples.size - clean.size,
                cleanAvgPowerWatts = clean.map { it.powerWatts }.averageOrNull(),
                cleanAvgCadenceRpm = clean.map { it.cadenceRpm }.averageOrNull()
            )
        }

        private fun List<Double>.averageOrNull(): Double? =
            if (isEmpty()) null else average()
    }
}

/**
 * True when every field of this sample is one a bike could have produced.
 *
 * Resistance is checked even though no chart draws it: a 602% resistance is the
 * signature of the labelling defect, and the cadence and power beside it in
 * that row are then in range and in the wrong columns.
 */
val ChartSample.isPlausible: Boolean
    get() = TelemetryBounds.accepts(TelemetryField.Cadence, cadenceRpm) &&
        TelemetryBounds.accepts(TelemetryField.Power, powerWatts) &&
        TelemetryBounds.accepts(TelemetryField.Resistance, resistancePercent)
