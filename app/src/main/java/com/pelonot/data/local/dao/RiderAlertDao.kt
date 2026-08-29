package com.pelonot.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pelonot.data.local.entity.RiderAlertEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RiderAlertDao {

    /**
     * Writes what a ride earned, and quietly drops anything already there.
     *
     * `IGNORE` and never `REPLACE`: REPLACE is a delete plus an insert in
     * SQLite and the delete fires foreign-key actions, which is the bug this
     * project has had three times (`UserDao.insertUser`, `class_templates`).
     * Here the collision is not an error at all — it is the second finalise of
     * one ride arriving at the same conclusion as the first, which is 27.1.1's
     * "never fires twice" doing its job.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(alerts: List<RiderAlertEntity>)

    /** Everything this rider has ever earned, newest first (27.4.1). */
    @Query(
        """
        SELECT * FROM rider_alerts
        WHERE user_id = :userId
        ORDER BY recorded_at DESC, id DESC
        """
    )
    fun observeFor(userId: Int): Flow<List<RiderAlertEntity>>

    /**
     * What one ride earned, ranked by the same order the rules ranked it in.
     *
     * The ordering is by `id` rather than by `kind`, because the rules wrote
     * them in rank order in one insert and the autoincrement preserves it —
     * re-deriving the rank in SQL would mean spelling `AlertKind`'s order out
     * a second time, in a place no test looks.
     */
    @Query("SELECT * FROM rider_alerts WHERE workout_id = :workoutId ORDER BY id ASC")
    suspend fun forWorkout(workoutId: String): List<RiderAlertEntity>

    /** Whether this ride has been judged already, so a resume does not redo it. */
    @Query("SELECT COUNT(*) FROM rider_alerts WHERE workout_id = :workoutId")
    suspend fun countForWorkout(workoutId: String): Int

    @Query("UPDATE rider_alerts SET seen_at = :at WHERE id = :id AND seen_at IS NULL")
    suspend fun markSeen(id: Long, at: Long)

    /** How many the rider has never been shown, for the dashboard's card. */
    @Query("SELECT COUNT(*) FROM rider_alerts WHERE user_id = :userId AND seen_at IS NULL")
    fun observeUnseenCount(userId: Int): Flow<Int>

    /**
     * Everything a profile has earned, for the tests and the backup writer.
     *
     * Not a `Flow`: the two callers want one answer at one moment.
     */
    @Query("SELECT * FROM rider_alerts WHERE user_id = :userId ORDER BY recorded_at DESC")
    suspend fun allFor(userId: Int): List<RiderAlertEntity>
}
