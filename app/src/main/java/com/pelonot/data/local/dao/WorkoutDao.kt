package com.pelonot.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pelonot.data.local.entity.WorkoutEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(workout: WorkoutEntity)

    @Update
    suspend fun updateWorkout(workout: WorkoutEntity)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteWorkout(id: String)

    @Query("UPDATE workouts SET rpe_rating = :rpe WHERE id = :id")
    suspend fun setRpeRating(id: String, rpe: Int)

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getWorkoutById(id: String): WorkoutEntity?

    @Query("SELECT * FROM workouts WHERE user_id = :userId AND is_complete = 1 ORDER BY timestamp DESC")
    fun getWorkoutsByUser(userId: Int): Flow<List<WorkoutEntity>>

    @Query(
        """
        SELECT * FROM workouts
        WHERE user_id = :userId AND is_complete = 1
        ORDER BY timestamp DESC LIMIT 1
        """
    )
    suspend fun getLatestWorkout(userId: Int): WorkoutEntity?

    // ── Leaderboard ─────────────────────────────────────────────────
    // All of these ignore in-progress rides, which would otherwise show a
    // partially-ridden workout as a personal best of 0 kJ.

    @Query(
        """
        SELECT MAX(total_output_kj) FROM workouts
        WHERE user_id = :userId AND is_complete = 1
          AND duration_sec BETWEEN :minDuration AND :maxDuration
        """
    )
    suspend fun getPersonalBestOutput(userId: Int, minDuration: Int, maxDuration: Int): Double?

    @Query(
        """
        SELECT AVG(total_output_kj) FROM workouts
        WHERE user_id = :userId AND is_complete = 1
          AND duration_sec BETWEEN :minDuration AND :maxDuration
        """
    )
    suspend fun getPersonalAverageOutput(userId: Int, minDuration: Int, maxDuration: Int): Double?

    @Query(
        """
        SELECT MAX(total_output_kj) FROM workouts
        WHERE is_complete = 1 AND duration_sec BETWEEN :minDuration AND :maxDuration
        """
    )
    suspend fun getHouseholdBestOutput(minDuration: Int, maxDuration: Int): Double?

    @Query("SELECT MAX(total_output_kj) FROM workouts WHERE user_id = :userId AND is_complete = 1")
    suspend fun getAllTimeBestOutput(userId: Int): Double?

    @Query(
        """
        SELECT * FROM workouts
        WHERE user_id = :userId AND is_complete = 1
        ORDER BY timestamp DESC LIMIT :limit
        """
    )
    suspend fun getRecentWorkouts(userId: Int, limit: Int): List<WorkoutEntity>

    @Query(
        """
        SELECT * FROM workouts
        WHERE class_id = :classId AND user_id = :userId AND is_complete = 1
        ORDER BY timestamp DESC
        """
    )
    fun getWorkoutsByClass(userId: Int, classId: String): Flow<List<WorkoutEntity>>

    /** Total output since [sinceEpochMs], for the dashboard's "today" figure. */
    @Query(
        """
        SELECT COALESCE(SUM(total_output_kj), 0) FROM workouts
        WHERE user_id = :userId AND is_complete = 1 AND timestamp >= :sinceEpochMs
        """
    )
    fun observeOutputSince(userId: Int, sinceEpochMs: Long): Flow<Double>

    @Query(
        """
        SELECT * FROM workouts
        WHERE user_id = :userId AND is_complete = 1
        ORDER BY timestamp DESC LIMIT 1
        """
    )
    fun observeLatestWorkout(userId: Int): Flow<WorkoutEntity?>

    /**
     * The most recent ride that was never finalised — i.e. the app was killed
     * mid-workout. Returns null in the normal case.
     */
    @Query("SELECT * FROM workouts WHERE is_complete = 0 ORDER BY timestamp DESC LIMIT 1")
    suspend fun getIncompleteWorkout(): WorkoutEntity?

    /** Abandons any stale in-progress rides, e.g. after the user declines recovery. */
    @Query("DELETE FROM workouts WHERE is_complete = 0")
    suspend fun deleteIncompleteWorkouts()
}
