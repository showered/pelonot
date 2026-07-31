package com.pelonot.domain.chart

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

/** Everything the ride detail screen draws, computed once. */
data class RideCharts(
    val power: RideTrace = RideTrace(),
    val heartRate: RideTrace = RideTrace(),
    val cadence: CadenceDistribution = CadenceDistribution(),
    val timeInZone: TimeInZone = TimeInZone(),
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
        buckets: Int = DEFAULT_BUCKETS
    ): RideCharts {
        if (samples.isEmpty()) return RideCharts(ftpWatts = ftpWatts)

        val ordered = samples.sortedBy { it.timestampSec }

        return RideCharts(
            power = downsample(ordered, buckets) { it.powerWatts },
            heartRate = downsampleNullable(ordered, buckets) { it.heartRateBpm?.toDouble() },
            cadence = cadenceDistribution(ordered),
            timeInZone = timeInZone(ordered, ftpWatts),
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
