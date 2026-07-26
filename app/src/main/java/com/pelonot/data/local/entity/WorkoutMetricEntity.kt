package com.pelonot.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workout_metrics",
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
data class WorkoutMetricEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "workout_id")
    val workoutId: String,

    @ColumnInfo(name = "timestamp_sec")
    val timestampSec: Int,

    @ColumnInfo(name = "cadence")
    val cadence: Double = 0.0,

    @ColumnInfo(name = "resistance")
    val resistance: Double = 0.0,

    @ColumnInfo(name = "power")
    val power: Double = 0.0,

    @ColumnInfo(name = "heart_rate")
    val heartRate: Int? = null
)