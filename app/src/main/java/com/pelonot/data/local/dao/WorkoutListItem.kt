package com.pelonot.data.local.dao

import androidx.room.ColumnInfo

/**
 * One row of ride history.
 *
 * A projection rather than the whole [com.pelonot.data.local.entity.WorkoutEntity]
 * joined to its class, because the list is the one query in the app that has to
 * stay cheap as the record grows. It reads only from `workouts` and
 * `class_templates` and **never touches `workout_metrics`** — a year of daily
 * rides is 365 workout rows and roughly a million metric samples, and a list
 * that pulled the series for each row would take seconds to open and then hold
 * all of it in memory (12.1.6).
 */
data class WorkoutListItem(
    @ColumnInfo(name = "id") val id: String,

    /** Null for a Just Ride, or for a class since deleted from the library. */
    @ColumnInfo(name = "class_title") val classTitle: String?,

    @ColumnInfo(name = "duration_sec") val durationSec: Int,
    @ColumnInfo(name = "total_output_kj") val totalOutputKj: Double,
    @ColumnInfo(name = "total_distance_km") val totalDistanceKm: Double,
    @ColumnInfo(name = "avg_power") val avgPower: Double?,
    @ColumnInfo(name = "rpe_rating") val rpeRating: Int?,
    @ColumnInfo(name = "was_recovered") val wasRecovered: Boolean,
    @ColumnInfo(name = "timestamp") val timestamp: Long
) {
    val displayTitle: String get() = classTitle ?: "Just Ride"
}
