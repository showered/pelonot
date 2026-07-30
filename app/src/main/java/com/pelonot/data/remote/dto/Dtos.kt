package com.pelonot.data.remote.dto

import com.pelonot.data.local.entity.ClassTemplateEntity
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.data.local.entity.WorkoutMetricEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    @SerialName("intervals_json") val intervalsJson: String
) {
    fun toEntity() = ClassTemplateEntity(
        id = id,
        title = title,
        category = category,
        durationSec = durationSec,
        intervalsJson = intervalsJson
    )
}

@Serializable
data class WorkoutMetricDto(
    @SerialName("timestamp_sec") val timestampSec: Int,
    val cadence: Double,
    val resistance: Double,
    val power: Double,
    @SerialName("heart_rate") val heartRate: Int? = null
) {
    companion object {
        fun from(metric: WorkoutMetricEntity) = WorkoutMetricDto(
            timestampSec = metric.timestampSec,
            cadence = metric.cadence,
            resistance = metric.resistance,
            power = metric.power,
            // Preserved as null rather than coerced to 0: a strap that was
            // never paired is not a rider with no pulse.
            heartRate = metric.heartRate
        )
    }
}

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
    @SerialName("recorded_at") val recordedAtEpochMs: Long,
    @SerialName("metrics_payload") val metrics: List<WorkoutMetricDto>
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
            recordedAtEpochMs = workout.timestamp,
            metrics = metrics.map(WorkoutMetricDto::from)
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
