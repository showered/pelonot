package com.pelonot.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Which rival a ride in progress is racing, for the live ghost (PLAN
 * 24.3.3–24.3.9).
 *
 * A small table of its own rather than a column on `workouts`, because
 * 24.3.9's rule about the ghost applies here too: `stopWorkout` finalises a
 * ride by building a **fresh** `WorkoutEntity` out of `WorkoutSession`
 * (8.3d.4), so a column added to `workouts` for this would be silently
 * dropped the moment the ride ends — and this choice has to survive exactly
 * the opposite event, a crash *before* the ride ends (24.3.8). `WorkoutSession`
 * itself does not survive process death either, being an in-memory
 * `StateFlow` — this table is what `beginResumedRide` reads back from.
 *
 * Both foreign keys are `CASCADE`: a ride racing a rival that later gets
 * deleted has nothing left to compare against, and a ride that is itself
 * deleted has no comparison to keep.
 */
@Entity(
    tableName = "active_ride_rival",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workout_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["rival_workout_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("rival_workout_id")]
)
data class ActiveRideRivalEntity(
    @PrimaryKey
    @ColumnInfo(name = "workout_id")
    val workoutId: String,

    @ColumnInfo(name = "rival_workout_id")
    val rivalWorkoutId: String
)
