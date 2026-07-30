package com.pelonot.data.repository

import com.pelonot.data.local.dao.WorkoutDao
import com.pelonot.data.local.dao.WorkoutMetricDao
import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.data.local.entity.WorkoutMetricEntity
import kotlinx.coroutines.flow.Flow

/** Leaderboard figures for a ride of a given length. */
data class LeaderboardStats(
    val personalBestKj: Double? = null,
    val personalAverageKj: Double? = null,
    val householdBestKj: Double? = null
)

class WorkoutRepository(
    private val workoutDao: WorkoutDao,
    private val metricDao: WorkoutMetricDao
) {

    fun observeWorkouts(userId: Int): Flow<List<WorkoutEntity>> =
        workoutDao.getWorkoutsByUser(userId)

    suspend fun getWorkout(id: String): WorkoutEntity? = workoutDao.getWorkoutById(id)

    suspend fun getMetrics(workoutId: String): List<WorkoutMetricEntity> =
        metricDao.getMetricsForWorkout(workoutId)

    /**
     * Writes the workout row at the *start* of a ride so that per-second
     * metrics have a parent to reference. See [WorkoutEntity.isComplete].
     */
    suspend fun beginWorkout(workout: WorkoutEntity) =
        workoutDao.insertWorkout(workout.copy(isComplete = false))

    suspend fun finaliseWorkout(workout: WorkoutEntity) =
        workoutDao.updateWorkout(workout.copy(isComplete = true))

    suspend fun recordMetrics(metrics: List<WorkoutMetricEntity>) {
        if (metrics.isEmpty()) return
        metricDao.insertMetrics(metrics)
    }

    suspend fun setRpe(workoutId: String, rpe: Int) = workoutDao.setRpeRating(workoutId, rpe)

    /** Removes a ride and, by cascade, its metrics. Used for "discard". */
    suspend fun discardWorkout(workoutId: String) = workoutDao.deleteWorkout(workoutId)

    suspend fun findRecoverableWorkout(): WorkoutEntity? = workoutDao.getIncompleteWorkout()

    suspend fun clearRecoverableWorkouts() = workoutDao.deleteIncompleteWorkouts()

    suspend fun getRecentWorkouts(userId: Int, limit: Int): List<WorkoutEntity> =
        workoutDao.getRecentWorkouts(userId, limit)

    /**
     * PB / average / household best for rides of comparable length.
     * The window is ±10% of [durationSec] so a 30-minute ride is not compared
     * against a 90-minute one.
     */
    suspend fun leaderboardFor(userId: Int, durationSec: Int): LeaderboardStats {
        val tolerance = (durationSec * DURATION_TOLERANCE).toInt()
        val min = durationSec - tolerance
        val max = durationSec + tolerance
        return LeaderboardStats(
            personalBestKj = workoutDao.getPersonalBestOutput(userId, min, max),
            personalAverageKj = workoutDao.getPersonalAverageOutput(userId, min, max),
            householdBestKj = workoutDao.getHouseholdBestOutput(min, max)
        )
    }

    private companion object {
        const val DURATION_TOLERANCE = 0.10
    }
}
