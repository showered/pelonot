package com.pelonot.data.remote

import com.pelonot.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.serialization.json.Json

/**
 * Lazily builds the Supabase client from [BuildConfig] values that originate
 * in `local.properties` or the environment.
 *
 * Credentials used to be `const val`s in a checked-in `SupabaseConfig.kt`.
 * They are now absent by default, so a fresh clone builds a fully offline app:
 * [isConfigured] is false, [client] is null, and every call in
 * [SupabaseSyncRepository] returns [SyncOutcome.Disabled] instead of failing.
 */
object SupabaseModule {

    val isConfigured: Boolean =
        BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    val client: SupabaseClient? by lazy {
        if (!isConfigured) return@lazy null
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Postgrest)

            // Installing Auth is what makes every Postgrest request carry the
            // rider's own JWT instead of the anon key (PLAN 15.1.1). After
            // `003_cloud_identity.sql` the anon role can read the class library
            // and nothing else, so without this the app would be *correctly*
            // refused on every write it makes.
            install(Auth) {
                // A session that outlives the process, because a rider does not
                // sign in to a bike weekly. The SDK persists it to the tablet's
                // own encrypted preferences and refreshes it in the background;
                // an expiry must never surface as a screen (15.1.3).
                alwaysAutoRefresh = true
                autoLoadFromStorage = true
            }
        }
    }

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}

/**
 * Result of a cloud operation.
 *
 * [Disabled] is distinct from [Failed] so callers — notably
 * [com.pelonot.data.worker.WorkoutSyncWorker] — can tell "there is no cloud
 * configured, stop asking" apart from "the network is down, try again".
 */
sealed interface SyncOutcome<out T> {
    data class Success<T>(val value: T) : SyncOutcome<T>
    data object Disabled : SyncOutcome<Nothing>

    /**
     * Something went wrong and **trying again might work** — the network is
     * down, the endpoint is briefly unwell, a timeout. The overwhelming
     * majority of failures, and the reason the worker stops the batch rather
     * than hammering the radio nineteen more times for the same answer.
     */
    data class Failed(val cause: Throwable) : SyncOutcome<Nothing>

    /**
     * **The cloud understood the request and refused it, and will refuse it
     * again for ever** (PLAN 14.2.7).
     *
     * A different thing from [Failed] and separated the day it first happened:
     * the first-sign-in backfill hit a ride whose id was not a UUID (a seeded
     * fixture, `r1`), Postgres said `invalid input syntax for type uuid`, the
     * worker treated it as "the network is having a moment" and stopped the
     * batch — **so every ride behind it was stuck permanently**, and Settings
     * told the rider their backup was failing with no action available to them
     * that could ever fix it. Five good rides held hostage by one bad one.
     *
     * @param reason a sentence for the rider, already trimmed of the URL and
     *   headers the SDK's own message carries.
     */
    data class Rejected(val reason: String) : SyncOutcome<Nothing>

    val isSuccess: Boolean get() = this is Success

    fun valueOrNull(): T? = (this as? Success)?.value
}
