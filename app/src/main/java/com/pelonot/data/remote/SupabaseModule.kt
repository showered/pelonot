package com.pelonot.data.remote

import com.pelonot.BuildConfig
import io.github.jan.supabase.SupabaseClient
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
    data class Failed(val cause: Throwable) : SyncOutcome<Nothing>

    val isSuccess: Boolean get() = this is Success

    fun valueOrNull(): T? = (this as? Success)?.value
}
