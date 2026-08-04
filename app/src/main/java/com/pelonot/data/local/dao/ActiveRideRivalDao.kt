package com.pelonot.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pelonot.data.local.entity.ActiveRideRivalEntity

/** The live ghost's chosen rival, keyed by the ride racing it (PLAN 24.3.8). */
@Dao
interface ActiveRideRivalDao {

    /**
     * `REPLACE` is safe here and nowhere else in this project (see CLAUDE.md):
     * the primary key is `workout_id`, which is never referenced by a third
     * table, so the delete-plus-insert REPLACE performs has no foreign-key
     * action to fire. A ride is only ever set once, but idempotency costs
     * nothing.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(entry: ActiveRideRivalEntity)

    @Query("SELECT * FROM active_ride_rival WHERE workout_id = :workoutId")
    suspend fun get(workoutId: String): ActiveRideRivalEntity?

    @Query("DELETE FROM active_ride_rival WHERE workout_id = :workoutId")
    suspend fun clear(workoutId: String)
}
