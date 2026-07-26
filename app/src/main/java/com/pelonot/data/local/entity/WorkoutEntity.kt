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

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis()
)