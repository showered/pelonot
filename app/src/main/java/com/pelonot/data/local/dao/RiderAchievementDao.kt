package com.pelonot.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pelonot.data.local.entity.RiderAchievementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RiderAchievementDao {
    /** `IGNORE` makes a second finalise harmless without replacing a permanent row. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rows: List<RiderAchievementEntity>)

    @Query("SELECT achievement_id FROM rider_achievements WHERE user_id = :userId")
    suspend fun idsFor(userId: Int): List<String>

    @Query("SELECT * FROM rider_achievements WHERE user_id = :userId ORDER BY earned_at DESC, id ASC")
    fun observeFor(userId: Int): Flow<List<RiderAchievementEntity>>

    @Query("SELECT * FROM rider_achievements WHERE user_id = :userId ORDER BY earned_at DESC, id ASC")
    suspend fun allFor(userId: Int): List<RiderAchievementEntity>
}
