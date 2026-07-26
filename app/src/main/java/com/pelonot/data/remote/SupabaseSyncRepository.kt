package com.pelonot.data.remote

import com.pelonot.data.local.entity.ClassTemplateEntity
import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.data.local.entity.WorkoutMetricEntity
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray

/**
 * Repository for syncing local data to Supabase cloud.
 */
class SupabaseSyncRepository {

    private val client = SupabaseClientProvider.client
    private val json = SupabaseClientProvider.json

    /**
     * Sync a completed workout (with compressed metrics) to Supabase.
     */
    suspend fun syncWorkout(
        workout: WorkoutEntity,
        metrics: List<WorkoutMetricEntity>
    ): Result<Unit> = runCatching {
        val metricSnapshots = metrics.map { m ->
            mapOf(
                "timestamp_sec" to m.timestampSec,
                "cadence" to m.cadence,
                "resistance" to m.resistance,
                "power" to m.power,
                "heart_rate" to (m.heartRate ?: 0)
            )
        }

        val payload = mapOf(
            "id" to workout.id,
            "duration_sec" to workout.durationSec,
            "total_output_kj" to workout.totalOutputKj,
            "total_distance_km" to workout.totalDistanceKm,
            "avg_cadence" to (workout.avgCadence ?: 0),
            "avg_power" to (workout.avgPower ?: 0),
            "avg_hr" to (workout.avgHr ?: 0),
            "intent_modifier" to workout.intentModifier,
            "rpe_rating" to (workout.rpeRating ?: 0),
            "metrics_payload" to metricSnapshots
        )

        client.from("workouts").insert(payload)
    }

    /**
     * Fetch all class templates from Supabase (for initial seeding).
     */
    suspend fun fetchClassTemplates(): Result<List<Map<String, Any?>>> = runCatching {
        client.from("class_templates").select().decodeList()
    }

    /**
     * Update user profile in Supabase.
     */
    suspend fun syncProfile(
        localUserId: Int,
        name: String,
        ftpWatts: Int,
        weightKg: Double
    ): Result<Unit> = runCatching {
        val payload = mapOf(
            "local_user_id" to localUserId,
            "name" to name,
            "ftp_watts" to ftpWatts,
            "weight_kg" to weightKg
        )
        client.from("profiles").upsert(payload)
    }
}