package com.pelonot.data.local.dao

import androidx.room.ColumnInfo

/**
 * What one ride can tell a window about its zones (PLAN 21.4.3).
 *
 * Deliberately every column needed to decide **which** source a ride's seconds
 * come from, and none of the seconds themselves: an intact ride is counted from
 * its samples and a condensed one from the summary written before they went
 * (23.4.2), and asking a trimmed ride's remaining rows would return a wrong
 * number rather than nothing.
 */
data class RideZoneSourceRow(
    @ColumnInfo(name = "id") val id: String,

    /** For the window's own day arithmetic, which is not done in SQL. */
    @ColumnInfo(name = "timestamp") val timestamp: Long,

    /**
     * The FTP this ride's zones divide by (7.8).
     *
     * Null for a ride recorded before that column existed, and such a ride is
     * **not counted** rather than divided by today's number — see
     * `RidingIntensity`.
     */
    @ColumnInfo(name = "ftp_watts") val ftpWatts: Int?,

    /** Null while the per-second record is intact; set once it is condensed. */
    @ColumnInfo(name = "metrics_detail_sec") val metricsDetailSec: Int?,

    /** What the trimmer counted while the seconds were still there (23.4.2). */
    @ColumnInfo(name = "distributions_json") val distributionsJson: String?
)

/**
 * A ride's seconds, gathered by whole watts rather than returned one by one.
 *
 * The one query in this feature that reads `workout_metrics`, and it is grouped
 * in SQL for a reason worth stating: a month of riding is on the order of twenty
 * thousand samples, and every one of them is about to be answered with the same
 * question — *which zone is this?* Grouped by watt it is a few hundred rows, and
 * the classification still happens in Kotlin, where [com.pelonot.domain.model.PowerZone]
 * is the only thing that knows where a zone starts.
 *
 * **The rounding to whole watts is stated rather than hidden.** A sample within
 * half a watt of a boundary can land in the neighbouring zone, which moves a
 * handful of seconds a month between two adjacent bands on a card that reports
 * whole percentages. Writing the Coggan boundaries into the SQL to avoid it
 * would put this scale's definition in a second place, and that is the more
 * expensive mistake — it is the one that can silently disagree with every other
 * zone in the app.
 */
data class RidePowerSecondsRow(
    @ColumnInfo(name = "workout_id") val workoutId: String,
    @ColumnInfo(name = "watts") val watts: Int,

    /** Seconds at this power with the cranks turning (19.1.2c). */
    @ColumnInfo(name = "ridden_seconds") val riddenSeconds: Int,

    /** Seconds at this power with the cranks still, which no zone divides. */
    @ColumnInfo(name = "stopped_seconds") val stoppedSeconds: Int
)
