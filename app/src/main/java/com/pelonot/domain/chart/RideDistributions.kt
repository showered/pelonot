package com.pelonot.domain.chart

import com.pelonot.domain.model.HeartRateZone
import com.pelonot.domain.model.PowerZone
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * How a ride's seconds were spent, written down before they were thrown away
 * (PLAN 23.4.2, 23.4.3).
 *
 * Time in zone and the cadence spread are the two charts that are **counts of
 * seconds** rather than lines through them, and that is exactly what a trim
 * destroys: a ride reduced to a ten-second outline has a fifth of its rows left,
 * so recomputing either one afterwards would draw a 45-minute ride that spent
 * nine minutes pedalling. A wrong total is worse than a coarse line, because
 * nothing about it looks coarse.
 *
 * So it is the same fix as `workouts.ftp_watts` (7.8), `max_hr_bpm` (21.2.3),
 * `power_bests_at` (16.3.3a) and `power_provenance` (23.4.12), for the fifth
 * time: **the derived number recorded at the one moment it is knowable**, which
 * is while the samples are still there. Written by the trimmer and nobody else —
 * an untrimmed ride has none of this and does not need it, because its own
 * seconds are still on disk and are a better answer than any summary of them.
 *
 * Stored as JSON in one column rather than two tables, because nothing ever
 * queries *into* it: it is read whole, by the one screen that draws the ride.
 * The zone keys are `PowerZone.name` and a name this build no longer knows is
 * dropped on the way in, for `Converters`' reason — a rider's chart must not
 * fail to draw because an enum was renamed.
 */
@Serializable
data class RideDistributions(
    @SerialName("seconds_by_zone")
    val secondsByZone: Map<String, Int> = emptyMap(),
    @SerialName("seconds_by_cadence_band")
    val secondsByCadenceBand: Map<Int, Int> = emptyMap(),
    /**
     * The FTP the zones above were counted against.
     *
     * `workouts.ftp_watts` is the same number today and can differ tomorrow —
     * a ride recorded before 7.8's column existed has none, and falls back to
     * the rider's current FTP, which moves. Kept here so the stored counts
     * carry their own denominator rather than being a set of zone names whose
     * meaning depends on a row somewhere else.
     */
    @SerialName("ftp_watts")
    val ftpWatts: Int = 0,
    /**
     * The heart's time in zone (21.4.1), counted while the seconds were there.
     *
     * Absent from a blob written before this existed, and that is read as *this
     * ride's heart-rate zones were never counted* rather than as zero — a ride
     * trimmed by an earlier build draws no heart-rate zone chart, which is
     * honest, and is the same answer a ride with no strap gets.
     */
    @SerialName("seconds_by_hr_zone")
    val secondsByHrZone: Map<String, Int> = emptyMap(),
    /** Recorded seconds with no heart rate in them. See [TimeInHeartRateZone]. */
    @SerialName("seconds_without_hr")
    val secondsWithoutHr: Int = 0,
    /**
     * Recorded seconds with the cranks still (19.1.2c).
     *
     * Absent from a blob written before this existed, where it reads as zero —
     * and unlike [secondsByHrZone] there is no empty map beside it to tell the
     * two apart, so an old trimmed ride with a bottle stop in it will show its
     * stopped seconds still counted under Z1 and say nothing about it. That is
     * the one case this change cannot reach and it is deliberately not guessed
     * at: **an untrimmed ride recomputes from its own samples every time**, so
     * every ride on a tablet today corrects itself the moment this build runs.
     * Only a ride already condensed keeps the old counting.
     */
    @SerialName("seconds_stopped")
    val secondsStopped: Int = 0,
    /**
     * The maximum heart rate the zones above were counted against.
     *
     * [ftpWatts]' twin, for [ftpWatts]' reason: `workouts.max_hr_bpm` is the
     * same number today and a ride recorded before that column existed (21.2.3)
     * has none, so it falls back to the rider's current maximum — which moves.
     * Kept here so the counts carry their own denominator.
     */
    @SerialName("max_hr_bpm")
    val maxHrBpm: Int = 0
) {
    fun timeInZone(): TimeInZone = TimeInZone(
        secondsByZone = secondsByZone.mapNotNull { (name, seconds) ->
            PowerZone.entries.firstOrNull { it.name == name }?.let { it to seconds }
        }.toMap(),
        secondsStopped = secondsStopped
    )

    fun heartRateTimeInZone(): TimeInHeartRateZone = TimeInHeartRateZone(
        secondsByZone = secondsByHrZone.mapNotNull { (name, seconds) ->
            HeartRateZone.entries.firstOrNull { it.name == name }?.let { it to seconds }
        }.toMap(),
        secondsUnrecorded = secondsWithoutHr
    )

    fun cadence(): CadenceDistribution =
        CadenceDistribution(secondsByBand = secondsByCadenceBand)

    fun encode(): String = JSON.encodeToString(serializer(), this)

    companion object {
        /** What a ride's own charts said, on the way to losing the samples. */
        fun of(charts: RideCharts): RideDistributions = RideDistributions(
            secondsByZone = charts.timeInZone.secondsByZone
                .map { (zone, seconds) -> zone.name to seconds }
                .toMap(),
            secondsByCadenceBand = charts.cadence.secondsByBand,
            ftpWatts = charts.ftpWatts,
            secondsByHrZone = charts.timeInHeartRateZone.secondsByZone
                .map { (zone, seconds) -> zone.name to seconds }
                .toMap(),
            secondsWithoutHr = charts.timeInHeartRateZone.secondsUnrecorded,
            secondsStopped = charts.timeInZone.secondsStopped,
            // Zero when the ride had no maximum to count against, in which case
            // the map above is empty too and there is nothing to be a
            // denominator for.
            maxHrBpm = charts.maxHrBpm ?: 0
        )

        /**
         * Null for a ride that has never been trimmed, and null for a blob this
         * build cannot read — a stored summary that fails to decode must leave
         * the charts to the samples rather than take the screen down.
         */
        fun decode(json: String?): RideDistributions? {
            if (json.isNullOrBlank()) return null
            return runCatching { JSON.decodeFromString(serializer(), json) }.getOrNull()
        }

        private val JSON = Json { ignoreUnknownKeys = true }
    }
}
