package com.pelonot.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.pelonot.data.local.entity.ClassTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClassTemplateDao {
    /**
     * **Not `OnConflictStrategy.REPLACE`.** SQLite implements `REPLACE` as a
     * delete followed by an insert, and the delete fires foreign-key actions —
     * so re-inserting a class a rider has ridden runs `workouts.class_id`'s
     * `ON DELETE SET NULL` and quietly detaches every one of those rides from
     * the class they were. Measured, not assumed.
     *
     * That was harmless while seeding only ever ran against an empty table. It
     * stops being harmless the moment the library can be updated in place,
     * which is what PLAN 23.2.6c and 23.2.3 both need.
     */
    @Upsert
    suspend fun upsertAll(templates: List<ClassTemplateEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(template: ClassTemplateEntity)

    /**
     * The library a rider can choose from — retired classes are not in it.
     *
     * A retired class is one the bundled library no longer contains but that
     * somebody's history still points at (23.2.6c). It has to keep existing
     * and it must not be offered.
     */
    @Query("SELECT * FROM class_templates WHERE retired_at IS NULL ORDER BY category, id ASC")
    fun getAllTemplates(): Flow<List<ClassTemplateEntity>>

    @Query(
        "SELECT * FROM class_templates WHERE category = :category AND retired_at IS NULL " +
            "ORDER BY id ASC"
    )
    fun getTemplatesByCategory(category: String): Flow<List<ClassTemplateEntity>>

    /** Resolves retired classes too: this is the lookup history uses. */
    @Query("SELECT * FROM class_templates WHERE id = :id")
    suspend fun getTemplateById(id: String): ClassTemplateEntity?

    @Query("SELECT COUNT(*) FROM class_templates")
    suspend fun getTemplateCount(): Int

    @Query("SELECT id FROM class_templates")
    suspend fun allIds(): List<String>

    /** The ones still in the library — i.e. the ones retiring can act on. */
    @Query("SELECT id FROM class_templates WHERE retired_at IS NULL")
    suspend fun liveIds(): List<String>

    /**
     * `retired_at IS NULL` in the predicate so that reconciling twice does not
     * move the date: when a class left the library is a fact about the library,
     * not about how often the app has started.
     */
    @Query("UPDATE class_templates SET retired_at = :at WHERE id IN (:ids) AND retired_at IS NULL")
    suspend fun retire(ids: List<String>, at: Long)

    @Query("DELETE FROM class_templates WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM class_templates")
    suspend fun deleteAll()
}
