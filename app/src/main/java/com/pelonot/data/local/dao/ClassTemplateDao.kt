package com.pelonot.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pelonot.data.local.entity.ClassTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClassTemplateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(templates: List<ClassTemplateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: ClassTemplateEntity)

    @Query("SELECT * FROM class_templates ORDER BY category, id ASC")
    fun getAllTemplates(): Flow<List<ClassTemplateEntity>>

    @Query("SELECT * FROM class_templates WHERE category = :category ORDER BY id ASC")
    fun getTemplatesByCategory(category: String): Flow<List<ClassTemplateEntity>>

    @Query("SELECT * FROM class_templates WHERE id = :id")
    suspend fun getTemplateById(id: String): ClassTemplateEntity?

    @Query("SELECT COUNT(*) FROM class_templates")
    suspend fun getTemplateCount(): Int

    @Query("DELETE FROM class_templates")
    suspend fun deleteAll()
}