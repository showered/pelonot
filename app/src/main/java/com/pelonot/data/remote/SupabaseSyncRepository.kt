package com.pelonot.data.remote

import android.util.Log
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.data.local.entity.WorkoutMetricEntity
import com.pelonot.data.remote.dto.ClassTemplateDto
import com.pelonot.data.remote.dto.ProfileDto
import com.pelonot.data.remote.dto.WorkoutDto
import io.github.jan.supabase.postgrest.from

/**
 * Pushes completed rides and profiles to Supabase and pulls the shared class
 * library down.
 *
 * Every method returns a [SyncOutcome] rather than throwing, and reports
 * [SyncOutcome.Disabled] when no credentials are configured, so cloud sync is
 * genuinely optional rather than a silent failure path.
 */
class SupabaseSyncRepository(
    private val enabled: () -> Boolean = { true }
) {

    private val client get() = SupabaseModule.client

    val isConfigured: Boolean get() = SupabaseModule.isConfigured

    /** Uploads a workout with its full metric time series. */
    suspend fun syncWorkout(
        workout: WorkoutEntity,
        metrics: List<WorkoutMetricEntity>
    ): SyncOutcome<Unit> = execute("syncWorkout") { supabase ->
        supabase.from(TABLE_WORKOUTS).insert(WorkoutDto.from(workout, metrics))
    }

    /**
     * Creates or updates the rider's cloud profile.
     *
     * `onConflict` is not optional. Without it the upsert conflicts on the
     * primary key `id` — a UUID the DTO does not send — so every call inserted
     * a fresh row instead of updating the rider's. `local_user_id` is the
     * natural key and is `UNIQUE` in the schema.
     */
    suspend fun syncProfile(user: UserEntity): SyncOutcome<Unit> = execute("syncProfile") { supabase ->
        supabase.from(TABLE_PROFILES).upsert(ProfileDto.from(user)) {
            onConflict = "local_user_id"
        }
    }

    /** Fetches the shared class library for seeding. */
    suspend fun fetchClassTemplates(): SyncOutcome<List<ClassTemplateDto>> =
        executeReturning("fetchClassTemplates") { supabase ->
            supabase.from(TABLE_CLASS_TEMPLATES)
                .select()
                .decodeList<ClassTemplateDto>()
        }

    private suspend inline fun execute(
        operation: String,
        crossinline block: suspend (io.github.jan.supabase.SupabaseClient) -> Unit
    ): SyncOutcome<Unit> = executeReturning(operation) { block(it) }

    private suspend inline fun <T> executeReturning(
        operation: String,
        crossinline block: suspend (io.github.jan.supabase.SupabaseClient) -> T
    ): SyncOutcome<T> {
        if (!enabled()) return SyncOutcome.Disabled
        val supabase = client ?: return SyncOutcome.Disabled
        return try {
            SyncOutcome.Success(block(supabase))
        } catch (e: Exception) {
            Log.w(TAG, "Supabase $operation failed", e)
            SyncOutcome.Failed(e)
        }
    }

    private companion object {
        const val TAG = "SupabaseSync"
        const val TABLE_WORKOUTS = "workouts"
        const val TABLE_PROFILES = "profiles"
        const val TABLE_CLASS_TEMPLATES = "class_templates"
    }
}
