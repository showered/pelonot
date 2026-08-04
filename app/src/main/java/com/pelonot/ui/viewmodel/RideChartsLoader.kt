package com.pelonot.ui.viewmodel

import com.pelonot.data.local.entity.UserEntity
import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.data.local.entity.WorkoutMetricEntity
import com.pelonot.domain.chart.ChartSample
import com.pelonot.domain.chart.RideChartBuilder
import com.pelonot.domain.chart.RideCharts
import com.pelonot.domain.model.Interval
import com.pelonot.domain.model.PowerProvenance

/**
 * One ride's samples, reduced to everything the charts draw (16.1).
 *
 * **Shared by ride detail and the post-ride summary (12.6.1)**, as the section
 * that renders it is. Two copies of this would be two answers to *which FTP
 * were these zones drawn from*, which is the one question on this screen the
 * app has already got wrong once (7.8).
 *
 * The FTP used for the zone bands is **the ride's own**, falling back to the
 * rider's current one only for a ride recorded before `workouts.ftp_watts`
 * existed. That fallback is what the whole app used to do, and it is why
 * accepting one auto-FTP breakthrough silently redrew every past ride's zones;
 * [RideCharts.ftpIsTheRides] carries which of the two was used so the screen can
 * say so rather than presenting both with the same authority (7.8.4).
 *
 * The intent multiplier has no such problem — `workouts.intent_modifier` is on
 * the row, so the prescribed band is the one the rider was actually given
 * rather than a re-derivation from today's preferences.
 *
 * Pure and synchronous on purpose: the caller has already read the samples (it
 * generally needs them for something else too) and is responsible for calling
 * this off the main thread.
 */
internal fun buildRideCharts(
    workout: WorkoutEntity,
    metrics: List<WorkoutMetricEntity>,
    intervals: List<Interval>,
    /** The rider's FTP *today*, used only when the ride did not record its own. */
    riderFtp: Int?,
    /**
     * The rider's maximum heart rate *today* (21.4.2a), used only when the ride
     * did not record its own — and null when the app has none, which draws no
     * heart-rate zones rather than inventing a denominator.
     */
    riderMaxHr: Int? = null
): RideCharts = RideChartBuilder.build(
    samples = metrics.map { metric ->
        ChartSample(
            timestampSec = metric.timestampSec,
            powerWatts = metric.power,
            cadenceRpm = metric.cadence,
            // Preserved as null: 16.1.2 depends on it.
            heartRateBpm = metric.heartRate,
            // Drawn by nothing, judged by 2.7.5.
            resistancePercent = metric.resistance
        )
    },
    ftpWatts = workout.ftpWatts ?: riderFtp ?: UserEntity.DEFAULT_FTP,
    // 16.1.6: counted from the samples themselves rather than asked of the
    // ride, because the samples are where the answer lives and a ride can be
    // both.
    powerProvenance = powerProvenanceOf(metrics),
    intervals = intervals,
    intentMultiplier = workout.intentModifier,
    ftpIsTheRides = workout.ftpWatts != null,
    // 21.4.2a, and deliberately the same two-line shape as the FTP above: the
    // ride's own maximum, the rider's current one as a fallback, and a flag
    // saying which — so the screen can label a re-derivation rather than
    // presenting it as a record.
    maxHrBpm = workout.maxHrBpm ?: riderMaxHr,
    maxHrIsTheRides = workout.maxHrBpm != null
)

/**
 * Where a ride's watts came from, counted over its own samples.
 *
 * `power_is_measured` is nullable and null means *nobody wrote it down* — not
 * "modelled" — so the three counts are three different claims and only
 * `Measured` passes `isTrustworthyAsMeasured`. Shared for the same reason as
 * the builder above: two screens asking this question differently is how one
 * of them ends up presenting a modelled watt as a measured one.
 */
internal fun powerProvenanceOf(metrics: List<WorkoutMetricEntity>): PowerProvenance =
    PowerProvenance.of(
        measured = metrics.count { it.powerIsMeasured == true },
        modelled = metrics.count { it.powerIsMeasured == false },
        unknown = metrics.count { it.powerIsMeasured == null }
    )
