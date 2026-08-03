package com.pelonot.data.remote.dto

import com.pelonot.data.local.entity.ClassTemplateEntity
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.data.local.entity.WorkoutMetricEntity
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
            // Reduced from the samples rather than read off the row, because
            // the row does not carry it: `PowerProvenance` *is* the reduction,
            // and computing it here means the wire value and the app's own
            // answer cannot disagree.
            powerProvenance = PowerProvenance.of(
                measured = metrics.count { it.powerIsMeasured == true },
                modelled = metrics.count { it.powerIsMeasured == false },
                unknown = metrics.count { it.powerIsMeasured == null }
            ).name,
            metrics = MetricsPayload.from(metrics)
        )
    }
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
