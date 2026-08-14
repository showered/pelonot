package com.pelonot.data.remote.dto

import com.pelonot.data.local.entity.ClassTemplateEntity
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.data.local.entity.WorkoutMetricEntity
import com.pelonot.domain.model.MaxHeartRate
import com.pelonot.domain.model.PowerProvenance
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Epoch milliseconds as ISO-8601 UTC.
 *
 * The cloud columns are `TIMESTAMPTZ`. Sending the raw epoch millis Long that
 * Room stores makes Postgres try to parse `1753900000000` as a date and fail
 * with `22008 date/time field value out of range` — verified against the live
 * project. Room keeps epoch millis locally; this is the boundary that converts.
 *
 * `SimpleDateFormat` rather than `java.time` because minSdk is 24 and core
 * library desugaring is not enabled. It is not thread-safe, so it is built per
 * call — this runs once per sync, not per sample.
 */
internal fun Long.toIso8601Utc(): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date(this))

/**
 * The other direction, for a ride coming back down (PLAN 15.3.2).
 *
 * **Null when it cannot be read, and the caller must skip the ride rather than
 * substitute anything.** A date is not a nice-to-have on a workout: it is what
 * history sorts by, what the week's totals count, and what the trimmer measures
 * age against. A ride restored at the epoch would appear in January 1970,
 * quietly, at the bottom of every list — a fabricated record of the kind the
 * rest of this file exists to refuse.
 *
 * It is hand-parsed rather than given to `SimpleDateFormat` because what comes
 * back is not one shape. Postgres renders a `TIMESTAMPTZ` in the session's time
 * zone with a variable number of fractional digits and either a `Z` or a
 * `+00:00` — `2026-08-13T09:14:22.481293+00:00` is a real answer from this
 * endpoint, and `SimpleDateFormat`'s `X` pattern is API 24-safe but its `S`
 * pattern reads *six* digits as milliseconds and lands the ride eight minutes
 * late. The offset is applied rather than assumed, so a project whose session
 * zone is not UTC does not move everybody's history.
 */
internal fun String.fromIso8601(): Long? {
    val match = ISO_8601.matchEntire(trim()) ?: return null
    val (year, month, day, hour, minute, second) = match.destructured
    val fraction = match.groupValues[7]
    val offset = match.groupValues[8]

    val calendar = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply {
        clear()
        set(year.toInt(), month.toInt() - 1, day.toInt(), hour.toInt(), minute.toInt(), second.toInt())
    }
    // Only ever the first three digits: Postgres emits microseconds, and reading
    // `481293` as milliseconds is where a ride arrives eight minutes late.
    val millis = fraction.take(3).padEnd(3, '0').toIntOrNull() ?: 0

    val offsetMs = when {
        offset.isEmpty() || offset == "Z" || offset == "z" -> 0L
        else -> {
            val sign = if (offset.first() == '-') -1 else 1
            val digits = offset.drop(1).replace(":", "")
            val hours = digits.take(2).toLong()
            val minutes = if (digits.length > 2) digits.drop(2).take(2).toLong() else 0L
            sign * ((hours * 60 + minutes) * 60_000L)
        }
    }

    return calendar.timeInMillis + millis - offsetMs
}

private val ISO_8601 = Regex(
    """(\d{4})-(\d{2})-(\d{2})[Tt ](\d{2}):(\d{2}):(\d{2})(?:\.(\d+))?(Z|z|[+-]\d{2}:?\d{2})?"""
)

/**
 * Wire types for the Supabase tables.
 *
 * The previous code passed `Map<String, Any?>` to Postgrest and decoded rows
 * as `List<Map<String, Any?>>`. kotlinx.serialization has no serializer for
 * `Any`, so the decode threw at runtime on every call; because the caller
 * wrapped it in `runCatching`, the failure was invisible and the seeder simply
 * always fell through to its local-assets path.
 */

@Serializable
data class ClassTemplateDto(
    val id: String,
    val title: String,
    val category: String,
    @SerialName("duration_sec") val durationSec: Int,
    /**
     * What the ride is for, in words (PLAN 23.2.7). Authored in
     * `classlibrary/descriptions.py`; every other fact about a class is derived
     * from its blocks and so can only ever restate them.
     *
     * Defaulted rather than required, because the cloud's copy of the library
     * predates the column and a class arriving without one is a class with no
     * description — not a parse failure that costs the rider the whole class.
     */
    val description: String = "",
    // A JSON string in the assets, a JSONB array in the cloud. See
    // [IntervalsJsonSerializer] — reading it as a plain String meant the cloud's
    // 72 class templates had never once been decoded.
    @SerialName("intervals_json")
    @Serializable(with = IntervalsJsonSerializer::class)
    val intervalsJson: String
) {
    fun toEntity() = ClassTemplateEntity(
        id = id,
        title = title,
        category = category,
        durationSec = durationSec,
        description = description,
        intervalsJson = intervalsJson
    )
}

// `WorkoutMetricDto` — one object per sample — was here until 14.4. It was
// replaced rather than kept beside `MetricsPayload` because two wire shapes for
// one series is how a reader ends up guessing. Nullability did not change with
// it: a heart rate is still preserved as null rather than coerced to 0, since a
// strap that was never paired is not a rider with no pulse.

@Serializable
data class WorkoutDto(
    val id: String,
    /**
     * **The rider this ride belongs to, and it is not optional** (PLAN 14.2.1).
     *
     * It holds the *auth user id*, because that is what `profiles.id` is in the
     * cloud — see `supabase/003_cloud_identity.sql`. The local `Int`
     * `local_user_id` never leaves the tablet: it is a per-device autoincrement
     * and every bike's first profile is `1`.
     *
     * Non-null rather than nullable, which is the whole point of the change.
     * The column existed and the DTO simply never sent it, so **every ride this
     * app ever uploaded arrived with `user_id` NULL** — unattributable, never
     * restorable onto a second bike, never on a leaderboard, and invisible to
     * any RLS policy written against `auth.uid()`. A nullable field here would
     * leave that shape representable; this way an anonymous ride does not
     * compile.
     */
    @SerialName("user_id") val userId: String,
    @SerialName("class_id") val classId: String? = null,
    @SerialName("duration_sec") val durationSec: Int,
    @SerialName("total_output_kj") val totalOutputKj: Double,
    @SerialName("total_distance_km") val totalDistanceKm: Double,
    @SerialName("avg_cadence") val avgCadence: Double? = null,
    @SerialName("avg_power") val avgPower: Double? = null,
    @SerialName("avg_hr") val avgHr: Double? = null,
    @SerialName("intent_modifier") val intentModifier: Double,
    @SerialName("rpe_rating") val rpeRating: Int? = null,
    @SerialName("recorded_at") val recordedAt: String,
    /**
     * Where this ride's watts came from, reduced to one word (PLAN 18.5).
     *
     * The per-sample `pm` column inside the payload stays and is still the
     * truth (14.4.7); this is the answer `PowerProvenance` already computes
     * *from* it, stored beside the row so a leaderboard can be a query rather
     * than 47 KB of JSON per rider. A cross-bike comparison is only honest
     * between measured rides — `PowerModel` scores RMSE 137 W against the real
     * board — so this is what 18.7 and 24.4.2 filter on.
     *
     * Nullable on the wire because every row uploaded before it existed has no
     * answer, and `Unknown` is a claim those rows cannot support either.
     */
    @SerialName("power_provenance") val powerProvenance: String? = null,
    /** Columnar since 14.4 — see [MetricsPayload] for why, and for what `v` is. */
    @SerialName("metrics_payload") val metrics: MetricsPayload
) {
    companion object {
        /**
         * @param ownerAuthUserId the auth user id of the profile that rode it,
         *   resolved by [com.pelonot.data.remote.CloudAccess.accountIdFor]. It
         *   is a parameter rather than something read off the entity because
         *   `WorkoutEntity` holds the *local* profile id and the mapping to an
         *   account lives one table away.
         */
        fun from(
            workout: WorkoutEntity,
            metrics: List<WorkoutMetricEntity>,
            ownerAuthUserId: String
        ) = WorkoutDto(
            id = workout.id,
            userId = ownerAuthUserId,
            classId = workout.classId,
            durationSec = workout.durationSec,
            totalOutputKj = workout.totalOutputKj,
            totalDistanceKm = workout.totalDistanceKm,
            avgCadence = workout.avgCadence,
            avgPower = workout.avgPower,
            avgHr = workout.avgHr,
            intentModifier = workout.intentModifier,
            rpeRating = workout.rpeRating,
            recordedAt = workout.timestamp.toIso8601Utc(),
            // **Read off the row now that the row carries it** (23.4.12), with
            // the sample reduction kept as the fallback for a ride recorded
            // before the column existed and not yet backfilled. It was the
            // reduction alone, on the argument that computing it here means the
            // wire value and the app's own answer cannot disagree — which was
            // right about the goal and became wrong about the means the moment
            // the app's own answer moved onto the row. The cloud's copy of this
            // column is the same four words (`007_everyone_leaderboard.sql`),
            // and 23.4.6 keeps an unsynced ride out of the trimmer, so the
            // fallback is never asked to answer for a series that has gone.
            powerProvenance = (
                workout.powerProvenance ?: PowerProvenance.of(
                    measured = metrics.count { it.powerIsMeasured == true },
                    modelled = metrics.count { it.powerIsMeasured == false },
                    unknown = metrics.count { it.powerIsMeasured == null }
                )
                ).name,
            // 23.4.14. The payload says what resolution it is, because an
            // upload is an export and 23.4.3's rule covers exports.
            metrics = MetricsPayload.from(
                metrics = metrics,
                detailSec = workout.metricsDetailSec,
                // 15.3.2. Everything on the row that the cloud has no column
                // for, which is everything this project learnt after 14.4.
                ride = RideFacts.of(workout)
            )
        )
    }

    /**
     * Back to a row on **this** tablet (PLAN 15.3.2).
     *
     * Three of its arguments are things the cloud copy cannot know and the
     * restoring tablet must decide, which is why they are parameters rather than
     * fields:
     *
     * - [localUserId] — `workouts.user_id` is a per-device autoincrement here
     *   and an account id up there. They are different kinds of thing and the
     *   mapping is the restore's whole point.
     * - [classId] — the caller passes the class **only if this tablet has it**.
     *   `workouts.class_id` is a foreign key onto `class_templates`, so a ride
     *   of a class this build's bundle does not carry would fail to insert
     *   altogether; a free ride is a smaller loss than a missing one, and
     *   `retired_at` means the commonest case is a class that was here once.
     * - [syncedAt] — a restored ride is in the cloud **by definition**, so it is
     *   marked as backed up rather than re-queued. Without this the first drain
     *   after a restore would upload every ride it had just downloaded.
     *
     * `is_complete` is true and not carried: the cloud only ever receives
     * finished rides (`WorkoutDao.unsyncedWorkouts` filters on it), and a row
     * arriving as unfinished would be offered to the rider as a ride to resume.
     *
     * `power_bests_at` is deliberately **left null**. The efforts are not on the
     * wire and must not be — they are derived, and 16.3.3a's backfill will walk
     * the restored samples itself. For a ride that came down as an outline it
     * will decline to, which is 23.4.15 and is the right answer.
     */
    fun toEntity(
        localUserId: Int,
        classId: String?,
        syncedAt: Long,
        recordedAtMs: Long
    ): WorkoutEntity = WorkoutEntity(
        id = id,
        userId = localUserId,
        classId = classId,
        durationSec = durationSec,
        totalOutputKj = totalOutputKj,
        totalDistanceKm = totalDistanceKm,
        avgCadence = avgCadence,
        avgPower = avgPower,
        avgHr = avgHr,
        intentModifier = intentModifier,
        ftpWatts = metrics.ride?.ftpWatts,
        maxHrBpm = metrics.ride?.maxHrBpm,
        // 21.4.2c, resolved the same way `power_provenance` is two lines down:
        // a word this build does not know becomes null, which is the one value
        // every reader already handles.
        maxHrSource = MaxHeartRate.Source.entries
            .firstOrNull { it.name == metrics.ride?.maxHrSource },
        rpeRating = rpeRating,
        isComplete = true,
        wasRecovered = metrics.ride?.wasRecovered ?: false,
        timestamp = recordedAtMs,
        syncedAt = syncedAt,
        resumeCount = metrics.ride?.resumeCount ?: 0,
        interruptedSec = metrics.ride?.interruptedSec ?: 0,
        powerProvenance = PowerProvenance.entries.firstOrNull { it.name == powerProvenance },
        metricsDetailSec = metrics.detailSec,
        distributionsJson = metrics.ride?.distributions?.encode()
    )
}

/**
 * One rider's place on a cross-bike leaderboard (PLAN 18.5).
 *
 * The whole of what `class_leaderboard` returns, and deliberately not one field
 * more — see `supabase/007_everyone_leaderboard.sql` for why the visibility is
 * a function rather than a policy. If this type ever grows a `recorded_at` or a
 * `workout_id`, the migration has been widened and that is a decision somebody
 * should have had to make on purpose.
 */
@Serializable
data class LeaderboardRowDto(
    @SerialName("account_id") val accountId: String,
    val name: String,
    @SerialName("output_kj") val outputKj: Double,
    @SerialName("weight_kg") val weightKg: Double,
    @SerialName("is_you") val isYou: Boolean
)

/**
 * A rider's cloud profile, keyed by their **auth user id** (PLAN 14.2.1).
 *
 * It used to be keyed by `local_user_id`, with the cloud column `UNIQUE` and
 * the upsert's `onConflict` pointing at it. That was correct for exactly as
 * long as there was one tablet in the world. `local_user_id` is a per-device
 * autoincrement, so **bike A's first profile and bike B's first profile are
 * both `1`** — and the second one to sync would not have collided harmlessly,
 * it would have *updated the first rider's row*, overwriting their name, weight
 * and FTP. The failure needs no malice and no unusual sequence: it is the first
 * thing that happens on the day a second bike signs in.
 *
 * The auth user id is the right key because, under rule 2 of the connectivity
 * model, a cloud profile and an account are 1:1 by construction — there is no
 * such thing as a cloud profile without an account. So `profiles.id` **is** the
 * auth uid rather than a `gen_random_uuid()` that has to be read back and
 * stored locally (which is what 14.2.1 originally asked for, and is a two-step
 * sync that can half-fail).
 */
@Serializable
data class ProfileDto(
    /** The auth user id. Also the cloud primary key, and the upsert target. */
    val id: String,
    val name: String,
    @SerialName("ftp_watts") val ftpWatts: Int,
    @SerialName("weight_kg") val weightKg: Double
) {
    companion object {
        /**
         * @param ownerAuthUserId the account id the gate just approved, passed
         *   in rather than read off `user.authUserId` for the same reason
         *   [WorkoutDto.from] takes one: a profile with no account is not a
         *   degraded cloud profile, it is **not a cloud profile at all**, and
         *   requiring the caller to have an id in hand means that row cannot be
         *   built by a path that never asked.
         */
        fun from(user: UserEntity, ownerAuthUserId: String) = ProfileDto(
            id = ownerAuthUserId,
            name = user.name,
            ftpWatts = user.ftpWatts,
            weightKg = user.weightKg
        )
    }
}
