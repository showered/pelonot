package com.pelonot.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pelonot.data.local.entity.WorkoutEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(workout: WorkoutEntity)

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getWorkoutById(id: String): WorkoutEntity?

    @Query("SELECT * FROM workouts WHERE user_id = :userId ORDER BY timestamp DESC")
    fun getWorkoutsByUser(userId: Int): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE user_id = :userId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestWorkout(userId: Int): WorkoutEntity?

    // Personal Best for a given duration
    @Query("""
        SELECT MAX(total_output_kj) FROM workouts 
        WHERE user_id = :userId AND duration_sec BETWEEN :minDuration AND :maxDuration
    """)
    suspend fun getPersonalBestOutput(userId: Int, minDuration: Int, maxDuration: Int): Double?

    // Personal Average for a given duration
    @Query("""
        SELECT AVG(total_output_kj) FROM workouts 
        WHERE user_id = :userId AND duration_sec BETWEEN :minDuration AND :maxDuration
    """)
    suspend fun getPersonalAverageOutput(userId: Int, minDuration: Int, maxDuration: Int): Double?

    // Household Best for a given duration
    @Query("""
        SELECT MAX(total_output_kj) FROM workouts 
        WHERE duration_sec BETWEEN :minDuration AND :maxDuration
    """)
    suspend fun getHouseholdBestOutput(minDuration: Int, maxDuration: Int): Double?

    // All-time best regardless of duration
    @Query("SELECT MAX(total_output_kj) FROM workouts WHERE user_id = :userId")
    suspend fun getAllTimeBestOutput(userId: Int): Double?

    // Last N workouts for FTP analysis
    @Query("SELECT * FROM workouts WHERE user_id = :userId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentWorkouts(userId: Int, limit: Int): List<WorkoutEntity>

    // Workouts for a specific class
    @Query("SELECT * FROM workouts WHERE class_id = :classId AND user_id = :userId ORDER BY timestamp DESC")
    fun getWorkoutsByClass(userId: Int, classId: String): Flow<List<WorkoutEntity>>
}