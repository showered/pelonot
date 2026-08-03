package com.pelonot.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pelonot.data.local.entity.WorkoutMetricEntity
import com.pelonot.domain.model.PowerProvenance

/** Row shape for [WorkoutMetricDao.getPowerProvenanceCounts]. */
data class PowerProvenanceCounts(
    val measured: Int = 0,
    val modelled: Int = 0,
    val unknown: Int = 0
) {
    val provenance: PowerProvenance get() = PowerProvenance.of(measured, modelled, unknown)
}

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
    
    /**
     * How many of a ride's samples were measured, modelled, and neither
     * (recorded before `power_is_measured` existed).
     *
     * Counted rather than stored on `workouts`: a per-ride flag beside the
     * samples it summarises is how `avg_hr` came to be wrong for the whole
     * project history while the samples underneath it were right.
     */
    @Query(
        """
        SELECT
            COALESCE(SUM(power_is_measured = 1), 0) AS measured,
            COALESCE(SUM(power_is_measured = 0), 0) AS modelled,
            COALESCE(SUM(power_is_measured IS NULL), 0) AS unknown
        FROM workout_metrics WHERE workout_id = :workoutId
        """
    )
    suspend fun getPowerProvenanceCounts(workoutId: String): PowerProvenanceCounts

    // Get last metric for a workout (for crash recovery)
    @Query("SELECT * FROM workout_metrics WHERE workout_id = :workoutId ORDER BY timestamp_sec DESC LIMIT 1")
    suspend fun getLastMetricForWorkout(workoutId: String): WorkoutMetricEntity?

    /**
     * The highest heart rate this rider has ever recorded, or null if they have
     * never worn a strap (21.1.3).
     *
     * Offered as a **starting point** when a rider is asked for their maximum,
     * never written for them: the app already holds every sample, and the
     * hardest thirty seconds of their hardest class is a far better opening
     * guess than a formula — but it is still a floor rather than a maximum, and
     * only the rider can say which.
     *
     * Null heart rates are absences, not zeroes, and `MAX` skips them.
     */
    @Query(
        """
        SELECT MAX(m.heart_rate) FROM workout_metrics m
        JOIN workouts w ON w.id = m.workout_id
        WHERE w.user_id = :userId AND m.heart_rate IS NOT NULL
        """
    )
    suspend fun getHighestHeartRate(userId: Int): Int?
}
