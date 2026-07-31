package com.pelonot.domain.chart

import com.pelonot.domain.model.Interval
import com.pelonot.domain.model.PowerZone
import kotlin.math.roundToInt

/**
 * One second of a ride, reduced to what the charts need.
 *
 * A separate type from `WorkoutMetricEntity` so everything in this file stays
 * free of Room and of Android, and is therefore JVM-testable.
 */
data class ChartSample(
    val timestampSec: Int,
    val powerWatts: Double,
    val cadenceRpm: Double,
    /** Null means **unknown**, never zero. See [heartRate]. */
    val heartRateBpm: Int?
)

/**
 * One bucket of a downsampled trace.
 *
 * Carries min and max, not just the mean, because **averaging erases the
 * sprint** — which is the single thing a rider most wants to see afterwards
 * (16.2.2). A 45-minute ride is ~2,700 points and a chart is a few hundred
 * pixels wide; collapsing 9 seconds into their mean turns a 600 W spike into a
 * 250 W bump.
 */
data class TraceBucket(
    val startSec: Int,
    val endSec: Int,
    val min: Double,
    val max: Double,
    val mean: Double
)

/**
 * A downsampled series ready to draw, with the axis range it needs.
 *
 * [isEmpty] is not the same as "all zeroes": a heart-rate trace with no strap
 * paired has no buckets at all, and drawing a line along the axis for it would
 * say the rider's heart stopped (16.1.2).
 */
data class RideTrace(
    val buckets: List<TraceBucket> = emptyList(),
    val maxValue: Double = 0.0,
    val minValue: Double = 0.0,
    val durationSec: Int = 0
) {
    val isEmpty: Boolean get() = buckets.isEmpty()
}

/** How long the ride spent in each zone, in seconds. */
data class TimeInZone(
    val secondsByZone: Map<PowerZone, Int> = emptyMap()
) {
    val totalSeconds: Int get() = secondsByZone.values.sum()

    fun fractionOf(zone: PowerZone): Float {
        val total = totalSeconds
        return if (total == 0) 0f else (secondsByZone[zone] ?: 0).toFloat() / total
    }

    /** Zones in order, skipping any the rider never entered. */
    val occupied: List<Pair<PowerZone, Int>>
        get() = PowerZone.entries
            .mapNotNull { zone -> secondsByZone[zone]?.takeIf { it > 0 }?.let { zone to it } }
}

/** How the ride's cadence was spread, in fixed rpm bands. */
data class CadenceDistribution(
    val bandWidthRpm: Int = CADENCE_BAND_RPM,
    val secondsByBand: Map<Int, Int> = emptyMap()
) {
    val totalSeconds: Int get() = secondsByBand.values.sum()

    val maxSeconds: Int get() = secondsByBand.values.maxOrNull() ?: 0

    /** `(lowRpm, highRpm, seconds)`, lowest band first, gaps included. */
    val bands: List<Triple<Int, Int, Int>>
        get() {
            if (secondsByBand.isEmpty()) return emptyList()
            val first = secondsByBand.keys.min()
            val last = secondsByBand.keys.max()
            return (first..last).map { band ->
                Triple(
                    band * bandWidthRpm,
                    (band + 1) * bandWidthRpm,
                    secondsByBand[band] ?: 0
                )
            }
        }
}

/**
 * One prescribed interval of the class, on the ride's own elapsed-second axis
 * (16.1.5).
 *
 * The target band is the interval's zone scaled by the **multiplier the ride
 * was ridden with** (`workouts.intent_modifier`), not by whatever the rider
 * would pick today — this is a record of what was asked for at the time.
 *
 * FTP is the one part that is not: the band is derived from the rider's
 * *current* FTP, exactly as the zone bands behind it are, so an FTP change
 * silently redraws every past ride's prescription. That is 7.8, and it is a
 * missing column rather than anything this file can fix.
 */
data class PrescribedSegment(
    val startSec: Int,
    val endSec: Int,
    val zone: PowerZone,
    val targetLowWatts: Double,
    val targetHighWatts: Double,
    /** Seconds of this segment the ride actually recorded. */
    val secondsRidden: Int,
    /** Of those, how many were inside the target band. */
    val secondsInBand: Int
) {
    val durationSec: Int get() = (endSec - startSec).coerceAtLeast(0)
}

/**
 * What the class asked for, ready to draw under what the rider did.
 *
 * Segments are **clipped to the ride**: a rider who abandons a 30-minute class
 * at 12 minutes gets 12 minutes of prescription, not 18 minutes of ghost plan
 * hanging off the end of a 12-minute axis. [classDurationSec] keeps the class's
 * full length so the difference can be said out loud.
 */
data class PrescribedPlan(
    val segments: List<PrescribedSegment> = emptyList(),
    /** The class as authored, before clipping. */
    val classDurationSec: Int = 0,
    /** Where the ride stopped, on the class's clock. */
    val riddenSec: Int = 0
) {
    val isEmpty: Boolean get() = segments.isEmpty()

    val secondsRidden: Int get() = segments.sumOf { it.secondsRidden }

    val secondsInBand: Int get() = segments.sumOf { it.secondsInBand }

    val fractionInBand: Float
        get() = if (secondsRidden == 0) 0f else secondsInBand.toFloat() / secondsRidden

    /** True when the rider saw the class out rather than stopping part way. */
    val finishedClass: Boolean
        get() = classDurationSec > 0 && riddenSec >= classDurationSec - FINISH_TOLERANCE_SEC

    /** The highest target *floor*, which is what the chart has to leave room for. */
    val highestTargetFloor: Double
        get() = segments.maxOfOrNull { it.targetLowWatts } ?: 0.0

    private companion object {
        /** A class ending on the tick is rare; a few seconds short is not. */
        const val FINISH_TOLERANCE_SEC = 5
    }
}

/** Everything the ride detail screen draws, computed once. */
data class RideCharts(
    val power: RideTrace = RideTrace(),
    val heartRate: RideTrace = RideTrace(),
    val cadence: CadenceDistribution = CadenceDistribution(),
    val timeInZone: TimeInZone = TimeInZone(),
    val prescribed: PrescribedPlan = PrescribedPlan(),
    val ftpWatts: Int = 0,
    /** True when these watts came off the board rather than out of the model. */
    val powerIsMeasured: Boolean = false
) {
    val hasAnything: Boolean
        get() = !power.isEmpty || !heartRate.isEmpty || cadence.totalSeconds > 0
}

/**
 * Turns a ride's per-second series into the four things worth drawing.
 *
 * Pure and synchronous. Whoever calls it runs it off the main thread and
 * caches the result on the ride (16.2.3) — a 90-minute ride is ~5,400 samples
 * and this walks them a handful of times.
 */
object RideChartBuilder {

    fun build(
        samples: List<ChartSample>,
        ftpWatts: Int,
        powerIsMeasured: Boolean = false,
        /** The class this ride was ridden to, or empty for a free ride. */
        intervals: List<Interval> = emptyList(),
        /** `workouts.intent_modifier` — the multiplier the ride was ridden with. */
        intentMultiplier: Double = 1.0,
        buckets: Int = DEFAULT_BUCKETS
    ): RideCharts {
        if (samples.isEmpty()) return RideCharts(ftpWatts = ftpWatts)

        val ordered = samples.sortedBy { it.timestampSec }

        return RideCharts(
            power = downsample(ordered, buckets) { it.powerWatts },
            heartRate = downsampleNullable(ordered, buckets) { it.heartRateBpm?.toDouble() },
            cadence = cadenceDistribution(ordered),
            timeInZone = timeInZone(ordered, ftpWatts),
            prescribed = prescribedPlan(ordered, intervals, ftpWatts, intentMultiplier),
            ftpWatts = ftpWatts,
            powerIsMeasured = powerIsMeasured
        )
    }

    /**
     * Buckets by **elapsed time**, not by sample index.
     *
     * A ride with dropped samples — which is exactly what 10.6 is looking for
     * — has gaps in its series, and bucketing by index would silently
     * compress the gap out and draw a ride that looks continuous.
     */
    private inline fun downsample(
        samples: List<ChartSample>,
        buckets: Int,
        value: (ChartSample) -> Double
    ): RideTrace = downsampleNullable(samples, buckets) { value(it) }

    private inline fun downsampleNullable(
        samples: List<ChartSample>,
        buckets: Int,
        value: (ChartSample) -> Double?
    ): RideTrace {
        val present = samples.mapNotNull { sample ->
            value(sample)?.let { sample.timestampSec to it }
        }
        if (present.isEmpty()) return RideTrace()

        val duration = samples.last().timestampSec - samples.first().timestampSec
        val start = samples.first().timestampSec
        val width = ((duration + 1).toDouble() / buckets).coerceAtLeast(1.0)

        val grouped = present.groupBy { (sec, _) ->
            ((sec - start) / width).toInt().coerceIn(0, buckets - 1)
        }

        val out = grouped.entries.sortedBy { it.key }.map { (index, points) ->
            val values = points.map { it.second }
            TraceBucket(
                startSec = start + (index * width).toInt(),
                endSec = start + ((index + 1) * width).toInt(),
                min = values.min(),
                max = values.max(),
                mean = values.average()
            )
        }

        return RideTrace(
            buckets = out,
            maxValue = out.maxOf { it.max },
            minValue = out.minOf { it.min },
            durationSec = duration.coerceAtLeast(1)
        )
    }

    /**
     * One second per sample.
     *
     * Not `endSec - startSec`: a recovered ride (8.3) has gaps, and charging
     * the rider for time nothing was recorded in would overstate every band.
     */
    private fun cadenceDistribution(samples: List<ChartSample>): CadenceDistribution {
        val bands = samples
            // Coasting is not a cadence. Without this every ride has a large
            // spike in the 0-9 band that says nothing about how it was ridden.
            .filter { it.cadenceRpm >= MIN_CHARTED_CADENCE }
            .groupingBy { (it.cadenceRpm / CADENCE_BAND_RPM).toInt() }
            .eachCount()

        return CadenceDistribution(secondsByBand = bands)
    }

    private fun timeInZone(samples: List<ChartSample>, ftpWatts: Int): TimeInZone {
        if (ftpWatts <= 0) return TimeInZone()

        val byZone = samples
            .groupingBy { PowerZone.forPower(it.powerWatts, ftpWatts.toDouble()) }
            .eachCount()

        return TimeInZone(secondsByZone = byZone)
    }

    /**
     * What the class asked for, over the part of it that was ridden (16.1.5).
     *
     * Metric timestamps and interval boundaries are the same clock — the
     * service records at `elapsedSec` and asks the interval engine for
     * `stateAt(elapsedSec)` on the same tick — so no alignment is needed beyond
     * clipping the class to where the ride stopped.
     *
     * Needs an FTP: without one there is no band to be inside, and a
     * prescription drawn against a guessed FTP would be a fiction shown beside
     * a record.
     */
    private fun prescribedPlan(
        samples: List<ChartSample>,
        intervals: List<Interval>,
        ftpWatts: Int,
        intentMultiplier: Double
    ): PrescribedPlan {
        if (intervals.isEmpty() || ftpWatts <= 0 || samples.isEmpty()) return PrescribedPlan()

        val lastSec = samples.last().timestampSec
        val classDuration = intervals.maxOf { it.endSec }

        val segments = intervals
            .filter { it.startSec < lastSec && it.durationSec > 0 }
            .map { interval ->
                // Clipped to the ride's *duration*, which is one less than its
                // sample count — a ride from second 0 to second 631 is 631
                // seconds long, and a prescription totalling 632 next to a
                // duration of 631 reads as an error in the same sentence.
                val end = minOf(interval.endSec, lastSec)
                val band = interval.powerZone.powerRange(ftpWatts.toDouble())
                val low = band.start * intentMultiplier
                val high = band.endInclusive * intentMultiplier
                val ridden = samples.filter { it.timestampSec in interval.startSec until end }

                PrescribedSegment(
                    startSec = interval.startSec,
                    endSec = end,
                    zone = interval.powerZone,
                    targetLowWatts = low,
                    targetHighWatts = high,
                    secondsRidden = ridden.size,
                    secondsInBand = ridden.count { it.powerWatts in low..high }
                )
            }
            .filter { it.durationSec > 0 }

        return PrescribedPlan(
            segments = segments,
            classDurationSec = classDuration,
            riddenSec = minOf(lastSec, classDuration)
        )
    }

    private const val DEFAULT_BUCKETS = 300
    private const val MIN_CHARTED_CADENCE = 20.0
}

/**
 * A sentence saying what each chart shows (16.2.4).
 *
 * A chart is unreadable to a screen reader, and a fair amount of this data is
 * a sentence anyway — "you spent most of it in Endurance and touched Threshold
 * twice" is arguably the more useful form for everyone.
 */
object RideChartSummaries {

    fun power(trace: RideTrace, isMeasured: Boolean): String {
        if (trace.isEmpty) return "No power was recorded for this ride."
        val source = if (isMeasured) "measured" else "estimated"
        return "Power over ${formatDuration(trace.durationSec)}, $source. " +
            "Peak ${trace.maxValue.roundToInt()} watts, " +
            "average ${trace.buckets.map { it.mean }.average().roundToInt()} watts."
    }

    /**
     * What you were asked for against what you did (16.1.5).
     *
     * Empty for a free ride, which was not asked for anything — a sentence
     * saying "0% of nothing" would be worse than silence.
     */
    fun prescribed(plan: PrescribedPlan): String {
        if (plan.isEmpty || plan.secondsRidden == 0) return ""

        val percent = (plan.fractionInBand * 100).roundToInt()
        val compliance = "Inside the class's target power for " +
            "${formatDuration(plan.secondsInBand)} of " +
            "${formatDuration(plan.secondsRidden)} prescribed — $percent%."

        if (plan.finishedClass) return compliance

        return "$compliance The class runs ${formatDuration(plan.classDurationSec)} " +
            "and this ride stopped at ${formatDuration(plan.riddenSec)}."
    }

    fun heartRate(trace: RideTrace): String {
        if (trace.isEmpty) {
            return "No heart rate was recorded for this ride — no strap was paired."
        }
        return "Heart rate from ${trace.minValue.roundToInt()} to " +
            "${trace.maxValue.roundToInt()} beats per minute."
    }

    fun cadence(distribution: CadenceDistribution): String {
        if (distribution.totalSeconds == 0) return "No cadence was recorded."
        val busiest = distribution.secondsByBand.maxByOrNull { it.value } ?: return ""
        val low = busiest.key * distribution.bandWidthRpm
        return "Most of the ride was spent between $low and " +
            "${low + distribution.bandWidthRpm} rpm."
    }

    fun timeInZone(timeInZone: TimeInZone): String {
        if (timeInZone.totalSeconds == 0) {
            return "Time in zone needs an FTP, and this ride has none."
        }
        return timeInZone.occupied.joinToString(prefix = "Time in zone: ") { (zone, seconds) ->
            "${zone.displayName} ${formatDuration(seconds)}"
        }
    }

    private fun formatDuration(totalSeconds: Int): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return when {
            minutes == 0 -> "$seconds seconds"
            seconds == 0 -> "$minutes minutes"
            else -> "$minutes minutes $seconds seconds"
        }
    }
}

internal const val CADENCE_BAND_RPM = 10
