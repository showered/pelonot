package com.pelonot.data.local

import android.content.Context
import android.util.Log
import com.pelonot.data.local.dao.ClassTemplateDao
import com.pelonot.data.local.dao.WorkoutDao
import com.pelonot.data.remote.dto.ClassTemplateDto
import com.pelonot.domain.model.IntervalParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Keeps the class library in the database equal to the one bundled in the APK,
 * **and to nothing else**.
 *
 * This used to ask Supabase first and fall back to assets, which put a network
 * call on the very first path a fresh install takes — before there is a rider
 * on the tablet, let alone an account. Rule 1 of the connectivity model says a
 * rider with no account makes no request to Supabase at all, and first launch
 * is the one moment where that is true of everybody. The cloud's remaining job
 * is to be an *update* channel for a signed-in rider (PLAN 23.2.3), never the
 * source of the first copy.
 *
 * It also used to stop at "is the table empty?", which was right for as long as
 * the library never changed. 23.2.6 rebuilt all 72 classes, and a bike with the
 * old ones seeded would have kept them forever. So this reconciles instead:
 *
 * - every bundled class is upserted, so an edited class reaches an install that
 *   already had it;
 * - a class the bundle no longer contains is **retired** if a ride points at
 *   it, and deleted if nobody rode it.
 *
 * Retiring rather than deleting is the whole point. `workouts.class_id` is a
 * foreign key with `ON DELETE SET NULL`, so removing a class a rider has ridden
 * does not merely lose the name — it turns their ride into a ride of nothing.
 * A retired class keeps its row, drops out of the library browser, and is still
 * resolvable by id from history.
 *
 * Reading 72 files on every launch would be waste, so the work is gated on a
 * fingerprint written into `assets/class_library.json` by
 * `classlibrary/build.py`. The fingerprint is stored **last**: if the process
 * dies mid-reconcile, the next launch simply does it again.
 */
class ClassTemplateSeeder(
    private val context: Context,
    private val classTemplateDao: ClassTemplateDao,
    private val workoutDao: WorkoutDao
) {

    private val json = Json { ignoreUnknownKeys = true }

    private val preferences by lazy {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    }

    /**
     * Brings the library up to date with the bundle. A no-op on every launch
     * where neither has changed, which is nearly all of them.
     */
    suspend fun syncWithBundledLibrary() {
        val fingerprint = bundledFingerprint()
        val seeded = classTemplateDao.getTemplateCount() > 0
        if (seeded && fingerprint != null && fingerprint == preferences.getString(KEY, null)) {
            return
        }

        val templates = readBundledLibrary()
        if (templates.isEmpty()) {
            // Never reconcile against nothing: that would retire the entire
            // library because one asset read failed.
            Log.w(TAG, "No class templates found under assets/$ASSET_ROOT")
            return
        }

        classTemplateDao.upsertAll(templates)
        retireWhatTheBundleDropped(templates.map { it.id }.toSet())

        preferences.edit().putString(KEY, fingerprint).apply()
        Log.i(TAG, "Class library synced: ${templates.size} classes, fingerprint $fingerprint")
    }

    private suspend fun retireWhatTheBundleDropped(bundled: Set<String>) {
        val departed = classTemplateDao.allIds().toSet() - bundled
        if (departed.isEmpty()) return

        val referenced = workoutDao.referencedClassIds().toSet()
        val orphaned = (departed - referenced).toList()
        // Only the ones still live: a class retired by an earlier sync is
        // already retired, and re-reporting it would say work was done that
        // was not. `retire` guards the date for the same reason.
        val ridden = departed.intersect(referenced).intersect(classTemplateDao.liveIds().toSet())

        if (orphaned.isNotEmpty()) {
            classTemplateDao.deleteByIds(orphaned)
            Log.i(TAG, "Dropped ${orphaned.size} classes nobody had ridden")
        }
        if (ridden.isNotEmpty()) {
            classTemplateDao.retire(ridden.toList(), System.currentTimeMillis())
            Log.i(TAG, "Retired ${ridden.size} classes that history still points at: $ridden")
        }
    }

    /**
     * The bundle's own summary of itself, so the common case costs one small
     * read rather than seventy-two.
     *
     * Null when it cannot be read, which makes the caller reconcile — the safe
     * direction, since the alternative is an install that silently never
     * updates.
     */
    private fun bundledFingerprint(): String? = runCatching {
        val raw = context.assets.open(MANIFEST).bufferedReader().use { it.readText() }
        (json.parseToJsonElement(raw) as JsonObject)["fingerprint"]?.jsonPrimitive?.content
    }.getOrElse { error ->
        Log.w(TAG, "Could not read $MANIFEST; reconciling the whole library", error)
        null
    }

    /**
     * Reads every `.json` under `assets/classes/`.
     *
     * An earlier version iterated a hardcoded list of seven category directory
     * names, four of which did not exist and three of which were missing, so
     * adding a class folder silently did nothing. Listing the directory means
     * the bundled library is whatever is actually shipped.
     */
    private fun readBundledLibrary(): List<com.pelonot.data.local.entity.ClassTemplateEntity> {
        val assets = context.assets
        val categories = runCatching { assets.list(ASSET_ROOT)?.toList().orEmpty() }
            .getOrElse { error ->
                Log.e(TAG, "Could not list $ASSET_ROOT", error)
                emptyList()
            }

        return buildList {
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

                    // Catch authoring mistakes here rather than rendering a
                    // class with no intervals in it.
                    IntervalParser.parse(entity.intervalsJson).onFailure { error ->
                        Log.e(TAG, "Class ${entity.id} has unreadable intervals_json", error)
                    }

                    add(entity)
                }
            }
        }
    }

    private companion object {
        const val TAG = "ClassTemplateSeeder"
        const val ASSET_ROOT = "classes"
        const val MANIFEST = "class_library.json"
        const val PREFERENCES = "class_library"
        const val KEY = "bundled_fingerprint"
    }
}
