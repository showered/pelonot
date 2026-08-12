package com.pelonot.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * One mean-maximal effort a ride actually held, computed once when the ride was
 * recorded (PLAN 16.3.3a).
 *
 * **A derived number, stored, and the reason is not speed.** `personalBests`
 * used to re-walk every measured ride's samples on every visit to *Your FTP* —
 * fine at 22 rides, ~365 queries and a million rows at a year of daily riding,
 * and 16.3.3a was content to leave it there until somebody felt it. What
 * changed the argument is 23.4.8: **trimming destroys the samples a best is
 * derived from**, so a rider's five-second and twenty-minute bests would
 * silently get worse on the one screen that exists to show their training
 * working. A best that was never computed cannot be recovered from a trimmed
 * ride, so it has to be computed before there is anything to trim.
 *
 * **A row per window rather than five columns on `workouts`**, because a window
 * a ride never held must be *absent* rather than zero — a rider who has never
 * ridden for an hour has no hour effort, which is not an hour at 0 W. Absence
 * is the natural state of a missing row and the awkward state of a column.
 *
 * `ON DELETE CASCADE`: these belong to the ride and mean nothing without it.
 */
@Entity(
    tableName = "workout_power_bests",
    primaryKeys = ["workout_id", "window_sec"],
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workout_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("workout_id")]
)
data class WorkoutPowerBestEntity(
    @ColumnInfo(name = "workout_id")
    val workoutId: String,

    /** The stretch, in seconds — one of `MeanMaximalPower.WINDOWS`. */
    @ColumnInfo(name = "window_sec")
    val windowSec: Int,

    /** The best average power held over that stretch, in watts. */
    @ColumnInfo(name = "watts")
    val watts: Double
)
