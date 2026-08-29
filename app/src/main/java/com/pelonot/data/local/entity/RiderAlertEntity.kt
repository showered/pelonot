package com.pelonot.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One thing the app noticed about a rider, written down at the moment it
 * happened (PLAN 27.1.1).
 *
 * **This table is what makes an alert an alert.** Every other version of these
 * numbers in the app — the mean-maximal curve, the household board, the streak
 * on the dashboard — is recomputed from the whole history on every load, which
 * is exactly right for a chart and useless here: an alert is a claim about a
 * *change*, and a number recomputed from scratch cannot tell you it moved.
 * Without a row there is no way to say "new", no way to stop the same
 * congratulation reappearing on every load, and nothing for 27.4's list to
 * read.
 *
 * **Two foreign keys, deliberately different.** The profile cascades, because
 * an alert about a rider who no longer exists is not about anybody. The ride
 * sets null, because **an alert is never revoked** — the rider did it, and
 * deleting the ride afterwards does not undo the week they rode. That is the
 * same instinct as 28.1.1 one phase early, and it is why [workoutId] is
 * nullable.
 *
 * The unique index across the rider, the family, the subject and the ride is
 * the "never twice" of 27.1.1: a ride finalised a second time — the crash
 * recovery's path, or 12.6.2's resume — re-runs the detector and must not
 * write the same congratulation again.
 */
@Entity(
    tableName = "rider_alerts",
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
        Index(
            value = ["user_id", "kind", "subject_key", "workout_id"],
            unique = true
        )
    ]
)
data class RiderAlertEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "user_id")
    val userId: Int,

    /**
     * The ride that caused it, or null once that ride has been deleted.
     *
     * A guest ride never gets here at all: its rides are filed against nobody
     * (`RiderScore`'s fourth rule, and the same reason), so there is no rider
     * for a record to belong to.
     */
    @ColumnInfo(name = "workout_id")
    val workoutId: String?,

    /** `AlertKind.name`. Stored as text so an unknown one can be skipped. */
    @ColumnInfo(name = "kind")
    val kind: String,

    /** `RiderAlert.subjectKey` — a class id, a window in seconds, or empty. */
    @ColumnInfo(name = "subject_key")
    val subjectKey: String,

    /**
     * The class's title as it read on the night, for `AlertKind.ClassOutput`.
     *
     * Copied rather than joined, because a class the bundle has since retired
     * (23.2.6) still has to be nameable in a rider's own list.
     */
    @ColumnInfo(name = "subject_title")
    val subjectTitle: String? = null,

    /** The absolute the rider produced: watts, kilojoules, seconds or weeks. */
    @ColumnInfo(name = "value_num")
    val value: Double,

    /** What it beat, or null for a threshold crossing. */
    @ColumnInfo(name = "previous_num")
    val previousValue: Double? = null,

    @ColumnInfo(name = "recorded_at")
    val recordedAt: Long,

    /**
     * When the rider was actually shown it, or null.
     *
     * 27.1.5 tells them one thing per ride and files the rest; this column is
     * what tells those two apart afterwards, so the list can mark what is new
     * without re-deciding what was worth saying.
     */
    @ColumnInfo(name = "seen_at")
    val seenAt: Long? = null
)
