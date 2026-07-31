package com.pelonot.data.repository

import com.pelonot.data.local.dao.WorkoutDao
import com.pelonot.data.local.dao.WorkoutListItem
import com.pelonot.data.local.dao.WorkoutMetricDao
import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.data.local.entity.WorkoutMetricEntity
import com.pelonot.data.service.RideInProgress
import com.pelonot.domain.model.MetricSample
import com.pelonot.domain.model.WorkoutAggregates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.Calendar

/**
 * Headline figures for the dashboard.
 *
 * These replace hardcoded literals — "12.5" kJ today, "8.3" kJ last ride and a
 * permanent "FTP Stable" badge — which were shown to the rider as their own
 * statistics on a device that had never recorded a single workout.
 */
data class DashboardStats(
    val todayOutputKj: Double = 0.0,
    val lastRide: WorkoutEntity? = null
) {
    val hasRidden: Boolean get() = lastRide != null
}

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

    /** Ride history, newest first, limited to [limit] rows. See [WorkoutListItem]. */
    fun observeHistory(userId: Int, limit: Int): Flow<List<WorkoutListItem>> =
        workoutDao.observeHistory(userId, limit)

    fun observeCompletedCount(userId: Int): Flow<Int> =
        workoutDao.observeCompletedCount(userId)

    /**
     * Headline figures for the dashboard.
     *
     * "Today" is measured from local midnight rather than a rolling 24 hours,
     * because a rider comparing this morning's ride against yesterday's
     * expects the number to reset overnight.
     */
    fun observeDashboardStats(userId: Int): Flow<DashboardStats> = combine(
        workoutDao.observeOutputSince(userId, startOfToday()),
        workoutDao.observeLatestWorkout(userId)
    ) { todayKj, latest ->
        DashboardStats(
            todayOutputKj = todayKj,
            lastRide = latest
        )
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

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

    /** Moves a guest ride onto a profile once the rider says whose it was. */
    suspend fun assignToUser(workoutId: String, userId: Int) =
        workoutDao.assignWorkoutToUser(workoutId, userId)

    /**
     * Removes a ride and, by cascade, its metric series.
     *
     * There is no undo below this line. `workout_metrics` has
     * `ON DELETE CASCADE`, so the per-second record goes with the row and
     * cannot be reconstructed from the aggregates. Anything offering the rider
     * an undo has to hold the delete back rather than reverse it — see
     * `HistoryViewModel`.
     */
    suspend fun discardWorkout(workoutId: String) = workoutDao.deleteWorkout(workoutId)

    /**
     * A ride the app was killed in the middle of, or null.
     *
     * The exclusion is the whole point (8.3b). A ride in progress is
     * `is_complete = 0` — that ordering is what lets `workout_metrics`
     * reference it at all (1.12) — so without it this returns the ride the
     * rider is on, and the app offers to discard the class they are currently
     * pedalling. The guard lives here rather than at each call site because
     * there are three of them and forgetting it is silent.
     */
    suspend fun findRecoverableWorkout(): WorkoutEntity? =
        workoutDao.getIncompleteWorkout(excludingId = RideInProgress.workoutId)

    suspend fun clearRecoverableWorkouts() =
        workoutDao.deleteIncompleteWorkouts(excludingId = RideInProgress.workoutId)

    /**
     * Finalises a ride the app was killed in the middle of.
     *
     * The `workouts` row was written at ride start with zeroed totals — that
     * ordering is what lets `workout_metrics` reference it at all — so the
     * aggregates have to be rebuilt from the samples that did land. Returns the
     * completed row, or null if there was nothing worth keeping.
     */
    suspend fun recoverWorkout(workoutId: String): WorkoutEntity? {
        val workout = workoutDao.getWorkoutById(workoutId) ?: return null
        val metrics = metricDao.getMetricsForWorkout(workoutId)

        val aggregates = WorkoutAggregates.from(
            metrics.map {
                MetricSample(
                    second = it.timestampSec,
                    power = it.power,
                    cadence = it.cadence,
                    heartRate = it.heartRate
                )
            }
        )

        if (aggregates.isEmpty) {
            // A ride that recorded nothing is not a ride.
            workoutDao.deleteWorkout(workoutId)
            return null
        }

        val recovered = workout.copy(
            durationSec = aggregates.durationSec,
            totalOutputKj = aggregates.totalOutputKj,
            totalDistanceKm = aggregates.distanceKm,
            avgPower = aggregates.avgPower,
            avgCadence = aggregates.avgCadence,
            avgHr = aggregates.avgHeartRate?.toDouble(),
            isComplete = true,
            wasRecovered = true
        )
        workoutDao.updateWorkout(recovered)
        return recovered
    }

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
