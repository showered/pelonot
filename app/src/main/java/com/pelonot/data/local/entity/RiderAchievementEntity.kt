package com.pelonot.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A permanent fact a rider earned (PLAN 28.1.1).
 *
 * This is intentionally not `rider_alerts`: alerts are timely observations and
 * may be cleared when a ride resumes; an achievement is a possession. The
 * source ride may disappear, but the achievement remains and keeps its date.
 */
@Entity(
    tableName = "rider_achievements",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["local_user_id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workout_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("user_id"),
        Index("workout_id"),
        Index(value = ["user_id", "achievement_id"], unique = true)
    ]
)
data class RiderAchievementEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "user_id")
    val userId: Int,
    @ColumnInfo(name = "workout_id")
    val workoutId: String?,
    /** Stable [com.pelonot.domain.achievements.AchievementDefinition.id]. */
    @ColumnInfo(name = "achievement_id")
    val achievementId: String,
    @ColumnInfo(name = "earned_at")
    val earnedAt: Long,
    /** Back-filled achievements begin seen; the next newly earned one is news. */
    @ColumnInfo(name = "seen_at")
    val seenAt: Long? = null
)
