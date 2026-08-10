package com.pelonot.data.local.dao

import androidx.room.ColumnInfo

/**
 * The dashboard's last-ride row (PLAN 22.1.5) — see
 * [WorkoutDao.observeLastRide].
 *
 * Six columns off `workouts` and one off `class_templates`. Deliberately not
 * the whole `WorkoutEntity` the dashboard used to carry: the card says what the
 * ride was, when, and how long, and a screen given thirty columns finds uses
 * for them — which is how *"Recent Ride 73 kJ"* got onto a screen whose
 * question is *should I ride today* (22.1.1).
 */
data class LastRideRow(
    @ColumnInfo(name = "id") val id: String,

    /** Null for a Just Ride. The comparison in 22.1.5 needs the id, not the title. */
    @ColumnInfo(name = "class_id") val classId: String?,

    /** Null for a Just Ride, or for a class the library has since dropped. */
    @ColumnInfo(name = "class_title") val classTitle: String?,

    @ColumnInfo(name = "duration_sec") val durationSec: Int,
    @ColumnInfo(name = "total_output_kj") val totalOutputKj: Double,
    @ColumnInfo(name = "timestamp") val timestamp: Long
)
