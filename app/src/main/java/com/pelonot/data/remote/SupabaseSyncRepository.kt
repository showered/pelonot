package com.pelonot.data.remote

import android.util.Log
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.data.local.entity.WorkoutMetricEntity
import com.pelonot.data.remote.dto.ClassTemplateDto
import com.pelonot.data.remote.dto.LeaderboardRowDto
import com.pelonot.data.remote.dto.ProfileDto
import com.pelonot.data.remote.dto.WorkoutDto
import com.pelonot.domain.cloud.CloudDeletion
import com.pelonot.domain.cloud.SyncFailure
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
/** One id off `workouts`, for the cheap half of a restore — see [SupabaseSyncRepository.fetchWorkoutIds]. */
@Serializable
private data class WorkoutIdDto(val id: String)

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
     * Removes this rider's own rows from the cloud (PLAN 15.4.2).
     *
     * **The rides first and the profile second**, which is not the order the
     * schema needs — `workouts.user_id` is `ON DELETE CASCADE` onto `profiles`
     * (`003`), so deleting the profile alone would take them all. It is the
     * order that lets the app *count* them: a cascade happens inside the
     * database and hands nothing back, and this returns a number a rider is
     * shown. Both statements are `select`ed for the same reason
     * ([CloudDeletion]).
     *
     * Every row it can reach is the rider's own, and that is a property of the
     * endpoint rather than of this filter: `003`'s delete policies are
     * `USING (auth.uid() = id)` and `USING (auth.uid() = user_id)`, so a
     * mistyped account id here deletes nothing rather than somebody else's
     * history. The filter is still written out, because a delete whose scope
     * exists only in a policy on a server is not a thing to read this file and
     * be confident about.
     *
     * What it deliberately does not touch is `device_link`: those rows have no
     * policy at all (`004`), belong to a pairing rather than to a history, and
     * expire in five minutes. There is nothing there for a rider to be rid of.
     */
    suspend fun deleteCloudCopy(localUserId: Int): SyncOutcome<CloudDeletion> =
        executeReturning("deleteCloudCopy", localUserId) { supabase, accountId ->
            val rides = supabase.from(TABLE_WORKOUTS).delete {
                select()
                filter { eq("user_id", accountId) }
            }.decodeList<JsonObject>().size

            val profiles = supabase.from(TABLE_PROFILES).delete {
                select()
                filter { eq("id", accountId) }
            }.decodeList<JsonObject>().size

            CloudDeletion(rides = rides, profileRemoved = profiles > 0)
        }

    /**
     * The ids of every ride this rider's account holds (PLAN 15.3.2).
     *
     * **One column, on purpose.** A restore's first question is *what is up
     * there that is not down here*, and answering it by fetching the rides
     * themselves would pull tens of megabytes to work out that none of them is
     * needed — a 45-minute ride's payload is 54 KB (14.4) and a year of riding
     * is a hundred of them. Ids are 36 bytes each, so this is a request a screen
     * can afford to make on the way in.
     *
     * The filter is the rider's own account and the policies say the same thing
     * (`003`), for [deleteCloudCopy]'s reason: a scope that exists only on a
     * server is not a thing to read this file and be sure about.
     */
    suspend fun fetchWorkoutIds(localUserId: Int): SyncOutcome<List<String>> =
        executeReturning("fetchWorkoutIds", localUserId) { supabase, accountId ->
            supabase.from(TABLE_WORKOUTS)
                .select(Columns.list("id")) { filter { eq("user_id", accountId) } }
                .decodeList<WorkoutIdDto>()
                .map { it.id }
        }

    /**
     * Whole rides, by id, for a restore (PLAN 15.3.2).
     *
     * **Called with a handful of ids at a time and that is the caller's job, not
     * a detail of it.** Each row carries its full series, so a request for forty
     * rides is a couple of megabytes in one response — on a bike's tablet, over
     * a household wifi, with nothing on screen but a spinner. Restoring in
     * batches also means an interrupted restore leaves the rides it did bring
     * down, which is the same argument as draining a backlog oldest-first
     * (14.2.5): partial progress that keeps is better than an all-or-nothing
     * that does not.
     */
    suspend fun fetchWorkouts(
        localUserId: Int,
        ids: List<String>
    ): SyncOutcome<List<WorkoutDto>> =
        executeReturning("fetchWorkouts", localUserId) { supabase, accountId ->
            if (ids.isEmpty()) return@executeReturning emptyList()
            supabase.from(TABLE_WORKOUTS)
                .select {
                    filter {
                        eq("user_id", accountId)
                        isIn("id", ids)
                    }
                }
                .decodeList<WorkoutDto>()
        }

    /**
     * The rider's cloud profile, or null if the account has never written one.
     *
     * Null is a real answer rather than a failure: an account created on a phone
     * and never ridden with has no profile row, and a restore has to be able to
     * say *there was nothing to bring down* without that reading as a network
     * problem.
     */
    suspend fun fetchProfile(localUserId: Int): SyncOutcome<ProfileDto?> =
        executeReturning("fetchProfile", localUserId) { supabase, accountId ->
            supabase.from(TABLE_PROFILES)
                .select { filter { eq("id", accountId) } }
                .decodeList<ProfileDto>()
                .firstOrNull()
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

    /**
     * Everyone registered's best measured effort at one class (PLAN 18.5).
     *
     * **No friend graph**, on the owner's instruction: three or four riders who
     * already know each other do not need to send each other requests. The
     * rule that makes that safe is not in this file — it is that public sign-up
     * is off (18.11.1), so "everyone registered" is the household and nobody
     * else.
     *
     * It is an RPC rather than a `select`, and that is the important part: the
     * function returns five columns for a board, while a policy widened to let
     * riders read each other's `workouts` rows would hand over ride dates, RPE
     * ratings, the full sample series, and every column the table grows later.
     * `workouts` and `profiles` keep "your own rows and nobody else's".
     *
     * Failure is an **empty board, not an exception**: this is drawn beside a
     * household board that came from Room and works offline, and a network
     * problem must not take that down with it (18.10 asks the screen to say
     * which it is, and `SyncOutcome` carries that distinction to it).
     */
    suspend fun classLeaderboard(
        classId: String,
        forProfileId: Int?
    ): SyncOutcome<List<LeaderboardRowDto>> =
        executeReturning("classLeaderboard", forProfileId) { supabase, _ ->
            supabase.postgrest.rpc(
                function = "class_leaderboard",
                parameters = buildJsonObject { put("p_class_id", classId) }
            ).decodeList<LeaderboardRowDto>()
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
            // **The message, never the exception** (PLAN 14.2.8a).
            //
            // `Log.w(tag, msg, e)` prints the SDK's own message, and for a
            // Postgrest failure that message is the whole request: the URL, the
            // header list, and inside it `Authorization: Bearer eyJ…` — the
            // rider's live access token — plus the apikey. Measured on the
            // tablet AVD, not theorised: it is right there in `logcat`.
            //
            // `Log.w` is also the one level that gets through on the bike,
            // where `log.tag` is `W` device-wide, so this is the *most* visible
            // line the app writes rather than the least.
            Log.w(TAG, "Supabase $operation failed: ${SyncFailure.riderFacing(e.message)}")
            // 14.2.7. "It failed" and "it will always fail" want opposite
            // responses, and treating the second as the first is what let one
            // unacceptable row hold five good ones hostage for ever.
            val status = (e as? RestException)?.statusCode
            if (SyncFailure.isPermanent(status)) {
                SyncOutcome.Rejected(SyncFailure.riderFacing(e.message))
            } else {
                SyncOutcome.Failed(e)
            }
        }
    }

    private companion object {
        const val TAG = "SupabaseSync"
        const val TABLE_WORKOUTS = "workouts"
        const val TABLE_PROFILES = "profiles"
        const val TABLE_CLASS_TEMPLATES = "class_templates"
    }
}
