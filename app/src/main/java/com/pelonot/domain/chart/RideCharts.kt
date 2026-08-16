package com.pelonot.domain.chart

import com.pelonot.core.Formatters
import com.pelonot.domain.model.AutoPausePolicy
import com.pelonot.domain.model.HeartRateZone
import com.pelonot.domain.model.Interval
import com.pelonot.domain.model.MaxHeartRate
import com.pelonot.domain.model.PowerProvenance
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
    val heartRateBpm: Int?,
    /**
     * Carried although nothing draws it, because [isPlausible] reads it: an
     * impossible resistance is what marks a row whose other two values have
     * swapped columns (2.7.5).
     */
    val resistancePercent: Double = 0.0
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

/**
 * How long the ride spent in each zone, in seconds.
 *
 * **The zones divide the seconds the rider was pedalling, not the seconds the
 * ride recorded (19.1.2c).** A second at zero cadence is a second nobody rode —
 * a bottle stop, or the twenty an auto-pause has to watch before it can know a
 * stop is a stop — and filing it under *Z1 Active Recovery* puts a claim about
 * riding easily on time spent standing still. It was measured doing exactly
 * that: 21 of 79 Z1 seconds on a 3:09 ride, and Z1 was the largest thing on the
 * card.
 *
 * So [secondsStopped] is kept beside the zones for [TimeInHeartRateZone]'s
 * reason, one metric along — the card can say what its percentages are out of.
 * The seconds are **not** drawn as a wedge in the bar: 21.4.1 refused that for
 * the heart and the argument is the same, an absence on the same footing as a
 * zone is the same mistake wearing a colour.
 */
data class TimeInZone(
    val secondsByZone: Map<PowerZone, Int> = emptyMap(),
    /** Recorded seconds with the cranks still. See the note above. */
    val secondsStopped: Int = 0
) {
    /** Seconds the rider was pedalling, which is what the zones divide. */
    val totalSeconds: Int get() = secondsByZone.values.sum()

    /** Every second the ride recorded, whether it was ridden or not. */
    val recordedSeconds: Int get() = totalSeconds + secondsStopped

    /** True when some of the recorded ride was spent standing still. */
    val isPartial: Boolean get() = totalSeconds > 0 && secondsStopped > 0

    fun fractionOf(zone: PowerZone): Float {
        val total = totalSeconds
        return if (total == 0) 0f else (secondsByZone[zone] ?: 0).toFloat() / total
    }

    /** Zones in order, skipping any the rider never entered. */
    val occupied: List<Pair<PowerZone, Int>>
        get() = PowerZone.entries
            .mapNotNull { zone -> secondsByZone[zone]?.takeIf { it > 0 }?.let { zone to it } }
}

/**
 * How long the ride spent in each heart-rate zone, in seconds (21.4.1).
 *
 * **Not [TimeInZone] with a different enum in it**, for one reason that matters:
 * power is recorded for every second of a ride and a heart rate is not. A strap
 * that was never paired, or that dropped out at minute twelve, leaves seconds
 * that are *unknown* rather than seconds spent in H1 — so the zones are counted
 * against [totalSeconds], the time a heart rate was actually being reported, and
 * [secondsUnrecorded] is kept beside them so the screen can say how much of the
 * ride that was. A percentage of the whole ride would read as effort and be
 * coverage.
 *
 * Empty for a rider whose maximum heart rate the app does not know. That is
 * 21.2.4 and the same rule the bands follow: a denominator nobody supplied is a
 * guess about a body.
 */
data class TimeInHeartRateZone(
    val secondsByZone: Map<HeartRateZone, Int> = emptyMap(),
    /** Recorded seconds with no heart rate in them — no strap, or a dropout. */
    val secondsUnrecorded: Int = 0
) {
    /** Seconds a heart rate was reported for, which is what the zones divide. */
    val totalSeconds: Int get() = secondsByZone.values.sum()

    /** Every second the ride recorded, whether a heart was heard in it or not. */
    val recordedSeconds: Int get() = totalSeconds + secondsUnrecorded

    /** True when the strap was reporting for some of the ride but not all of it. */
    val isPartial: Boolean get() = totalSeconds > 0 && secondsUnrecorded > 0

    fun fractionOf(zone: HeartRateZone): Float {
        val total = totalSeconds
        return if (total == 0) 0f else (secondsByZone[zone] ?: 0).toFloat() / total
    }

    /** Zones in order, skipping any the rider never entered. */
    val occupied: List<Pair<HeartRateZone, Int>>
        get() = HeartRateZone.entries
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
    /**
     * The prescribed cadence, in rpm (16.1.5a).
     *
     * **Not scaled by the intent multiplier**, unlike the watts above. Riding
     * *easier* than the class asks means less power; it does not mean turning
     * the pedals more slowly, and a 60–70 rpm torque block ridden at 0.9× is
     * still a 60–70 rpm torque block. The multiplier is a power thing.
     */
    val targetCadenceLow: Int,
    val targetCadenceHigh: Int,
    /** Seconds of this segment the ride actually recorded. */
    val secondsRidden: Int,
    /** Of those, how many were inside the target band. */
    val secondsInBand: Int,
    /** And how many were inside the prescribed cadence, which is a separate question. */
    val secondsInCadenceBand: Int
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

    val secondsInCadenceBand: Int get() = segments.sumOf { it.secondsInCadenceBand }

    val fractionInCadenceBand: Float
        get() = if (secondsRidden == 0) 0f else secondsInCadenceBand.toFloat() / secondsRidden

    /** True when the rider saw the class out rather than stopping part way. */
    val finishedClass: Boolean
        get() = classDurationSec > 0 && riddenSec >= classDurationSec - FINISH_TOLERANCE_SEC

    /** The highest target *floor*, which is what the chart has to leave room for. */
    val highestTargetFloor: Double
        get() = segments.maxOfOrNull { it.targetLowWatts } ?: 0.0

    /**
     * The top of the prescribed cadence, which the cadence chart *does* have to
     * fit whole — unlike the power bands, where a Z7 ceiling at twice FTP would
     * flatten the trace. Cadence targets live in a narrow, human range and a
     * block drawn half off the top would be the one the rider most wants to
     * see.
     */
    val highestTargetCadence: Int
        get() = segments.maxOfOrNull { it.targetCadenceHigh } ?: 0

    private companion object {
        /** A class ending on the tick is rare; a few seconds short is not. */
        const val FINISH_TOLERANCE_SEC = 5
    }
}

/** Everything the ride detail screen draws, computed once. */
data class RideCharts(
    val power: RideTrace = RideTrace(),
    val heartRate: RideTrace = RideTrace(),
    /**
     * Cadence on the clock (16.1.5a), which is a different question from
     * [cadence] and the only one a *prescribed* cadence can be drawn against —
     * a distribution has no time axis to lay a target on.
     *
     * **Zeros are drawn**, unlike the distribution, which excludes them. There
     * the 0–9 band is a spike that says nothing about how the ride was ridden;
     * here a coast is a real thing that happened at a real moment, and it is
     * measured rather than unknown. That is the line between this and the heart
     * rate trace, which refuses to draw a gap at all.
     */
    val cadenceTrace: RideTrace = RideTrace(),
    val cadence: CadenceDistribution = CadenceDistribution(),
    val timeInZone: TimeInZone = TimeInZone(),
    /**
     * The same question asked of the heart instead of the pedals (21.4.1).
     *
     * Empty whenever [maxHrBpm] is null, so a rider the app has no maximum for
     * gets no heart-rate zones at all rather than five drawn off a default.
     */
    val timeInHeartRateZone: TimeInHeartRateZone = TimeInHeartRateZone(),
    val prescribed: PrescribedPlan = PrescribedPlan(),
    /**
     * What the class asked for against what the heart says was done (21.6.3),
     * or null whenever there is nothing honest to compare — which is most
     * rides. See [EffortAgainstPlan] for every reason it is null.
     */
    val effortAgainstPlan: EffortAgainstPlan? = null,
    val ftpWatts: Int = 0,
    /**
     * True when [ftpWatts] is the FTP **this ride was judged against**, false
     * when it is the rider's current one standing in for a ride recorded before
     * `workouts.ftp_watts` existed (7.8.4).
     *
     * The distinction is the whole point of 7.8: zone bands drawn from a number
     * the ride never saw are a re-derivation, not a record, and they must not
     * be presented with the same authority as the real thing.
     */
    val ftpIsTheRides: Boolean = false,
    /**
     * The maximum heart rate the heart-rate zone bands are drawn from (21.4.2),
     * or null when the app has none for this rider.
     *
     * Null means **no bands at all**, never a default: a maximum nobody gave is
     * a guess about a body, and it is the same rule `HeartRateZone.forHeartRate`
     * follows (21.2.4).
     */
    val maxHrBpm: Int? = null,
    /**
     * True when [maxHrBpm] is the maximum **this ride was judged against**,
     * false when it is the rider's current one standing in for a ride recorded
     * before `workouts.max_hr_bpm` existed (21.4.2a).
     *
     * Exactly [ftpIsTheRides]'s distinction, for the other denominator: bands
     * drawn from a number the ride never saw are a re-derivation rather than a
     * record, and the screen says so instead of presenting both alike.
     */
    val maxHrIsTheRides: Boolean = false,
    /**
     * Where [maxHrBpm] came from — the rider's own measurement or Tanaka's
     * estimate from their date of birth (21.4.2c).
     *
     * Null means **nobody wrote it down**, which is what every ride recorded
     * before `workouts.max_hr_source` existed says, and a card that does not
     * know says nothing rather than guessing. An estimate has a 10–12 bpm
     * spread — wider than a zone — so drawing bands from one is fine and
     * drawing them silently is not (21.5.5).
     *
     * When [maxHrIsTheRides] is false this is the *rider's current* source,
     * which is not a guess: the number beside it is today's too, and the card
     * already says so.
     */
    val maxHrSource: MaxHeartRate.Source? = null,
    /** Where these watts came from — the board, the model, or both (16.1.6). */
    val powerProvenance: PowerProvenance = PowerProvenance.Unknown,
    /**
     * How many seconds one stored sample stands for — 1 for a ride whose
     * record is intact, 10 for one 23.4 has trimmed (23.4.3).
     *
     * **The whole discipline of trimming is this number being carried rather
     * than assumed.** A ten-second outline drawn without it looks exactly like
     * a ride: the line has the same shape, the same peak and the same axis, and
     * nothing on the screen distinguishes the record from a sketch of it. It is
     * the same family as [ftpIsTheRides] and [powerProvenance] — a chart saying
     * what it is drawn from instead of presenting everything with one authority.
     */
    val detailSec: Int = 1,
    /**
     * The FTP the stored time-in-zone was counted against, or null when the
     * counts came from this ride's own samples just now.
     *
     * Only ever differs from [ftpWatts] for a ride that never recorded its own
     * FTP (7.8) and has since been trimmed: the counts were frozen against
     * whatever the rider's FTP was that day, and the screen says so rather than
     * letting them pass as today's zones.
     */
    val zoneFtpWatts: Int? = null,
    /**
     * The maximum heart rate the stored heart-rate zone counts were made
     * against, or null when the counts came from this ride's own samples just
     * now.
     *
     * [zoneFtpWatts] for the other denominator, and it exists for the same
     * narrow case: a ride that never recorded its own maximum (21.2.3) and has
     * since been trimmed. The counts were frozen against whatever the rider's
     * maximum was that day, and the screen says so rather than letting them pass
     * as today's zones.
     */
    val zoneMaxHrBpm: Int? = null,
    /**
     * What the fence makes of the ride's own samples (2.7.5).
     *
     * Everything above is drawn from the samples it accepts. On every ride
     * recorded since the frame fix that is all of them.
     */
    val integrity: RideIntegrity = RideIntegrity()
) {
    val hasAnything: Boolean
        get() = !power.isEmpty || !heartRate.isEmpty || cadence.totalSeconds > 0

    /** True when this ride's seconds have been thinned out (23.4.2). */
    val isTrimmed: Boolean get() = detailSec > 1
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
        powerProvenance: PowerProvenance = PowerProvenance.Unknown,
        /** The class this ride was ridden to, or empty for a free ride. */
        intervals: List<Interval> = emptyList(),
        /** `workouts.intent_modifier` — the multiplier the ride was ridden with. */
        intentMultiplier: Double = 1.0,
        /** See [RideCharts.ftpIsTheRides]. */
        ftpIsTheRides: Boolean = false,
        /** See [RideCharts.maxHrBpm] — null draws no heart-rate zone bands. */
        maxHrBpm: Int? = null,
        /** See [RideCharts.maxHrIsTheRides]. */
        maxHrIsTheRides: Boolean = false,
        /** See [RideCharts.maxHrSource] — null says nothing rather than guessing. */
        maxHrSource: MaxHeartRate.Source? = null,
        /** See [RideCharts.detailSec] — 1 for a ride whose record is intact. */
        detailSec: Int = 1,
        /**
         * What the ride's seconds said before they were trimmed (23.4.2), or
         * null for a ride that still has them.
         *
         * Only ever read for the two charts that are counts of seconds. The
         * traces are drawn from whatever samples are actually there, because a
         * line through the surviving points is honest at its own resolution
         * whereas a count of them is simply wrong.
         */
        stored: RideDistributions? = null,
        buckets: Int = DEFAULT_BUCKETS
    ): RideCharts {
        if (samples.isEmpty()) {
            return RideCharts(
                ftpWatts = ftpWatts,
                ftpIsTheRides = ftpIsTheRides,
                maxHrBpm = maxHrBpm,
                maxHrIsTheRides = maxHrIsTheRides,
                maxHrSource = maxHrSource,
                detailSec = detailSec,
                zoneFtpWatts = stored?.ftpWatts,
                zoneMaxHrBpm = stored?.maxHrBpm,
                timeInZone = stored?.timeInZone() ?: TimeInZone(),
                timeInHeartRateZone =
                    stored?.heartRateTimeInZone() ?: TimeInHeartRateZone(),
                cadence = stored?.cadence() ?: CadenceDistribution()
            )
        }

        // 2.7.5. A sample the fence would have rejected is left out of every
        // trace, distribution and total below — the same treatment a rejected
        // reading gets live, which is a gap rather than a clamped value. The
        // count survives on `integrity` so the screen can say the ride was
        // drawn short rather than quietly drawing it short.
        val integrity = RideIntegrity.of(samples)
        val ordered = samples.filter { it.isPlausible }.sortedBy { it.timestampSec }

        if (ordered.isEmpty()) {
            return RideCharts(
                ftpWatts = ftpWatts,
                ftpIsTheRides = ftpIsTheRides,
                maxHrBpm = maxHrBpm,
                maxHrIsTheRides = maxHrIsTheRides,
                maxHrSource = maxHrSource,
                detailSec = detailSec,
                zoneFtpWatts = stored?.ftpWatts,
                zoneMaxHrBpm = stored?.maxHrBpm,
                timeInZone = stored?.timeInZone() ?: TimeInZone(),
                timeInHeartRateZone =
                    stored?.heartRateTimeInZone() ?: TimeInHeartRateZone(),
                cadence = stored?.cadence() ?: CadenceDistribution(),
                integrity = integrity
            )
        }

        // A count of seconds, so a trimmed ride reads what it wrote down rather
        // than recounting the fifth of its rows that survived — the same rule as
        // the cadence spread and time in zone, and the reason
        // `RideDistributions` carries the maximum it counted against.
        val heartZones = stored?.heartRateTimeInZone()
            ?: heartRateTimeInZone(ordered, maxHrBpm)

        val prescribed = prescribedPlan(
            samples = ordered,
            intervals = intervals,
            ftpWatts = ftpWatts,
            intentMultiplier = intentMultiplier,
            // A trimmed ride keeps the blocks and loses the compliance: the
            // bands come from the class and are as true as they ever were,
            // while "inside the target for 14 of 20 minutes" is a count of
            // seconds that are no longer there (23.4.3).
            countSeconds = detailSec <= 1
        )

        return RideCharts(
            power = downsample(ordered, buckets) { it.powerWatts },
            heartRate = downsampleNullable(ordered, buckets) { it.heartRateBpm?.toDouble() },
            cadenceTrace = downsample(ordered, buckets) { it.cadenceRpm },
            cadence = stored?.cadence() ?: cadenceDistribution(ordered),
            timeInZone = stored?.timeInZone() ?: timeInZone(ordered, ftpWatts),
            timeInHeartRateZone = heartZones,
            prescribed = prescribed,
            // 21.6.3. Survives a trim on purpose: it reads the blocks the class
            // prescribed, which are still true, and the heart's own stored
            // counts — never the compliance the trim withdrew above.
            effortAgainstPlan = EffortAgainstPlan.of(prescribed, heartZones),
            detailSec = detailSec,
            zoneFtpWatts = stored?.ftpWatts,
            zoneMaxHrBpm = stored?.maxHrBpm,
            ftpWatts = ftpWatts,
            ftpIsTheRides = ftpIsTheRides,
            maxHrBpm = maxHrBpm,
            maxHrIsTheRides = maxHrIsTheRides,
            maxHrSource = maxHrSource,
            powerProvenance = powerProvenance,
            integrity = integrity
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
            //
            // 19.1.2c: the threshold used to be a private 20 rpm of this
            // function's own, which made three answers in this app to the
            // question *was the rider pedalling* — the pause's, the averages'
            // and this one. It is `AutoPausePolicy`'s now. The measurement that
            // says the change costs nothing is that a stop produces **no
            // samples at all between 1 and 19 rpm**: 21 zeros and 168 seconds
            // above 20 on the ride this was checked against, which is the
            // board reporting a true 0 the moment the cranks stop rather than a
            // flywheel coasting down through the low bands.
            .filter { it.cadenceRpm >= AutoPausePolicy.PEDALLING_RPM }
            .groupingBy { (it.cadenceRpm / CADENCE_BAND_RPM).toInt() }
            .eachCount()

        return CadenceDistribution(secondsByBand = bands)
    }

    /**
     * The zones divide the seconds that were *ridden* (19.1.2c).
     *
     * The stopped seconds are counted rather than dropped, so the card can say
     * what its percentages are out of — the same shape [heartRateTimeInZone]
     * already uses for a strap that missed part of the ride, and the same
     * threshold `AutoPausePolicy` stops the clock on.
     */
    private fun timeInZone(samples: List<ChartSample>, ftpWatts: Int): TimeInZone {
        if (ftpWatts <= 0) return TimeInZone()

        val (ridden, stopped) = samples.partition {
            it.cadenceRpm >= AutoPausePolicy.PEDALLING_RPM
        }
        val byZone = ridden
            .groupingBy { PowerZone.forPower(it.powerWatts, ftpWatts.toDouble()) }
            .eachCount()

        return TimeInZone(secondsByZone = byZone, secondsStopped = stopped.size)
    }

    /**
     * The same count for the heart (21.4.1), with the one difference that makes
     * it a separate function rather than a generic one.
     *
     * A sample with no heart rate in it is **not** a second in H1 — it is a
     * second the app knows nothing about, and it is counted as such. This is the
     * third time this project has had to say that a missing heart rate is not a
     * zero (`heartRateBpm` nullable, 21.2.4), and it is the first time it would
     * have been a *percentage* that was wrong rather than a number.
     *
     * No maximum means no zones at all, never a default.
     */
    private fun heartRateTimeInZone(
        samples: List<ChartSample>,
        maxHrBpm: Int?
    ): TimeInHeartRateZone {
        if (maxHrBpm == null || maxHrBpm <= 0) return TimeInHeartRateZone()

        val byZone = samples
            .groupingBy { HeartRateZone.forHeartRate(it.heartRateBpm, maxHrBpm) }
            .eachCount()

        return TimeInHeartRateZone(
            secondsByZone = byZone.mapNotNull { (zone, seconds) ->
                zone?.let { it to seconds }
            }.toMap(),
            secondsUnrecorded = byZone[null] ?: 0
        )
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
        intentMultiplier: Double,
        /** False for a trimmed ride, whose seconds cannot be counted (23.4.3). */
        countSeconds: Boolean = true
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
                val ridden = if (countSeconds) {
                    samples.filter { it.timestampSec in interval.startSec until end }
                } else {
                    emptyList()
                }

                PrescribedSegment(
                    startSec = interval.startSec,
                    endSec = end,
                    zone = interval.powerZone,
                    targetLowWatts = low,
                    targetHighWatts = high,
                    targetCadenceLow = interval.cadenceMin,
                    targetCadenceHigh = interval.cadenceMax,
                    secondsRidden = ridden.size,
                    secondsInBand = ridden.count { it.powerWatts in low..high },
                    // A coast is outside the target, and counted as such: the
                    // rider was asked to turn the pedals at 85 and was not.
                    secondsInCadenceBand = ridden.count {
                        it.cadenceRpm >= interval.cadenceMin && it.cadenceRpm <= interval.cadenceMax
                    }
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
}

/**
 * A sentence saying what each chart shows (16.2.4).
 *
 * A chart is unreadable to a screen reader, and a fair amount of this data is
 * a sentence anyway — "you spent most of it in Endurance and touched Threshold
 * twice" is arguably the more useful form for everyone.
 */
object RideChartSummaries {

    fun power(trace: RideTrace, provenance: PowerProvenance, detailSec: Int = 1): String {
        if (trace.isEmpty) return "No power was recorded for this ride."
        val source = when (provenance) {
            PowerProvenance.Measured -> "measured"
            PowerProvenance.Mixed -> "partly measured"
            else -> "estimated"
        }
        val shape = "Power over ${formatDuration(trace.durationSec)}, $source. " +
            "Peak ${trace.maxValue.roundToInt()} watts"

        // The peak survives a trim exactly — `MetricTrim` keeps each bucket's
        // highest second — and the average does not: what is left is the highs
        // and the lows of every bucket and nothing in between, so a mean of them
        // is not this ride's average power. The ride's own average is on the row
        // above, computed live (`avg_power`), and it is the honest one to read.
        if (detailSec > 1) {
            return "$shape. Kept as a $detailSec-second outline, so the seconds " +
                "between those points are gone."
        }

        // 19.1.2b: this sentence used to end with a second average, taken over
        // every bucket in the trace. `avg_power` is now the mean of the seconds
        // the rider was *pedalling*, so on any ride with a stop in it the two
        // disagreed — one screen, two numbers, both called "average". The
        // figures row above already carries the ride's own, so the caption says
        // nothing rather than a rival version of it.
        return "$shape."
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

    /**
     * Cadence on the clock, and what it was asked to be (16.1.5a).
     *
     * The peak is stated because the chart's own scale is rounded, and a rider
     * who put in a 130 rpm effort wants the number rather than "somewhere above
     * 125". The compliance half is silent for a free ride, exactly as
     * [prescribed] is: a class that asked for nothing was not disobeyed.
     */
    fun cadenceOverTime(trace: RideTrace, plan: PrescribedPlan): String {
        if (trace.isEmpty) return "No cadence was recorded for this ride."

        val shape = "Cadence over ${formatDuration(trace.durationSec)}, " +
            "peaking at ${trace.maxValue.roundToInt()} rpm."

        if (plan.isEmpty || plan.secondsRidden == 0) return shape

        val percent = (plan.fractionInCadenceBand * 100).roundToInt()
        return "$shape Inside the class's target cadence for " +
            "${formatDuration(plan.secondsInCadenceBand)} of " +
            "${formatDuration(plan.secondsRidden)} prescribed — $percent%."
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
            // 19.1.2c: a ride can now reach zero pedalling seconds while still
            // having recorded some, which is a different fact from having no
            // FTP to divide by and is said as one.
            if (timeInZone.secondsStopped > 0) {
                return "Nothing was pedalled in this ride's " +
                    "${formatDuration(timeInZone.secondsStopped)} of recording."
            }
            return "Time in zone needs an FTP, and this ride has none."
        }
        val zones = timeInZone.occupied.joinToString(
            prefix = "Time in zone: ",
            postfix = "."
        ) { (zone, seconds) ->
            "${zone.displayName} ${formatDuration(seconds)}"
        }
        // The heart's sentence's twin (21.4.1), for the same reason: five zones
        // adding to 100% read as the shape of the ride, so when some of the
        // recording was not ridden the card says what the percentages are out
        // of rather than leaving it to be assumed.
        if (!timeInZone.isPartial) return zones
        return "$zones You were pedalling for " +
            "${formatDuration(timeInZone.totalSeconds)} of " +
            "${formatDuration(timeInZone.recordedSeconds)}."
    }

    /**
     * The heart's version, and it says the coverage rather than implying it
     * (21.4.1).
     *
     * "H2 Aerobic 18 minutes" out of a 40-minute ride the strap only heard half
     * of is a true sentence that reads as a false one, so the time a heart rate
     * was actually reported is stated whenever it is not the whole ride.
     */
    fun timeInHeartRateZone(timeInZone: TimeInHeartRateZone): String {
        if (timeInZone.totalSeconds == 0) {
            return "Time in heart-rate zone needs a maximum heart rate and a " +
                "strap, and this ride has neither."
        }
        // The full stop is load-bearing: 21.6.3's sentence is appended to this
        // one on the card, and without it the zone list runs straight into
        // *"About what the class asked"* as a single sentence.
        val zones = timeInZone.occupied.joinToString(
            prefix = "Time in heart-rate zone: ",
            postfix = "."
        ) { (zone, seconds) ->
            "${zone.displayName} ${formatDuration(seconds)}"
        }
        if (!timeInZone.isPartial) return zones
        return "$zones A heart rate was recorded for " +
            "${formatDuration(timeInZone.totalSeconds)} of " +
            "${formatDuration(timeInZone.recordedSeconds)}."
    }

    /**
     * What the class asked for against what the heart did (21.6.3).
     *
     * Empty whenever [EffortAgainstPlan] found nothing honest to compare, which
     * is most rides — and the empty string rather than a hedge, because a
     * sentence saying the app cannot tell is a sentence about the app.
     *
     * The wording keeps the two scales apart deliberately. The heart's side
     * says *"your top two heart-rate zones"*, which is that scale's own
     * language, and the class's side says what it prescribed; nothing here
     * claims H4 and Z4 are the same zone, because they are not. And the verdict
     * is about the **ride**: *"harder than the class asked"* is an observation
     * about a session, where "you found this hard" would be a claim about a
     * body that only the rider can make (21.6.1).
     */
    fun effortAgainstPlan(effort: EffortAgainstPlan?): String {
        if (effort == null) return ""

        val opening = when (effort.verdict) {
            EffortAgainstPlan.Verdict.Harder -> "Harder than the class asked"
            EffortAgainstPlan.Verdict.Easier -> "Easier than the class asked"
            EffortAgainstPlan.Verdict.AsAsked -> "About what the class asked"
        }
        val heart = if (effort.heartHardSeconds == 0) {
            "no time in your top two heart-rate zones"
        } else {
            "${formatDuration(effort.heartHardSeconds)} in your top two heart-rate zones"
        }
        val prescribed = if (effort.prescribedHardSeconds == 0) {
            "on a class that prescribed no hard riding at all"
        } else {
            "against the ${formatDuration(effort.prescribedHardSeconds)} it prescribed"
        }

        return "$opening — $heart, $prescribed."
    }

    /**
     * Spoken out, because every one of these strings is read aloud by a screen
     * reader as well as printed (16.2.4) — and *"1 minutes 42 seconds"* is
     * wrong in both. The plural was hard-coded until the forty-eighth sitting,
     * where it turned up in the middle of a new sentence and was visible on a
     * card that had been drawing it for four.
     */
    private fun formatDuration(totalSeconds: Int): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return when {
            minutes == 0 -> plural(seconds, "second")
            seconds == 0 -> plural(minutes, "minute")
            else -> "${plural(minutes, "minute")} ${plural(seconds, "second")}"
        }
    }

    // See `Formatters.plural` (22.9.4) — one answer, three former copies.
    private fun plural(count: Int, unit: String): String = Formatters.plural(count, unit)
}

internal const val CADENCE_BAND_RPM = 10
