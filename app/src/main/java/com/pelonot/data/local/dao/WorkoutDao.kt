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

    /**
     * Assigns a guest ride to a profile after the fact.
     *
     * A guest ride is recorded with a null `user_id`, and the rider is asked
     * afterwards whether to keep it and against whom. Rewriting the owner is
     * cheaper and safer than re-recording the ride under a new id, which would
     * orphan every `workout_metrics` row pointing at the old one.
     */
    @Query("UPDATE workouts SET user_id = :userId WHERE id = :id")
    suspend fun assignWorkoutToUser(id: String, userId: Int)

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getWorkoutById(id: String): WorkoutEntity?

    @Query("SELECT * FROM workouts WHERE user_id = :userId AND is_complete = 1 ORDER BY timestamp DESC")
    fun getWorkoutsByUser(userId: Int): Flow<List<WorkoutEntity>>

    /**
     * Ride history, newest first, windowed.
     *
     * `is_complete = 1` is not decoration: an unfinished row is a crashed or
     * in-flight ride with zeroed totals, and showing it as history would put a
     * 0 kJ entry at the top of the list every time the app is killed mid-ride
     * (12.1.4).
     *
     * `LIMIT` rather than paging: the screen asks for one window and raises it
     * when the rider reaches the bottom. A `Flow` over the whole table would
     * re-read every ride the rider has ever done on each insert.
     */
    @Query(
        """
        SELECT w.id AS id,
               c.title AS class_title,
               w.duration_sec AS duration_sec,
               w.total_output_kj AS total_output_kj,
               w.total_distance_km AS total_distance_km,
               w.avg_power AS avg_power,
               w.rpe_rating AS rpe_rating,
               w.was_recovered AS was_recovered,
               w.timestamp AS timestamp
        FROM workouts w
        LEFT JOIN class_templates c ON c.id = w.class_id
        WHERE w.user_id = :userId AND w.is_complete = 1
        ORDER BY w.timestamp DESC
        LIMIT :limit
        """
    )
    fun observeHistory(userId: Int, limit: Int): Flow<List<WorkoutListItem>>

    /** How many rides exist, so the screen knows whether the window is full. */
    @Query("SELECT COUNT(*) FROM workouts WHERE user_id = :userId AND is_complete = 1")
    fun observeCompletedCount(userId: Int): Flow<Int>

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
     *
     * [excludingId] is the ride being recorded right now, which is also
     * `is_complete = 0` and is emphatically not a crash artifact (8.3b). Room
     * cannot express "skip nothing" with a null parameter and an inequality, so
     * the null case is spelled out in the predicate rather than left to
     * `id != NULL`, which is never true and would return no rows at all.
     */
    @Query(
        """
        SELECT * FROM workouts
        WHERE is_complete = 0 AND (:excludingId IS NULL OR id != :excludingId)
        ORDER BY timestamp DESC LIMIT 1
        """
    )
    suspend fun getIncompleteWorkout(excludingId: String?): WorkoutEntity?

    /**
     * Abandons stale in-progress rides, e.g. after the rider declines recovery.
     *
     * This used to be an unqualified `DELETE FROM workouts WHERE is_complete = 0`,
     * which took the live ride with it — see 8.3b. It keeps the bulk form
     * because a device that has crashed twice has two orphaned rides and the
     * rider should not be asked twice, but it can no longer reach the one ride
     * that is not orphaned.
     */
    @Query("DELETE FROM workouts WHERE is_complete = 0 AND (:excludingId IS NULL OR id != :excludingId)")
    suspend fun deleteIncompleteWorkouts(excludingId: String?)
}
