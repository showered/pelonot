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

    /**
     * The FTP this ride was actually judged against (PLAN 7.8).
     *
     * `profiles.ftp_watts` moves — by hand in Settings, and by itself when the
     * rider accepts an auto-FTP breakthrough. Everything that draws a past
     * ride's zones used to read that current value, so a ride ridden in Zone 5
     * in January was silently redrawn as Zone 4 in March: a record editing
     * itself behind the rider. The same family as the `avg_*` trap — a number
     * derived on read from a source that has since moved.
     *
     * **Nullable, and null means nobody wrote it down.** Every ride recorded
     * before this column existed is one, and backfilling them with the
     * profile's *current* FTP would bake today's guess permanently into the
     * record while looking exactly like real data. A reader falls back to the
     * profile and says that it is doing so (7.8.4).
     */
    @ColumnInfo(name = "ftp_watts")
    val ftpWatts: Int? = null,

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
    val timestamp: Long = System.currentTimeMillis(),

    /**
     * The rider was offered an FTP breakthrough off this ride and said no
     * (PLAN 7.10.5).
     *
     * Kept because the analyser runs on every load of the summary, so without
     * it declining lasted until the screen was closed and the rider was asked
     * again about a ride they had already answered for. Asked often enough,
     * "no" stops being a decision and becomes a thing to tap past.
     *
     * It is not the same fact as accepting: an accepted proposal is recorded in
     * `ftp_history` because the rider's FTP *changed*, and a decline changed
     * nothing about them. It is only a note that the question has been asked.
     */
    @ColumnInfo(name = "ftp_proposal_declined")
    val ftpProposalDeclined: Boolean = false,

    /**
     * When this ride was last accepted by the cloud, or null if it never has
     * been (PLAN 14.2.4).
     *
     * **The app has never known what it had not uploaded.** `WorkoutSyncWorker`
     * fires once at the end of a ride, gets three attempts, and then the
     * question is closed forever — a ride that failed while the wifi was down
     * is indistinguishable from one that succeeded, because nothing wrote
     * either fact anywhere. The only record of an upload was a `Log.i` line on
     * a tablet whose `log.tag` is `W`.
     *
     * That is survivable while the cloud is a curiosity and unacceptable the
     * moment it is a *backup*. 23.3.1 already tells an offline rider that ten
     * rides have gone by unprotected; a signed-in rider whose uploads have been
     * failing silently for a month is in a worse position, because they think
     * they are covered.
     *
     * **Null, not a boolean.** Three reasons, in order of how much they matter:
     * a backlog needs to be *ordered* to be drained oldest-first (14.2.5); "how
     * stale is my backup?" is a question a rider will ask and a flag cannot
     * answer; and the payload format is versioned inside itself (14.4.3), so a
     * future reader that needs to know which rides went up under the old shape
     * has a date to compare against. Absent means never, exactly as it does for
     * `heartRateBpm` and `ftpWatts` — it is not a zero and not a false.
     *
     * It is deliberately **not** cleared when a ride is edited. Today nothing
     * about a ride changes after it ends except `rpeRating` and the FTP
     * proposal flag, and neither is in the payload. When something in the
     * payload does become editable, that edit has to null this column or the
     * cloud keeps a copy the rider has since corrected — see 14.2.4a.
     */
    @ColumnInfo(name = "synced_at")
    val syncedAt: Long? = null,

    /**
     * How many times this ride was interrupted and picked up again (8.3d.2).
     *
     * **The series cannot show this, which is the whole reason it is a
     * column.** A resumed ride continues recording at the second it left off
     * (8.3d.1), so `workout_metrics` comes back contiguous and a reader
     * afterwards sees an unbroken hour. A 45-minute ride actually ridden across
     * two hours with a crash in the middle is a different ride from one ridden
     * straight through, whatever the totals say.
     *
     * [wasRecovered] will not do the job: it means *rebuilt from its samples
     * after a crash*, which is the **keep** path, and the two are different
     * claims about what happened — the same argument that keeps
     * `power_is_measured` nullable and `target_position` absent.
     *
     * `NOT NULL DEFAULT 0` rather than nullable, and unlike [syncedAt] the
     * backfill here is *true*: every ride that predates this column was never
     * resumed, because resuming did not exist. A default that states a fact is
     * not the false reassurance 9→10 was avoiding.
     */
    @ColumnInfo(name = "resume_count")
    val resumeCount: Int = 0,

    /**
     * Total wall-clock seconds this ride spent not being ridden (8.3d.2).
     *
     * Accumulated across every interruption, from `RideInterruption`. It is
     * time that produced no samples rather than specifically time the app was
     * dead — see that class for why the broader quantity is the honest one.
     *
     * Kept beside [resumeCount] rather than derived from it because they answer
     * different questions: *was this ride continuous?* and *how much of it is
     * missing?* One resume after ten seconds and one after twenty minutes are
     * the same count and very different rides.
     */
    @ColumnInfo(name = "interrupted_sec")
    val interruptedSec: Int = 0
)