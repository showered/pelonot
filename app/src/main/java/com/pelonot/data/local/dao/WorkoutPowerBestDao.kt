package com.pelonot.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.pelonot.data.local.entity.WorkoutPowerBestEntity

/**
 * One stored effort with the ride it came from — see
 * [WorkoutPowerBestDao.bestsFor].
 *
 * The three ride columns travel with the effort because a personal best is
 * useless without them: the screen names the class and the date and opens the
 * ride on a tap.
 */
data class PowerBestRow(
    val windowSec: Int,
    val watts: Double,
    val workoutId: String,
    val recordedAt: Long,
    val classTitle: String?
)

@Dao
interface WorkoutPowerBestDao {

    /**
     * `@Upsert`, never `OnConflictStrategy.REPLACE`.
     *
     * Nothing points at this table today, so REPLACE would not fire a cascade
     * — but this project has had that bug three times and one of them was live,
     * and "nothing points at it *yet*" is how the third one happened.
     */
    @Upsert
    suspend fun upsert(bests: List<WorkoutPowerBestEntity>)

    /** Everything stored for one ride, before it is recomputed. */
    @Query("DELETE FROM workout_power_bests WHERE workout_id = :workoutId")
    suspend fun clearFor(workoutId: String)

    /**
     * Every stored effort of this rider's, newest ride first (16.3.3a).
     *
     * One query for the whole history, where the old shape was one query per
     * ride plus a full sample scan of each. The ordering carries the tie-break:
     * on two rides with exactly the same watts over a window, the reduction
     * keeps the first it sees, which was the most recent ride before this
     * change and stays the most recent ride after it.
     *
     * No provenance clause, and that is the point of the column beside it.
     * `workouts.power_bests_at` is only ever set for a ride whose watts the
     * board measured, so **the existence of these rows is itself the claim** —
     * which is what keeps the answer true after 23.4 has taken the samples the
     * old gate was computed from.
     */
    @Query(
        """
        SELECT b.window_sec AS windowSec,
               b.watts AS watts,
               w.id AS workoutId,
               w.timestamp AS recordedAt,
               c.title AS classTitle
        FROM workout_power_bests b
        JOIN workouts w ON w.id = b.workout_id
        LEFT JOIN class_templates c ON c.id = w.class_id
        WHERE w.user_id = :userId
          AND w.is_complete = 1
        ORDER BY w.timestamp DESC
        """
    )
    suspend fun bestsFor(userId: Int): List<PowerBestRow>

    /**
     * The best this rider had held at each window before one ride (27.1.7).
     *
     * **Read from here and never re-scanned from samples**, which is the whole
     * of 27.1.7: 23.4 condenses old rides, so a scan would watch a record fall
     * because the ride holding it had been trimmed — and would then congratulate
     * the rider for beating it. A stored best survives the trim; the samples it
     * came from do not.
     *
     * No provenance clause, for `bestsFor`'s reason: a row only exists for a
     * ride whose watts the board measured, so the existence of these rows is
     * itself the claim.
     */
    @Query(
        """
        SELECT b.window_sec AS window_sec, MAX(b.watts) AS watts
        FROM workout_power_bests b
        JOIN workouts w ON w.id = b.workout_id
        WHERE w.user_id = :userId
          AND w.is_complete = 1
          AND w.id != :excludingWorkoutId
        GROUP BY b.window_sec
        """
    )
    suspend fun bestsBefore(userId: Int, excludingWorkoutId: String): List<WindowBestRow>

    /** What one ride held, for the same comparison from the other side. */
    @Query(
        """
        SELECT window_sec AS window_sec, watts AS watts
        FROM workout_power_bests WHERE workout_id = :workoutId
        """
    )
    suspend fun bestsOf(workoutId: String): List<WindowBestRow>
}
