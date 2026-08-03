package com.pelonot.data.local.dao

import androidx.room.ColumnInfo

/**
 * One ride, reduced to what a volume trend needs (PLAN 16.3.2).
 *
 * The same argument as [WorkoutListItem]: a projection rather than the whole
 * entity, and never a join onto `workout_metrics`. It maps straight to
 * `RideRecord` in `domain/progress`, which is where the date arithmetic lives —
 * nothing about weeks or days is done in SQL, because "the week the rider
 * considers this to be" is locale and timezone work that cannot be tested where
 * it would sit.
 */
data class RideRecordRow(
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "duration_sec") val durationSec: Int,
    @ColumnInfo(name = "total_output_kj") val totalOutputKj: Double
)
