package com.pelonot.data.remote

import com.pelonot.data.local.entity.ClassTemplateEntity
import com.pelonot.data.local.entity.WorkoutEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Singleton Supabase client configured from SupabaseConfig.
 */
object SupabaseClientProvider {
    val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = SupabaseConfig.SUPABASE_URL,
        supabaseKey = SupabaseConfig.SUPABASE_ANON_KEY
    ) {
        install(Postgrest)
    }

    val json = Json { ignoreUnknownKeys = true }
}

/**
 * DTO for syncing workout metrics as compressed JSON payload.
 */
@Serializable
data class MetricSnapshot(
    val timestampSec: Int,
    val cadence: Double,
    val resistance: Double,
    val power: Double,
    val heartRate: Int? = null
)