package com.pelonot.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pelonot.domain.model.MaxHeartRate
import com.pelonot.domain.model.PowerProvenance

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

    /**
     * The maximum heart rate this ride's zones were judged against (21.2.3).
     *
     * Exactly [ftpWatts]'s argument, for the other denominator. A rider who
     * measures a real 186 in September and replaces the estimate of 177 would
     * otherwise silently redraw every ride they did in August — the record
     * editing itself behind them, which is 7.8 and the `avg_*` trap and this,
     * three instances of one mistake.
     *
     * **Nullable, and null means nobody wrote it down.** Every ride recorded
     * before this column existed is one, and every ride by a rider who has
     * never given the app a maximum. A reader falls back to the rider's
     * maximum *today* and **says that it is doing so** (21.4.2a); it does not
     * backfill, because a backfill would bake today's number into last
     * summer's rides while looking exactly like a measurement.
     *
     * Resolved rather than copied: it is whatever `MaxHeartRate.resolve` gave
     * at the start of the ride, so a Tanaka estimate is stored the same way a
     * measured number is — and [maxHrSource] is what keeps those two apart.
     */
    @ColumnInfo(name = "max_hr_bpm")
    val maxHrBpm: Int? = null,

    /**
     * Whether [maxHrBpm] was the rider's own measured number or Tanaka's
     * estimate from their date of birth (PLAN 21.4.2c).
     *
     * **A zone drawn off a formula is a different claim from one drawn off a
     * measurement** — that is why `MaxHeartRate` has carried a `source` since
     * the day it was written (21.5.5), and why storing the number alone made
     * the claim unrecoverable the moment the ride ended. The estimate has a
     * 10–12 bpm spread, which is wider than a zone; prescribing effort from it
     * is fine and doing it silently is not.
     *
     * **Null means nobody wrote it down**, exactly as [powerProvenance] and
     * `workout_metrics.power_is_measured` do, and it is what every ride
     * recorded between 12 → 13 and 20 → 21 says. The tempting wrong fix is to
     * answer it from the rider's *current* profile: that would be right for a
     * ride with no maximum of its own — those are drawn from today's number and
     * say so — and a silent guess for every ride carrying one, which is 7.8's
     * shape. So a screen that cannot say where the maximum came from says
     * nothing about it.
     *
     * Written at the same instant as [maxHrBpm], at the *start* of the ride, so
     * `WorkoutSession` carries it: 8.3d.4's rule is that a column the session
     * does not carry is written back as its default when the ride is finalised
     * twenty minutes later.
     */
    @ColumnInfo(name = "max_hr_source")
    val maxHrSource: MaxHeartRate.Source? = null,

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
    val interruptedSec: Int = 0,

    /**
     * When this ride's samples were walked for mean-maximal power, or null if
     * they never have been (16.3.3a).
     *
     * **Only ever set for a ride whose watts the board measured**, which makes
     * this column two facts in one and deliberately so. A personal best derived
     * from `PowerModel` — RMSE 137 W — is a fiction filed as a record, so the
     * scan declines to run at all on a modelled ride; and once 23.4 starts
     * trimming, the samples that gate was computed *from* are gone, so a
     * question asked of `workout_metrics` today has no answer tomorrow. Written
     * down at the one moment it is knowable, exactly like [ftpWatts] and
     * [maxHrBpm] — a derived number whose source moves is this project's most
     * repeated mistake.
     *
     * Null therefore means one of three things and none of them is *"has no
     * bests"*: the ride is modelled, the ride predates this column, or the ride
     * has been trimmed without ever being scanned. All three are honestly
     * "not counted", which is what the rider is told.
     *
     * Written **after** the finalise rather than as part of it, like
     * [syncedAt] and [rpeRating] — `WorkoutSession` does not carry it, and
     * 8.3d.4's rule is that anything it does not carry is written back as its
     * default by the finalise. Resuming a finished ride (12.6.2) clears it for
     * the same reason it clears [syncedAt]: the ride is about to get longer.
     */
    @ColumnInfo(name = "power_bests_at")
    val powerBestsAt: Long? = null,

    /**
     * Where this ride's watts came from, written down once (PLAN 23.4.12).
     *
     * [com.pelonot.domain.model.PowerProvenance] was derived from
     * `workout_metrics` on every read, which made it a question a **trimmed**
     * ride cannot answer — and eight readers ask it (23.4.9), six of them
     * leaderboards. So a rider whose old rides had been trimmed would fall off
     * every board, out of *"best you've ridden it"* and out of the FTP proposal
     * (7.10.7), with nothing wrong on any screen: their history would just get
     * smaller. Same fix as [ftpWatts] (7.8), [maxHrBpm] (21.2.3) and
     * [powerBestsAt] (16.3.3a) — the denominator recorded at the one moment it
     * is knowable.
     *
     * **Null means nobody wrote it down**, exactly as
     * `workout_metrics.power_is_measured` does — not `Unknown`, which is a
     * claim about the samples. It is null for a ride still being ridden and for
     * the instant between a finalise and the write that follows it;
     * `WorkoutDao.backfillPowerProvenance` closes both, and it is the
     * *unfinished* rides that keep this nullable rather than the old ones.
     *
     * The four names are the same four the cloud has had since 18.5 —
     * `supabase/007_everyone_leaderboard.sql` constrains the column to them and
     * its board filters on `'Measured'` — so this is the local schema agreeing
     * with the remote one rather than a second vocabulary.
     *
     * Written **after** the finalise like [syncedAt] and [powerBestsAt], for
     * 8.3d.4's reason: `WorkoutSession` does not carry it, so a value written
     * first would be handed back as its default by the finalise. Resuming a
     * finished ride (12.6.2) clears it — the ride is about to get longer, and
     * a board dying during the extra minutes turns `Measured` into `Mixed`.
     */
    @ColumnInfo(name = "power_provenance")
    val powerProvenance: PowerProvenance? = null,

    /**
     * How many seconds one of this ride's stored samples stands for (PLAN
     * 23.4.3).
     *
     * **Null means the record is intact** — one row per second, as recorded.
     * `10` means 23.4 has trimmed it: what is left is the lowest and highest
     * watt of each ten seconds, and the seconds between them are gone for good
     * unless the rider has a backup or a cloud copy (23.4.10).
     *
     * *"This column is the whole discipline of the feature; without it, do not
     * ship it"* is 23.4.3's own sentence and it is right. A ten-second outline
     * draws a line with the same shape, the same peak and the same axis as the
     * record it replaced, and there is nothing else on the screen — or in an
     * export — that could tell a rider which of the two they are looking at.
     * Same family as [ftpWatts] and [powerProvenance]: the provenance of a
     * number, kept beside it.
     *
     * Written **after** the ride ends, by the trimmer and by nothing else, so
     * 8.3d.4's trap does not apply: `WorkoutSession` never carries it because no
     * finalise ever writes it.
     */
    @ColumnInfo(name = "metrics_detail_sec")
    val metricsDetailSec: Int? = null,

    /**
     * What this ride's seconds said, kept for after they are gone (23.4.2).
     *
     * [com.pelonot.domain.chart.RideDistributions] as JSON: time in zone and the
     * cadence spread, which are the two charts that are *counts of seconds*
     * rather than lines through them, plus the FTP the zones were counted
     * against. Recomputing either from a trimmed ride would say a 45-minute ride
     * spent nine minutes pedalling — wrong in a way that does not look wrong,
     * which is the failure mode this whole item is written around.
     *
     * Null for every ride that has not been trimmed, and that is not a gap: its
     * own samples are still there and are a better answer than any summary of
     * them. One column of JSON rather than tables, because nothing queries into
     * it — it is read whole by the one screen that draws the ride.
     */
    @ColumnInfo(name = "distributions_json")
    val distributionsJson: String? = null
)