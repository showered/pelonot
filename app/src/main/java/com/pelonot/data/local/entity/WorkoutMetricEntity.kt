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
    val heartRate: Int? = null,

    /**
     * Where this second's watts came from: the bike's own board, or the model.
     *
     * Recorded per sample rather than per ride because a ride can genuinely be
     * both — the board can drop out and come back — and because a per-ride flag
     * would be a derived number stored beside the thing it was derived from,
     * which is the trap `avg_hr` and 7.8 are both examples of. The per-ride
     * verdict is [com.pelonot.domain.model.PowerProvenance], computed from
     * these.
     *
     * **Null means unknown, and unknown is not "modelled".** Every sample
     * recorded before this column existed has no answer, and inventing one
     * would be claiming a fact about a rider's record. The consumers that
     * matter — the FTP proposal (7.10.7) and the household leaderboard
     * (24.4.2) — treat unknown as *not proven measured*, which is the only
     * safe direction when the rule is that a modelled watt is never presented
     * as a measured one.
     */
    @ColumnInfo(name = "power_is_measured")
    val powerIsMeasured: Boolean? = null
)