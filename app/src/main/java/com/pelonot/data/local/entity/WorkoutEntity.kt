package com.pelonot.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workouts",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["local_user_id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = ClassTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["class_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("user_id"),
        Index("class_id"),
        Index("timestamp")
    ]
)
data class WorkoutEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "user_id")
    val userId: Int? = null,

    @ColumnInfo(name = "class_id")
    val classId: String? = null,

    @ColumnInfo(name = "duration_sec")
    val durationSec: Int,

    @ColumnInfo(name = "total_output_kj")
    val totalOutputKj: Double,

    @ColumnInfo(name = "total_distance_km")
    val totalDistanceKm: Double = 0.0,

    @ColumnInfo(name = "avg_cadence")
    val avgCadence: Double? = null,

    @ColumnInfo(name = "avg_power")
    val avgPower: Double? = null,

    @ColumnInfo(name = "avg_hr")
    val avgHr: Double? = null,

    @ColumnInfo(name = "intent_modifier")
    val intentModifier: Double = 1.0,

    @ColumnInfo(name = "rpe_rating")
    val rpeRating: Int? = null,

    /**
     * False while a ride is in progress, true once it has been finalised.
     *
     * The row is inserted when the workout *starts*, not when it ends, because
     * `workout_metrics` has a foreign key onto this table: writing a metric
     * every second against a parent row that did not exist yet raised a
     * constraint violation and killed the recording coroutine, so no ride ever
     * captured a time series.
     *
     * It doubles as the crash-recovery marker. The previous
     * `getIncompleteWorkout()` simply returned the most recent workout, so on
     * every launch the app offered to resume the ride you had just finished.
     */
    @ColumnInfo(name = "is_complete")
    val isComplete: Boolean = false,

    /**
     * True when this ride's totals were rebuilt from its stored samples after
     * the app was killed mid-workout, rather than finalised by the service.
     *
     * A recovered ride is missing however much of itself the rider went on
     * pedalling after the process died, and its aggregates come from
     * `WorkoutAggregates` rather than the live calculator. History says so
     * rather than presenting a truncated ride as a complete one.
     */
    @ColumnInfo(name = "was_recovered")
    val wasRecovered: Boolean = false,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis()
)