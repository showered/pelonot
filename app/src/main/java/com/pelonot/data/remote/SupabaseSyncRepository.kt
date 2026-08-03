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

    /**
     * Uploads a workout with its full metric time series, **attributed to the
     * rider who rode it** (PLAN 14.2.1).
     *
     * The owner's account id comes out of the gate itself rather than being
     * looked up separately — see [CloudAccess.accountIdFor] for why those are
     * one question. Before this, `WorkoutDto` carried no owner at all and every
     * ride that ever left a tablet arrived anonymous.
     *
     * **An upsert, not an insert** (PLAN 15.3.3). The ride's id is a
     * client-generated UUID and is already the cloud's primary key, so sending
     * one twice is a duplicate-key failure rather than a harmless no-op — and
     * "twice" is not exotic: it is a retry after a timeout that actually
     * landed, a re-install restoring from a backup file, and above all the
     * first-sign-in backfill, which re-offers every ride the tablet has
     * (15.3.1). With `insert`, the first already-present ride in a backlog
     * fails the drain and stops it, permanently, with a message about a
     * constraint nobody will read.
     */
    suspend fun syncWorkout(
        workout: WorkoutEntity,
        metrics: List<WorkoutMetricEntity>
    ): SyncOutcome<Unit> = execute("syncWorkout", workout.userId) { supabase, accountId ->
        supabase.from(TABLE_WORKOUTS).upsert(WorkoutDto.from(workout, metrics, accountId)) {
            onConflict = "id"
        }
    }

    /**
     * Creates or updates the rider's cloud profile.
     *
     * `onConflict` is not optional — without it the upsert conflicts on the
     * primary key and every call inserts a fresh row. It targets `id`, which is
     * now the auth user id the DTO does send; it used to target `local_user_id`,
     * a per-device autoincrement that is `1` on every bike in the world. See
     * [ProfileDto] for what that would have done on the second tablet.
     *
     * The DTO is built from the account id the gate resolved rather than from
     * `user.authUserId` directly, so the row cannot be written under an identity
     * the gate did not just approve.
     */
    suspend fun syncProfile(user: UserEntity): SyncOutcome<Unit> =
        execute("syncProfile", user.localUserId) { supabase, accountId ->
            supabase.from(TABLE_PROFILES).upsert(ProfileDto.from(user, accountId)) {
                onConflict = "id"
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
        executeReturning("fetchClassTemplates", forProfileId) { supabase, _ ->
            // The only call that ignores the account id: `class_templates` is
            // public data (15.5.3) and the same 72 rows for everybody. It still
            // takes a rider, because *asking at all* is what rule 1 gates.
            supabase.from(TABLE_CLASS_TEMPLATES)
                .select()
                .decodeList<ClassTemplateDto>()
        }

    private suspend inline fun execute(
        operation: String,
        localUserId: Int?,
        crossinline block: suspend (io.github.jan.supabase.SupabaseClient, String) -> Unit
    ): SyncOutcome<Unit> = executeReturning(operation, localUserId) { c, id -> block(c, id) }

    /**
     * The one door out to the network. The gate is checked here, before the
     * client is even resolved, so a new method cannot forget to ask.
     *
     * It now hands the block **who the rider is up there** as well as letting it
     * through, because the gate's answer already contains that (see
     * [CloudAccess.accountIdFor]). The block cannot reach the network without an
     * account id in scope, which is the structural version of 14.2.1: a request
     * that does not know whose it is can no longer be written.
     */
    private suspend inline fun <T> executeReturning(
        operation: String,
        localUserId: Int?,
        crossinline block: suspend (io.github.jan.supabase.SupabaseClient, String) -> T
    ): SyncOutcome<T> {
        val accountId = cloudAccess.accountIdFor(localUserId) ?: return SyncOutcome.Disabled
        val supabase = client ?: return SyncOutcome.Disabled
        return try {
            SyncOutcome.Success(block(supabase, accountId))
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
