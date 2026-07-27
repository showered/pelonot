package com.pelonot.data.local

import android.content.Context
import com.pelonot.data.local.entity.ClassTemplateEntity
import com.pelonot.data.remote.SupabaseSyncRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

/**
 * Seeds class templates from Supabase into Room database.
 * Falls back to local assets if Supabase is unavailable.
 */
class ClassTemplateSeeder(
    private val context: Context,
    private val syncRepository: SupabaseSyncRepository
) {
    
    @Serializable
    data class ClassTemplateJson(
        val id: String,
        val title: String,
        val category: String,
        val duration_sec: Int,
        val intervals_json: String
    )
     
    suspend fun seedIfEmpty() {
        val database = AppDatabase.getDatabase(context)
        if (database.classTemplateDao().getTemplateCount() > 0) return
         
        // Try to fetch from Supabase first
        val result = syncRepository.fetchClassTemplates()
        if (result.isSuccess) {
            val templates = result.getOrNull() ?: return
            for (template in templates) {
                val entity = ClassTemplateEntity(
                    id = template["id"] as? String ?: continue,
                    title = template["title"] as? String ?: continue,
                    category = template["category"] as? String ?: continue,
                    durationSec = (template["duration_sec"] as? Number)?.toInt() ?: continue,
                    intervalsJson = (template["intervals_json"] as? String) ?: continue
                )
                database.classTemplateDao().insert(entity)
            }
            return
        }
        
        // Fallback to assets if Supabase fails
        seedFromAssets(database)
    }
    
    private suspend fun seedFromAssets(database: AppDatabase) {
        val assetManager = context.assets
        val categories = listOf("endurance", "sweet_spot", "threshold", "vo2_max", "hiit_heavy_climbs", "tabata_bursts", "recovery")
         
        for (category in categories) {
            val files = try {
                assetManager.list("classes/$category")?.toList() ?: continue
            } catch (e: Exception) {
                continue
            }
             
            for (file in files) {
                if (!file.endsWith(".json")) continue
                 
                try {
                    val inputStream = assetManager.open("classes/$category/$file")
                    val jsonString = inputStream.bufferedReader().use { it.readText() }
                    val template = Json.decodeFromString(ClassTemplateJson.serializer(), jsonString)
                     
                    val entity = ClassTemplateEntity(
                        id = template.id,
                        title = template.title,
                        category = template.category,
                        durationSec = template.duration_sec,
                        intervalsJson = template.intervals_json
                    )
                     
                    database.classTemplateDao().insert(entity)
                } catch (e: Exception) {
                    // Log error but continue seeding
                }
            }
        }
    }
}
