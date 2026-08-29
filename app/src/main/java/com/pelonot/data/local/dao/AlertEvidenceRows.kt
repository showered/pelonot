package com.pelonot.data.local.dao

import androidx.room.ColumnInfo

/**
 * What this rider's history looked like **before** one ride (PLAN 27.2.1) —
 * see [WorkoutDao.historyBefore].
 *
 * One row of aggregates rather than four queries, because all four are read at
 * the same moment by the same caller and three of them share a predicate. The
 * exclusion is the load-bearing part: by the time the detector runs, the ride
 * being judged is already `is_complete = 1`, so a query that did not leave it
 * out would find it as its own record to beat.
 */
data class HistoryBeforeRow(
    /** Completed rides, whatever their provenance — the floor for a duration record. */
    @ColumnInfo(name = "rides") val rides: Int,
    /** Of those, the ones whose watts the board measured all the way through. */
    @ColumnInfo(name = "measured_rides") val measuredRides: Int,
    /** The most output in any single measured ride, or null if there were none. */
    @ColumnInfo(name = "best_output_kj") val bestOutputKj: Double?,
    /** The longest ride, in seconds, or null. */
    @ColumnInfo(name = "longest_sec") val longestSec: Int?
)

/**
 * The same, narrowed to one class (PLAN 27.2.1) — see
 * [WorkoutDao.classHistoryBefore].
 *
 * Measured rides only, because the number being compared is total output and
 * output is watts integrated: a modelled ride's is a fiction, and a record set
 * against one would be a congratulation for `PowerModel` (27.1.2).
 */
data class ClassHistoryBeforeRow(
    @ColumnInfo(name = "rides") val rides: Int,
    @ColumnInfo(name = "best_output_kj") val bestOutputKj: Double?
)

/** One stretch and the best watts held over it — see [WorkoutPowerBestDao]. */
data class WindowBestRow(
    @ColumnInfo(name = "window_sec") val windowSec: Int,
    @ColumnInfo(name = "watts") val watts: Double
)
