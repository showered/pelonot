package com.pelonot.data.local

import android.content.Context
import android.util.Log
import com.pelonot.data.local.dao.ClassTemplateDao
import com.pelonot.data.remote.dto.ClassTemplateDto
import com.pelonot.domain.model.IntervalParser
import kotlinx.serialization.json.Json

/**
 * Populates the class library on first launch, **from the bundled assets and
 * from nothing else**.
 *
 * This used to ask Supabase first and fall back to assets, which put a network
 * call on the very first path a fresh install takes — before there is a rider
 * on the tablet, let alone an account. Rule 1 of the connectivity model says a
 * rider with no account makes no request to Supabase at all, and first launch
 * is the one moment where that is true of everybody.
 *
 * The old order is also how the library came to be five classes rather than
 * seventy-two: the assets were the emergency fallback nobody expected to hit,
 * so nobody noticed how few of them there were. All 72 now ship in the APK
 * (~100 KB of JSON, ~9 KB once the package is compressed) and the cloud's
 * remaining job is to be an *update* channel for a signed-in rider — PLAN
 * 23.2.3 — never the source of the first copy.
 */
class ClassTemplateSeeder(
    private val context: Context,
    private val classTemplateDao: ClassTemplateDao
) {

    private val json = Json { ignoreUnknownKeys = true }

    /** No-op when the library is already populated. */
    suspend fun seedIfEmpty() {
        if (classTemplateDao.getTemplateCount() > 0) return
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
