package com.pelonot.data.remote.dto

import com.pelonot.data.local.entity.ClassTemplateEntity
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.data.local.entity.WorkoutMetricEntity
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
    /** Columnar since 14.4 — see [MetricsPayload] for why, and for what `v` is. */
    @SerialName("metrics_payload") val metrics: MetricsPayload
) {
    companion object {
        fun from(workout: WorkoutEntity, metrics: List<WorkoutMetricEntity>) = WorkoutDto(
            id = workout.id,
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
            metrics = MetricsPayload.from(metrics)
        )
    }
}

@Serializable
data class ProfileDto(
    @SerialName("local_user_id") val localUserId: Int,
    val name: String,
    @SerialName("ftp_watts") val ftpWatts: Int,
    @SerialName("weight_kg") val weightKg: Double
) {
    companion object {
        fun from(user: UserEntity) = ProfileDto(
            localUserId = user.localUserId,
            name = user.name,
            ftpWatts = user.ftpWatts,
            weightKg = user.weightKg
        )
    }
}
