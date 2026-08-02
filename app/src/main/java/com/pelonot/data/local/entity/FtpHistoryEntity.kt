package com.pelonot.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Why a rider's FTP moved (PLAN 7.9.2).
 *
 * A typed enum rather than a display string, and the precedent is 5.8:
 * `RideIntent` was a bare string until a typo silently defeated the whole
 * feature. This one carries a distinction that shows on the chart —
 * **an FTP the rider typed is a claim; one the app measured is evidence** — so
 * a trend line drawn from both has to be able to tell them apart.
 *
 * [Unknown] is not a defensive branch. It is the honest answer for every value
 * that already existed when the history table did not, and for any future write
 * path that changes the number without saying why — which is precisely the case
 * a history table must be able to record rather than lose.
 */
enum class FtpChangeSource {
    /** The number given when the profile was made. */
    ProfileCreated,

    /** Typed into Settings. A claim about the rider, made by the rider. */
    ManualEdit,

    /** A twenty-minute peak the app measured and the rider accepted (7.1–7.3). */
    AutoBreakthrough,

    /** A ramp or 20-minute test the app ran on purpose (19.2.3). */
    GuidedTest,

    /** Arrived from another device (15.3). */
    PulledFromCloud,

    /** Nobody wrote it down. */
    Unknown;

    companion object {
        fun fromName(name: String?): FtpChangeSource =
            entries.firstOrNull { it.name == name } ?: Unknown
    }
}

/**
 * One row per change to a rider's FTP (PLAN 7.9).
 *
 * FTP is the only number in this app that is a *fitness* measure rather than a
 * volume measure, and the app recomputes it for free after every ride — then
 * overwrote the previous value and lost it. A derived history is not available
 * afterwards, so this has to be recorded at the moment of the change or it does
 * not exist. Two features already assume it does: 16.3.1's trend line and
 * 22.1.4's dashboard.
 *
 * [workoutId] is `ON DELETE SET NULL` on purpose (7.9.3). Deleting a ride must
 * not delete the fact that the rider's FTP changed — the training history is
 * not the ride's to take with it. The profile reference is `CASCADE` for the
 * opposite reason: unlike a ride, which is a record of something that happened,
 * an FTP history means nothing once there is no rider it is about.
 */
@Entity(
    tableName = "ftp_history",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["local_user_id"],
            childColumns = ["local_user_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workout_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("local_user_id"), Index("workout_id"), Index("changed_at")]
)
data class FtpHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "local_user_id")
    val localUserId: Int,

    @ColumnInfo(name = "ftp_watts")
    val ftpWatts: Int,

    @ColumnInfo(name = "changed_at")
    val changedAt: Long,

    /** Stored by name, read back through [FtpChangeSource.fromName]. */
    @ColumnInfo(name = "source")
    val source: String,

    /** The ride that caused it, where there was one. */
    @ColumnInfo(name = "workout_id")
    val workoutId: String? = null
)
