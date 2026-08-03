package com.pelonot.data.repository

import com.pelonot.data.local.dao.HouseholdRivalRow
import com.pelonot.data.local.dao.WorkoutDao
import com.pelonot.data.local.dao.WorkoutListItem
import com.pelonot.data.local.dao.WorkoutMetricDao
import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.data.local.entity.WorkoutMetricEntity
import com.pelonot.data.local.dao.PreviousBestRow
import com.pelonot.data.local.dao.SyncBacklog
import com.pelonot.data.service.RideInProgress
import com.pelonot.domain.model.ClassLeaderboard
import com.pelonot.domain.model.MetricSample
import com.pelonot.domain.model.RideInterruption
import com.pelonot.domain.model.WorkoutAggregates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import com.pelonot.domain.social.HouseholdRiderWeek
import com.pelonot.domain.progress.MeanMaximalPower
import com.pelonot.domain.progress.PersonalBest
import com.pelonot.domain.progress.PersonalBests
import com.pelonot.domain.progress.PowerSample
import com.pelonot.domain.progress.RideRecord
import com.pelonot.domain.progress.RidingHistory
import com.pelonot.domain.progress.RidingHistoryBuilder
import com.pelonot.domain.social.StreakCalculator
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

/**
 * An interrupted ride reopened, and everything it had already banked (8.3d).
 *
 * The row and the aggregates travel together because the service needs both and
 * they must describe the same instant: the row carries the identity the ride
 * must keep — its id, its class, and **the FTP it was started at**, which has
 * to come from here rather than from the rider's profile or a breakthrough
 * accepted in between would silently rescore the ride (7.8).
 */
data class ResumedRide(
    val workout: WorkoutEntity,
    val aggregates: WorkoutAggregates
)

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
     * The highest heart rate this rider has ever recorded, or null (21.1.3).
     *
     * Offered as an opening guess when Settings asks for their maximum. It is a
     * **floor, not a maximum** — the hardest thirty seconds they have ridden so
     * far — which is why it is offered and never written for them.
     */
    suspend fun highestHeartRate(userId: Int): Int? =
        metricDao.getHighestHeartRate(userId)

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

    /** The rider said no to a breakthrough off this ride (7.10.5). */
    suspend fun declineFtpProposal(workoutId: String) = workoutDao.declineFtpProposal(workoutId)

    /** The cloud has this ride now (14.2.4). */
    suspend fun markSynced(workoutId: String, at: Long = System.currentTimeMillis()) =
        workoutDao.markSynced(workoutId, at)

    /**
     * Rides this profile has that the cloud has not, oldest first (14.2.5,
     * 14.2.6).
     *
     * Capped rather than unbounded. A first-sign-in backfill over a year of
     * daily riding is 300-odd rides at ~55 KB each, and loading every entity
     * before uploading any of them is both a memory spike and an all-or-nothing
     * unit of work. The drain runs in batches and comes back for the next one.
     */
    suspend fun unsyncedWorkouts(userId: Int, limit: Int = SYNC_BATCH): List<WorkoutEntity> =
        workoutDao.unsyncedWorkouts(userId, limit)

    /** How far behind this rider's backup is, for Settings to say (14.2.3). */
    fun observeBacklog(userId: Int): Flow<SyncBacklog> = workoutDao.observeBacklog(userId)

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

    /**
     * How long [workoutId] has been interrupted for, or null if it is not a
     * ride that can be asked (8.3d).
     *
     * Reads the last sample rather than the row's `duration_sec`, because the
     * row of an interrupted ride was written at ride start with zeroed totals —
     * that ordering is what lets `workout_metrics` reference it at all (1.12) —
     * so the row does not know how far the ride got. The samples do.
     */
    suspend fun interruptionFor(
        workoutId: String,
        nowEpochMs: Long = System.currentTimeMillis()
    ): RideInterruption? {
        val workout = workoutDao.getWorkoutById(workoutId) ?: return null
        val lastSample = metricDao.getLastMetricForWorkout(workoutId)
        return RideInterruption.between(
            startedAtEpochMs = workout.timestamp,
            lastRecordedSec = lastSample?.timestampSec ?: 0,
            nowEpochMs = nowEpochMs
        )
    }

    /**
     * Reopens an interrupted ride so the service can carry on recording it
     * (8.3d).
     *
     * Deliberately **not** an insert. The `workouts` row already exists and
     * `workout_metrics` points at it; re-inserting would either violate the
     * primary key or, with the wrong conflict strategy, delete the row and take
     * the whole metric series with it by cascade — which is the REPLACE trap
     * that has already cost this project three tables and one live defect.
     *
     * Records the interruption on the way past (8.3d.2) and returns the totals
     * the ride had banked, or null if there is nothing to resume.
     */
    suspend fun resumeInterruptedWorkout(
        workoutId: String,
        interruption: RideInterruption
    ): ResumedRide? {
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

        // Same rule as the keep path: a ride that recorded nothing is not a
        // ride. Unlike that path this one does not delete it — the rider has
        // asked to carry on, and answering by silently binning their ride is
        // worse than declining to.
        if (aggregates.isEmpty) return null

        val reopened = workout.copy(
            resumeCount = workout.resumeCount + 1,
            interruptedSec = workout.interruptedSec + interruption.unrecordedSec
        )
        workoutDao.updateWorkout(reopened)

        return ResumedRide(workout = reopened, aggregates = aggregates)
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

    /**
     * The household's board for one class (24.1).
     *
     * Room does the exclusions — guests, rides with no samples, rides whose
     * watts were not measured — and the ranking rule lives on
     * [ClassLeaderboard] rather than in the `ORDER BY`, so it can be
     * tested without a database.
     */
    suspend fun householdLeaderboard(classId: String, youId: Int?): ClassLeaderboard =
        ClassLeaderboard.of(
            classId = classId,
            standings = householdStandings(classId),
            youId = youId
        )

    /**
     * The household **and** everyone else registered, on one board (PLAN 18.5,
     * 18.9).
     *
     * 18.9's rule, applied rather than quoted: this is the household board with
     * more rows, not a second leaderboard beside it. One type, one ranking, one
     * renderer — because two implementations of a leaderboard drift, and it is
     * always the one nobody rides against that gets left behind.
     *
     * **The household half never touches the network.** It is the same Room
     * query as before and it answers on a tablet in a garage with no wifi;
     * the cloud half is added when it can be. So the failure mode is a board
     * that is *shorter* than it could be, never a board that is missing.
     *
     * @param yourAccountId lets a cloud row be recognised as the rider's own,
     *   which matters on a second bike where their local profile id is
     *   different (14.2.1's whole argument).
     */
    suspend fun classLeaderboard(
        classId: String,
        youId: Int?,
        yourAccountId: String?,
        cloudStandings: suspend () -> List<ClassLeaderboard.Standing>
    ): ClassLeaderboard = ClassLeaderboard.of(
        classId = classId,
        standings = householdStandings(classId) + cloudStandings(),
        youId = youId,
        yourAccountId = yourAccountId
    )

    private suspend fun householdStandings(classId: String) =
        workoutDao.householdLeaderboard(classId).map { row ->
            ClassLeaderboard.Standing(
                localUserId = row.localUserId,
                accountId = row.authUserId,
                name = row.name,
                outputKj = row.bestOutputKj,
                weightKg = row.weightKg,
                source = ClassLeaderboard.Source.Household
            )
        }

    /**
     * The housemates whose ride of this class can be drawn behind yours
     * (24.3.1).
     *
     * Best ride per rider, best first, and **empty is the common answer** — a
     * household of one, a class nobody else has ridden, or a household where
     * the other rides were simulated. Nothing on the screen may imply otherwise
     * (24.1.6's rule: a household of one draws no card at all).
     */
    suspend fun householdRivals(
        classId: String,
        excludingWorkoutId: String,
        excludingUserId: Int
    ): List<HouseholdRivalRow> =
        workoutDao.householdRivals(classId, excludingWorkoutId, excludingUserId)

    /**
     * The rider's personal bests by duration (16.3.3).
     *
     * One ride at a time, and only the power column of it: the alternative is
     * every sample of every ride in memory at once, which for a year of daily
     * riding is about a million rows. Each ride's series is walked and dropped.
     *
     * **Measured rides only**, and the count of the others comes back with them
     * — an empty list has to be able to say *why* it is empty rather than
     * implying the rider has never ridden.
     *
     * Not cached anywhere yet, and that is a deliberate not-yet: caching means
     * a column or a table, and a schema change made before anybody has felt it
     * be slow is a guess. The ceiling is written down in 16.3.3.
     */
    suspend fun personalBests(userId: Int): PersonalBests {
        val rides = workoutDao.measuredRides(userId)
        val best = mutableMapOf<Int, PersonalBest>()

        rides.forEach { ride ->
            val samples = metricDao.getMetricsForWorkout(ride.workoutId)
                .map { PowerSample(it.timestampSec, it.power) }
            MeanMaximalPower.bests(samples).forEach { (window, watts) ->
                val standing = best[window]
                if (standing == null || watts > standing.watts) {
                    best[window] = PersonalBest(
                        windowSec = window,
                        watts = watts,
                        workoutId = ride.workoutId,
                        atEpochMs = ride.recordedAt,
                        classTitle = ride.classTitle
                    )
                }
            }
        }

        return PersonalBests(
            efforts = MeanMaximalPower.WINDOWS.mapNotNull { best[it] },
            ridesCounted = rides.size,
            ridesSkipped = workoutDao.unmeasuredRideCount(userId)
        )
    }

    /** The rider's own best earlier ride of this class (16.3.4). */
    suspend fun previousBestOfClass(
        classId: String,
        userId: Int,
        excludingWorkoutId: String,
        beforeMs: Long
    ): PreviousBestRow? =
        workoutDao.previousBestOfClass(classId, userId, excludingWorkoutId, beforeMs)

    /**
     * Who on this bike has ridden in the last week, with their streaks
     * (PLAN 24.2.1, 24.2.2).
     *
     * Two queries rather than one clever one: the week's totals come out of
     * SQL, and each rider's streak is counted by `StreakCalculator` from raw
     * timestamps, because "consecutive local calendar days" is the kind of date
     * arithmetic that goes wrong twice a year and cannot be tested where SQL
     * lives.
     */
    /**
     * The household week, reloaded whenever anybody on the tablet rides.
     *
     * Keyed off a whole-table count rather than the current profile's, because
     * the point of this panel is the *other* riders and their rides would
     * otherwise never move it.
     */
    /** Completed rides on this tablet since a moment, for the backup reminder (23.3.1). */
    fun observeCompletedSince(sinceEpochMs: Long): Flow<Int> =
        workoutDao.observeCompletedSince(sinceEpochMs)

    /**
     * How much and how often, over whole weeks (PLAN 16.3.2, 16.3.5).
     *
     * The window is a few days wider than the weeks asked for, because the
     * first week on the chart starts before the day the window is counted from
     * — a ride on the Monday of the earliest week would otherwise be dropped by
     * SQL and leave a bar that is short for no visible reason.
     *
     * The streak comes from the same place the household panel's does, so the
     * two cannot disagree about what a day is.
     */
    fun observeRidingHistory(
        userId: Int,
        weeks: Int = RidingHistoryBuilder.DEFAULT_WEEKS,
        now: () -> Long = { System.currentTimeMillis() }
    ): Flow<RidingHistory> {
        val since = now() - (weeks + 1) * WEEK_MS
        return workoutDao.observeRideRecords(userId, since).map { rows ->
            val at = now()
            RidingHistoryBuilder.build(
                rides = rows.map {
                    RideRecord(
                        atEpochMs = it.timestamp,
                        durationSec = it.durationSec,
                        outputKj = it.totalOutputKj
                    )
                },
                now = at,
                weeks = weeks,
                streakDays = StreakCalculator.currentStreak(
                    rideTimestamps = rows.map { it.timestamp },
                    now = at
                )
            )
        }
    }

    fun observeHouseholdWeek(): Flow<List<HouseholdRiderWeek>> =
        workoutDao.observeAnyCompletedCount().map { householdWeek() }

    suspend fun householdWeek(now: Long = System.currentTimeMillis()): List<HouseholdRiderWeek> {
        val weekAgo = now - WEEK_MS
        return workoutDao.householdWeek(weekAgo).map { row ->
            HouseholdRiderWeek(
                localUserId = row.localUserId,
                name = row.name,
                rides = row.rides,
                outputKj = row.outputKj,
                lastRideAt = row.lastRideAt,
                streakDays = StreakCalculator.currentStreak(
                    rideTimestamps = workoutDao.rideTimestampsSince(
                        userId = row.localUserId,
                        sinceMs = now - STREAK_WINDOW_MS
                    ),
                    now = now
                )
            )
        }
    }

    companion object {
        /**
         * Rides drained per pass of the backlog (14.2.5).
         *
         * Twenty ~55 KB payloads is around a megabyte of uploads in one waking
         * of the worker, which is a reasonable ask of household wifi and small
         * enough that a failure halfway costs little. The drain re-enqueues
         * itself while anything is left rather than doing a year in one job.
         */
        const val SYNC_BATCH = 20

        private const val DURATION_TOLERANCE = 0.10
        private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

        /**
         * How far back a streak is allowed to reach. Long enough that no real
         * streak is truncated, short enough that this stays one small query
         * per rider rather than the whole history.
         */
        const val STREAK_WINDOW_MS = 400L * 24 * 60 * 60 * 1000
    }
}
