package com.pelonot.data.local

import android.content.Context
import android.util.Log
import com.pelonot.data.local.dao.ClassTemplateDao
import com.pelonot.data.local.entity.ClassTemplateEntity
import com.pelonot.data.remote.SupabaseSyncRepository
import com.pelonot.data.remote.SyncOutcome
import com.pelonot.data.remote.dto.ClassTemplateDto
import com.pelonot.domain.model.IntervalParser
import kotlinx.serialization.json.Json

/**
 * Populates the class library on first launch: from Supabase when it is
 * configured and reachable, otherwise from the bundled assets.
 */
class ClassTemplateSeeder(
    private val context: Context,
    private val classTemplateDao: ClassTemplateDao,
    private val syncRepository: SupabaseSyncRepository
) {

    private val json = Json { ignoreUnknownKeys = true }

    /** No-op when the library is already populated. */
    suspend fun seedIfEmpty() {
        if (classTemplateDao.getTemplateCount() > 0) return

        when (val outcome = syncRepository.fetchClassTemplates()) {
            is SyncOutcome.Success -> {
                val templates = outcome.value.map(ClassTemplateDto::toEntity)
                if (templates.isNotEmpty()) {
                    classTemplateDao.insertAll(templates)
                    Log.i(TAG, "Seeded ${templates.size} class templates from Supabase")
                    return
                }
                Log.i(TAG, "Supabase returned no templates; falling back to assets")
            }

            SyncOutcome.Disabled -> Log.i(TAG, "Cloud sync disabled; seeding from assets")
            is SyncOutcome.Failed -> Log.w(TAG, "Supabase seed failed; using assets", outcome.cause)
        }

        seedFromAssets()
    }

    /**
     * Reads every `.json` under `assets/classes/`.
     *
     * The previous version iterated a hardcoded list of seven category
     * directory names, four of which do not exist and three of which were
     * missing, so adding a class folder silently did nothing. Listing the
     * directory means the bundled library is whatever is actually shipped.
     */
    private suspend fun seedFromAssets() {
        val assets = context.assets
        val categories = runCatching { assets.list(ASSET_ROOT)?.toList().orEmpty() }
            .getOrElse { error ->
                Log.e(TAG, "Could not list $ASSET_ROOT", error)
                emptyList()
            }

        val templates = buildList {
            for (category in categories) {
                val files = runCatching { assets.list("$ASSET_ROOT/$category")?.toList().orEmpty() }
                    .getOrDefault(emptyList())

                for (fileName in files.filter { it.endsWith(".json") }) {
                    val path = "$ASSET_ROOT/$category/$fileName"
                    val entity = runCatching {
                        val raw = assets.open(path).bufferedReader().use { it.readText() }
                        json.decodeFromString<ClassTemplateDto>(raw).toEntity()
                    }.getOrElse { error ->
                        Log.e(TAG, "Skipping malformed class template $path", error)
                        null
                    } ?: continue

                    // Catch authoring mistakes at seed time rather than
                    // rendering a class with no intervals in it.
                    IntervalParser.parse(entity.intervalsJson).onFailure { error ->
                        Log.e(TAG, "Class ${entity.id} has unreadable intervals_json", error)
                    }

                    add(entity)
                }
            }
        }

        if (templates.isEmpty()) {
            Log.w(TAG, "No class templates found under assets/$ASSET_ROOT")
            return
        }

        classTemplateDao.insertAll(templates)
        Log.i(TAG, "Seeded ${templates.size} class templates from assets")
    }

    private companion object {
        const val TAG = "ClassTemplateSeeder"
        const val ASSET_ROOT = "classes"
    }
}
