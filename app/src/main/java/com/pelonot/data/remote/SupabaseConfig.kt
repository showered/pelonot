package com.pelonot.data.remote

/**
 * Supabase connection configuration.
 *
 * IMPORTANT: In production, these values should be injected via BuildConfig
 * or local.properties. For development, they are defined here.
 *
 * The anon key is safe to include in the app binary as Supabase enforces
 * Row Level Security at the database level.
 */
object SupabaseConfig {
    const val SUPABASE_URL = "https://podsmtujqarlqhvorpdh.supabase.co"
    const val SUPABASE_ANON_KEY = "sb_publishable_mslwXtKEt3yLignctbQ-lw_XaTdDjsk"
}