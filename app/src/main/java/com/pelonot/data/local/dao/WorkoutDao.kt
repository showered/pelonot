package com.pelonot.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.pelonot.data.local.entity.WorkoutEntity
import kotlinx.coroutines.flow.Flow

/** One rider's week on the dashboard's household panel — see [WorkoutDao.householdWeek]. */
data class HouseholdWeekRow(
    val localUserId: Int,
    val name: String,
    val rides: Int,
    val outputKj: Double,
    val lastRideAt: Long
)

/** One rider's place on a class's household board — see [WorkoutDao.householdLeaderboard]. */
data class HouseholdLeaderboardRow(
    val localUserId: Int,
    val name: String,
    val weightKg: Double,
    val bestOutputKj: Double
)

/** A housemate's ride of the same class, ready to draw behind yours (24.3.1). */
data class HouseholdRivalRow(
    val localUserId: Int,
    val name: String,
    val workoutId: String,
    val outputKj: Double,
    val durationSec: Int
)

@Dao
interface WorkoutDao {

    /**
     * `@Upsert` rather than `@Insert(onConflict = REPLACE)`, for the reason
     * spelled out on `UserDao.insertUser`: REPLACE is a delete plus an insert
     * and the delete fires foreign-key actions. `workout_metrics.workout_id` is
     * `ON DELETE CASCADE`, so re-inserting a workout row would take its whole
     * time series with it.
     *
     * That has never happened, because a ride is inserted once at the start
     * with a fresh id and finalised through `@Update`. It is a loaded gun
     * pointing at the one table in this app that cannot be regenerated, and
     * there is no reason to keep it loaded.
     */
    @Upsert
    suspend fun insertWorkout(workout: WorkoutEntity)

    @Update
    suspend fun updateWorkout(workout: WorkoutEntity)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteWorkout(id: String)

    @Query("UPDATE workouts SET rpe_rating = :rpe WHERE id = :id")
    suspend fun setRpeRating(id: String, rpe: Int)

    /**
     * Records that the rider declined a breakthrough off this ride (7.10.5).
     *
     * One-way on purpose: nothing clears it. Accepting later is done by typing
     * the number in Settings, which is a different act with a different reason
     * on it, and re-offering a proposal the rider has refused is exactly what
     * this exists to stop.
     */
    @Query("UPDATE workouts SET ftp_proposal_declined = 1 WHERE id = :id")
    suspend fun declineFtpProposal(id: String)

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

    // ── Household leaderboard (24.1) ────────────────────────────────
    // Everyone with a profile on this tablet, ranked on one class. No network,
    // no account, no RLS — and the fairest comparison this app can make, since
    // both sides came off the same board and the same knob.

    /**
     * Each rider's **best** ride of one class, best first.
     *
     * Three exclusions, each of which is a rule rather than a filter:
     *
     * - `user_id IS NOT NULL` via the join — a guest ride has no owner, so
     *   there is nobody to put on the board (24.1.4).
     * - a ride with no samples at all cannot be ranked on work it has no
     *   evidence of.
     * - **any sample that is not a measurement disqualifies the ride**
     *   (24.4.2). A simulated ride's watts are `PowerModel`'s output, which
     *   scores RMSE 137 W against the real board; putting one beside a
     *   measured ride would be ranking a rider against a number the app made
     *   up. `NULL` counts as not-a-measurement for the same reason it does
     *   everywhere else: nobody wrote it down, so it cannot be shown to be one.
     *
     * One row per rider rather than per ride: a leaderboard listing somebody's
     * six attempts is a personal history, not a comparison.
     */
    @Query(
        """
        SELECT p.local_user_id AS localUserId,
               p.name AS name,
               p.weight_kg AS weightKg,
               MAX(w.total_output_kj) AS bestOutputKj
        FROM workouts w
        JOIN profiles p ON p.local_user_id = w.user_id
        WHERE w.class_id = :classId
          AND w.is_complete = 1
          AND p.household_visible = 1
          AND EXISTS (SELECT 1 FROM workout_metrics m WHERE m.workout_id = w.id)
          AND NOT EXISTS (
              SELECT 1 FROM workout_metrics m
              WHERE m.workout_id = w.id
                AND (m.power_is_measured IS NULL OR m.power_is_measured = 0)
          )
        GROUP BY p.local_user_id
        ORDER BY bestOutputKj DESC
        """
    )
    suspend fun householdLeaderboard(classId: String): List<HouseholdLeaderboardRow>

    /**
     * The same riders and the same rules, but carrying the **ride** rather than
     * the number — because 24.3.1 draws their trace, and a trace needs a
     * workout id to fetch samples for.
     *
     * Deliberately the same `WHERE` clause as [householdLeaderboard], down to
     * the measured-power exclusion. Drawing a modelled trace over a measured
     * one is the same lie as ranking them against each other, and a worse one
     * for being drawn to scale: `PowerModel` scores RMSE 137 W against the real
     * board, which on a chart is most of the height of a zone.
     *
     * `MAX(w.total_output_kj)` with the other columns is SQLite's bare-column
     * form: with an aggregate over a `GROUP BY`, the non-aggregated columns are
     * taken from **the row the max came from**, which is precisely the ride
     * wanted. That is a documented SQLite guarantee and not a portable one.
     */
    @Query(
        """
        SELECT p.local_user_id AS localUserId,
               p.name AS name,
               w.id AS workoutId,
               MAX(w.total_output_kj) AS outputKj,
               w.duration_sec AS durationSec
        FROM workouts w
        JOIN profiles p ON p.local_user_id = w.user_id
        WHERE w.class_id = :classId
          AND w.id != :excludingWorkoutId
          AND w.user_id != :excludingUserId
          AND w.is_complete = 1
          AND p.household_visible = 1
          AND EXISTS (SELECT 1 FROM workout_metrics m WHERE m.workout_id = w.id)
          AND NOT EXISTS (
              SELECT 1 FROM workout_metrics m
              WHERE m.workout_id = w.id
                AND (m.power_is_measured IS NULL OR m.power_is_measured = 0)
          )
        GROUP BY p.local_user_id
        ORDER BY outputKj DESC
        """
    )
    suspend fun householdRivals(
        classId: String,
        excludingWorkoutId: String,
        excludingUserId: Int
    ): List<HouseholdRivalRow>

    /**
     * Who has ridden since [sinceMs], one row per rider (PLAN 24.2.1).
     *
     * **An inner join, deliberately.** A rider with no rides in the window is
     * absent from the result rather than present with a zero — which is 24.2.4
     * ("no nudging one household member about another") made structural instead
     * of remembered. There is no row here that could ever be rendered as
     * "Sam hasn't ridden this week", because the row does not exist.
     *
     * `household_visible` is the per-profile opt-out (24.2.3). It gates this
     * and the per-class board above by the same column, because a rider who
     * opts out of being seen has not asked to opt out of half of it.
     *
     * Unlike the per-class board this does **not** exclude modelled power: it
     * counts rides and kilojoules rather than ranking one rider's watts against
     * another's, so 24.4.2's argument does not apply. Presence is not a claim
     * about accuracy.
     */
    @Query(
        """
        SELECT p.local_user_id AS localUserId,
               p.name AS name,
               COUNT(w.id) AS rides,
               COALESCE(SUM(w.total_output_kj), 0) AS outputKj,
               MAX(w.timestamp) AS lastRideAt
        FROM profiles p
        JOIN workouts w ON w.user_id = p.local_user_id
        WHERE p.household_visible = 1
          AND w.is_complete = 1
          AND w.timestamp >= :sinceMs
        GROUP BY p.local_user_id
        ORDER BY rides DESC, outputKj DESC
        """
    )
    suspend fun householdWeek(sinceMs: Long): List<HouseholdWeekRow>

    /**
     * A change signal for the household panel, not a number anyone displays.
     *
     * **It joins `profiles` on purpose.** Room invalidates a query when any
     * table it *mentions* is written, so mentioning both is what makes the
     * dashboard's household week reload for both of the things that can change
     * it: a housemate finishing a ride, and anybody turning
     * `household_visible` off. The first version selected from `workouts`
     * alone, and opting out left the rider sitting on the panel until the next
     * ride — observed on the AVD, which is the only way that class of bug ever
     * shows up.
     *
     * Every other dashboard flow is scoped to one profile and would notice
     * neither.
     */
    @Query(
        """
        SELECT COUNT(*) FROM workouts w
        JOIN profiles p ON p.local_user_id = w.user_id
        WHERE w.is_complete = 1
        """
    )
    fun observeAnyCompletedCount(): Flow<Int>

    /**
     * Completed rides recorded since a moment, **on this tablet and by anybody**
     * (23.3.1).
     *
     * Not scoped to a profile, and not joined to one, because the thing it
     * counts is what a backup would protect: the backup file is the whole
     * database, so a housemate's rides are as much at stake as the current
     * rider's — and a guest ride, which has no profile at all, is in the file
     * too and would be lost with it.
     */
    @Query(
        """
        SELECT COUNT(*) FROM workouts
        WHERE is_complete = 1 AND timestamp > :sinceEpochMs
        """
    )
    fun observeCompletedSince(sinceEpochMs: Long): Flow<Int>

    /**
     * Ride timestamps per rider, for [com.pelonot.domain.social.StreakCalculator].
     *
     * The streak arithmetic is not done in SQL: "consecutive local calendar
     * days" is exactly the kind of date logic that goes wrong twice a year and
     * cannot be tested where it lives. It comes out as timestamps and is
     * counted in a pure object with the clock and the timezone injected.
     */
    @Query(
        """
        SELECT w.timestamp FROM workouts w
        WHERE w.user_id = :userId AND w.is_complete = 1 AND w.timestamp >= :sinceMs
        ORDER BY w.timestamp DESC
        """
    )
    suspend fun rideTimestampsSince(userId: Int, sinceMs: Long): List<Long>

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

    /**
     * Every class id some ride still points at.
     *
     * `ClassTemplateSeeder` asks this before it removes a class the bundled
     * library no longer contains: one nobody has ridden can simply go, and one
     * somebody has ridden must stay as a retired row, or the foreign key's
     * `SET NULL` turns their ride into a ride of nothing (23.2.6c).
     */
    @Query("SELECT DISTINCT class_id FROM workouts WHERE class_id IS NOT NULL")
    suspend fun referencedClassIds(): List<String>

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
