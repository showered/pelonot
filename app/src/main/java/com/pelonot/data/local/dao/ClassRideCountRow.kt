package com.pelonot.data.local.dao

import androidx.room.ColumnInfo

/**
 * One class this rider has ridden, counted (PLAN 22.8.6) — see
 * [WorkoutDao.observeClassRideCounts].
 */
data class ClassRideCountRow(
    @ColumnInfo(name = "class_id") val classId: String,
    @ColumnInfo(name = "rides") val rides: Int,
    @ColumnInfo(name = "last_ridden_at") val lastRiddenAtMs: Long
)

/**
 * One of the rider's recent rides, reduced to what choosing the next one needs
 * (PLAN 22.8.6) — see [WorkoutDao.observeRecentRides].
 */
data class RecentRideRow(
    /** Null for a Just Ride, which still says how long this rider rides. */
    @ColumnInfo(name = "class_id") val classId: String?,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "duration_sec") val durationSec: Int
)
