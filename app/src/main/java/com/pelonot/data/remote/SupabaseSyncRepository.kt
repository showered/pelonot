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
 * Every method returns a [SyncOutcome] rather than throwing, and every method
 * goes out **only for a rider with an account** — see [CloudAccess], which is
 * consulted at the single choke point below rather than at each call site.
 * [SyncOutcome.Disabled] is what an offline rider gets, and it is not an error:
 * it is the app working as designed for the rung most riders are on.
 *
 * Note that no method can be called without naming a profile. That is the
 * design, not an inconvenience: a cloud call with no rider attached is exactly
 * the thing rule 1 of the connectivity model forbids, and until now
 * `fetchClassTemplates()` was one.
 */
class SupabaseSyncRepository(
    private val cloudAccess: CloudAccess
) {

    private val client get() = SupabaseModule.client

    /** Uploads a workout with its full metric time series. */
    suspend fun syncWorkout(
        workout: WorkoutEntity,
        metrics: List<WorkoutMetricEntity>
    ): SyncOutcome<Unit> = execute("syncWorkout", workout.userId) { supabase ->
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
    suspend fun syncProfile(user: UserEntity): SyncOutcome<Unit> =
        execute("syncProfile", user.localUserId) { supabase ->
            supabase.from(TABLE_PROFILES).upsert(ProfileDto.from(user)) {
                onConflict = "local_user_id"
            }
        }

    /**
     * Fetches the shared class library.
     *
     * No longer the source of the library — the 72 classes ship in the APK and
     * `ClassTemplateSeeder` never asks (23.2.2). This is the *update* channel
     * of 23.2.3, which is why it now takes a rider: only a signed-in one may
     * ask, and there is no such thing as fetching on behalf of nobody.
     */
    suspend fun fetchClassTemplates(forProfileId: Int?): SyncOutcome<List<ClassTemplateDto>> =
        executeReturning("fetchClassTemplates", forProfileId) { supabase ->
            supabase.from(TABLE_CLASS_TEMPLATES)
                .select()
                .decodeList<ClassTemplateDto>()
        }

    private suspend inline fun execute(
        operation: String,
        localUserId: Int?,
        crossinline block: suspend (io.github.jan.supabase.SupabaseClient) -> Unit
    ): SyncOutcome<Unit> = executeReturning(operation, localUserId) { block(it) }

    /**
     * The one door out to the network. The gate is checked here, before the
     * client is even resolved, so a new method cannot forget to ask.
     */
    private suspend inline fun <T> executeReturning(
        operation: String,
        localUserId: Int?,
        crossinline block: suspend (io.github.jan.supabase.SupabaseClient) -> T
    ): SyncOutcome<T> {
        if (!cloudAccess.isAllowedFor(localUserId)) return SyncOutcome.Disabled
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
