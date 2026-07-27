package com.pelonot.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pelonot.data.local.entity.WorkoutMetricEntity

@Dao
interface WorkoutMetricDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetric(metric: WorkoutMetricEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetrics(metrics: List<WorkoutMetricEntity>)

    @Query("SELECT * FROM workout_metrics WHERE workout_id = :workoutId ORDER BY timestamp_sec ASC")
    suspend fun getMetricsForWorkout(workoutId: String): List<WorkoutMetricEntity>

    @Query("SELECT * FROM workout_metrics WHERE workout_id = :workoutId ORDER BY timestamp_sec ASC")
    fun getMetricsForWorkoutFlow(workoutId: String): kotlinx.coroutines.flow.Flow<List<WorkoutMetricEntity>>

    @Query("DELETE FROM workout_metrics WHERE workout_id = :workoutId")
    suspend fun deleteMetricsForWorkout(workoutId: String)

    @Query("SELECT COUNT(*) FROM workout_metrics WHERE workout_id = :workoutId")
    suspend fun getMetricCountForWorkout(workoutId: String): Int

    // For 20-min peak power FTP calculation
    @Query("""
        SELECT power FROM workout_metrics 
        WHERE workout_id = :workoutId 
        ORDER BY timestamp_sec ASC
    """)
    suspend fun getPowerTimeSeries(workoutId: String): List<Double>
    
    // Get last metric for a workout (for crash recovery)
    @Query("SELECT * FROM workout_metrics WHERE workout_id = :workoutId ORDER BY timestamp_sec DESC LIMIT 1")
    suspend fun getLastMetricForWorkout(workoutId: String): WorkoutMetricEntity?
}
