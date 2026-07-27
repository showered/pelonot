package com.pelonot.data.local

import android.content.Context
import com.pelonot.data.local.entity.ClassTemplateEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

/**
 * Seeds class templates from JSON assets into Room database.
 */
class ClassTemplateSeeder(private val context: Context) {
    
    @Serializable
    data class ClassTemplateJson(
        val id: String,
        val title: String,
        val category: String,
        val durationSec: Int,
        val intervalsJson: String
    )
    
    suspend fun seedIfEmpty() {
        val database = AppDatabase.getDatabase(context)
        if (database.classTemplateDao().getTemplateCount() > 0) return
        
        // Read from assets
        val assetManager = context.assets
        val categories = listOf("aerobic_engine", "hiit_heavy_climbs", "tabata_bursts", "threshold_pyramids")
        
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
                        durationSec = template.durationSec,
                        intervalsJson = template.intervalsJson
                    )
                    
                    database.classTemplateDao().insert(entity)
                } catch (e: Exception) {
                    // Log error but continue seeding
                }
            }
        }
    }
}