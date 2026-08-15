package com.pelonot.data.repository

import com.pelonot.data.local.dao.ActiveRideRivalDao
import com.pelonot.data.local.dao.HouseholdRivalRow
import com.pelonot.data.local.dao.LastRideRow
import com.pelonot.data.local.dao.WorkoutDao
import com.pelonot.data.local.dao.WorkoutListItem
import com.pelonot.data.local.dao.WorkoutMetricDao
import com.pelonot.data.local.dao.WorkoutPowerBestDao
import com.pelonot.data.local.entity.ActiveRideRivalEntity
import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.data.local.entity.WorkoutMetricEntity
import com.pelonot.data.local.entity.WorkoutPowerBestEntity
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
import com.pelonot.domain.social.ClassRival
import com.pelonot.domain.social.HouseholdRider
import com.pelonot.domain.social.RaceCompetitor
import com.pelonot.domain.progress.LastRide
import com.pelonot.domain.progress.LastRideStanding
import com.pelonot.domain.progress.MeanMaximalPower
import com.pelonot.domain.progress.RideStanding
import com.pelonot.domain.progress.PersonalBest
import com.pelonot.domain.progress.PersonalBests
import com.pelonot.domain.progress.PowerSample
import com.pelonot.domain.progress.RECENT_WINDOW_DAYS
import com.pelonot.domain.progress.RideRecord
import com.pelonot.domain.progress.RiderLevel
import com.pelonot.domain.progress.RidingTotals
import com.pelonot.domain.progress.RidingHistory
import com.pelonot.domain.progress.RidingHistoryBuilder
import com.pelonot.domain.social.StreakCalculator
import com.pelonot.domain.suggest.ClassRideCount
import com.pelonot.domain.suggest.RecentRide
import com.pelonot.domain.suggest.RiderRides

/**
 * What the dashboard's progress section is built from (PLAN 22.1.8).
 *
 * **One field, and that is the item.** It used to carry `todayOutputKj` and the
 * whole `WorkoutEntity` of the last ride, which the screen drew as two cards
 * reading *"Today's Output 73 kJ"* and *"Recent Ride 73 kJ"* — the same number,
 * twice, whenever the last ride happened to be today. 22.1's original complaint
 * was that *"both are the same quantity on the same axis"*, and on a rider who
 * rides once a week the today figure spends six days out of seven at `0.0`,
 * which is 22.5's defect surviving on the card next to the one that fixed it.
 *
 * 22.1.1 settles what belongs here: the dashboard answers *should I ride today,
 * and what should I ride*. A total of work already done answers neither, and
 * lives on history (12) and *Your riding* (16.3.2) where it is being read
 * rather than glanced at.
 *
 * Before either of those it held hardcoded literals — "12.5" kJ today, "8.3" kJ
 * last ride and a permanent *FTP Stable* badge — shown to riders as their own
 * statistics on a device that had never recorded a workout.
 */
data class DashboardStats(
    val lastRide: LastRide? = null
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
    private val metricDao: WorkoutMetricDao,
    private val activeRideRivalDao: ActiveRideRivalDao,
    private val workoutPowerBestDao: WorkoutPowerBestDao
) {

    fun observeWorkouts(userId: Int): Flow<List<WorkoutEntity>> =
        workoutDao.getWorkoutsByUser(userId)

    /** Ride history, newest first, limited to [limit] rows. See [WorkoutListItem]. */
    fun observeHistory(userId: Int, limit: Int): Flow<List<WorkoutListItem>> =
        workoutDao.observeHistory(userId, limit)

    fun observeCompletedCount(userId: Int): Flow<Int> =
        workoutDao.observeCompletedCount(userId)

    /**
     * Rides nobody has claimed, newest first (12.4.1).
     *
     * Deliberately not filtered by profile — see [WorkoutDao
     * .observeUnclaimedRides]. The window is small because it is a loose end
     * rather than a list: a household with fifty unclaimed rides has a habit
     * to change, not a page to scroll.
     */
    fun observeUnclaimedRides(limit: Int = UNCLAIMED_WINDOW): Flow<List<WorkoutListItem>> =
        workoutDao.observeUnclaimedRides(limit)

    /**
     * What the dashboard's progress section draws (22.1.5, 22.1.8).
     *
     * The standing is resolved off the row rather than in a second flow, and
     * re-resolved every time the row changes — which is every time a ride ends
     * and every time one is deleted, since both write `workouts` and that is
     * the table this query mentions (the rule the household panel is built on).
     */
    fun observeDashboardStats(userId: Int): Flow<DashboardStats> =
        workoutDao.observeLastRide(userId).map { row ->
            DashboardStats(lastRide = row?.let { lastRide(userId, it) })
        }

    /**
     * One row, plus the question the card's claim depends on (22.1.7).
     *
     * It is only asked when there is a class to ask about: a Just Ride has
     * nothing to be compared with, so the earlier totals are not fetched at all
     * on the commonest case.
     *
     * **The provenance comes off the row** (23.4.12). It used to be a count over
     * this ride's whole sample series — every second of a 45-minute ride, read to
     * answer one yes-or-no question, on the one screen that must not touch
     * `workout_metrics` (22.1.8). Now it is a column the finalise wrote, which
     * is also the only answer that survives 23.4 trimming the ride.
     */
    private suspend fun lastRide(userId: Int, row: LastRideRow): LastRide {
        val standing = row.classId?.let { classId ->
            LastRideStanding.of(
                classId = classId,
                outputKj = row.totalOutputKj,
                isMeasured = row.powerProvenance?.isTrustworthyAsMeasured == true,
                earlierMeasuredTotals = workoutDao.ownTotalsForClassExcluding(
                    userId = userId,
                    classId = classId,
                    excludingWorkoutId = row.id
                )
            )
        } ?: RideStanding.Unclaimed

        return LastRide(
            workoutId = row.id,
            classTitle = row.classTitle,
            atEpochMs = row.timestamp,
            durationSec = row.durationSec,
            standing = standing
        )
    }

    /**
     * What the suggestion rule reads about this rider (22.8.6).
     *
     * Two queries rather than one because they answer two different questions
     * over two different windows — every class they have *ever* ridden, and the
     * *last few* rides of any kind — and a join that produced both would have to
     * pick one of the two windows.
     *
     * Both mention `workouts` and nothing else, so both re-emit whenever a ride
     * lands or is deleted, which is the rule the household panel is built on.
     * Neither touches `workout_metrics`: this feeds the first screen anybody
     * sees (22.1.8).
     */
    fun observeRiderRides(userId: Int): Flow<RiderRides> = combine(
        workoutDao.observeClassRideCounts(userId),
        workoutDao.observeRecentRides(userId, RIDE_HISTORY_WINDOW)
    ) { counts, recent ->
        RiderRides(
            perClass = counts.map {
                ClassRideCount(
                    classId = it.classId,
                    rides = it.rides,
                    lastRiddenAtMs = it.lastRiddenAtMs
                )
            },
            recent = recent.map {
                RecentRide(
                    classId = it.classId,
                    atEpochMs = it.timestamp,
                    durationSec = it.durationSec
                )
            }
        )
    }

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

    /**
     * Ends a ride, and writes down the two things about it that its samples will
     * not be able to answer later (16.3.3a, 23.4.12).
     *
     * The scan is here rather than at the one call site because there are three
     * ways a ride is finalised — the service's, the crash recovery's, and the
     * second finalise of a ride resumed under 12.6.2 — and a ride that missed
     * it would be uncounted for ever once 23.4 has trimmed it.
     *
     * Strictly after the update: [recordPowerFacts] writes `power_provenance`
     * and `power_bests_at` on the same row, and `WorkoutSession` carries
     * neither, so either one written first would be handed back as its default
     * by the finalise (8.3d.4).
     */
    suspend fun finaliseWorkout(workout: WorkoutEntity) {
        workoutDao.updateWorkout(workout.copy(isComplete = true))
        recordPowerFacts(workout.id)
    }

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
        recordPowerFacts(workoutId)
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
            interruptedSec = workout.interruptedSec + interruption.unrecordedSec,
            // Both matter only on the path 12.6.2 opened — resuming a ride that
            // was *finished* rather than crashed out of, because the rider hit
            // End by accident. A crashed ride is already incomplete and has
            // never been synced, so for 8.3d these two are no-ops.
            //
            // `isComplete` because a ride being ridden is not a ride in the
            // rider's history: left true it shows up in history, in the
            // leaderboards and in the totals while it is still being ridden.
            // `syncedAt` because the cloud's copy is now the short version of a
            // ride that is about to get longer, and a ride that is marked as
            // backed up is never offered again (14.2.5). The upload is an
            // upsert on the ride's own id (15.3.3), so re-sending replaces it
            // rather than duplicating it.
            //
            // `powerBestsAt` for exactly `syncedAt`'s reason (16.3.3a): the
            // efforts on record are the short ride's, and the longer one may
            // hold a window this one did not. The stored rows go with it —
            // leaving them would let a twenty-minute effort survive on a ride
            // whose scan says it was never worked out.
            //
            // `powerProvenance` for the same reason and one of its own
            // (23.4.12): the extra minutes can change the answer. A board that
            // dies during them turns a `Measured` ride into a `Mixed` one, and
            // the stale word would keep it on six leaderboards it no longer
            // belongs on. The finalise writes it again either way.
            isComplete = false,
            syncedAt = null,
            powerBestsAt = null,
            powerProvenance = null
        )
        workoutDao.updateWorkout(reopened)
        workoutPowerBestDao.clearFor(workoutId)

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
     * The rider's personal bests by duration (16.3.3), read rather than
     * re-derived (16.3.3a).
     *
     * Every ride's efforts were worked out once, when it was recorded, so this
     * is one query over `workout_power_bests` and a reduction. What it used to
     * be was one query *per ride* plus a full sample scan of each — bounded in
     * memory but not in reads, and about a million rows for a year of daily
     * riding.
     *
     * **The reason that changed is not speed.** 23.4 trims old rides down to
     * their aggregates, and a best derived on read would silently get worse the
     * moment the samples behind it went — on the one screen that exists to show
     * a rider their training working, with nothing said. A best that was never
     * computed cannot be recovered from a trimmed ride, so 23.4.8 makes this a
     * prerequisite rather than the optimisation 16.3.3a first filed it as.
     *
     * [backfillPowerFacts] is what keeps that true of rides recorded before the
     * column existed. It runs here rather than at launch because this is the
     * only screen that needs the answer, and after the first run it finds
     * nothing.
     *
     * **Measured rides only**, and the count of the others comes back with them
     * — an empty list has to be able to say *why* it is empty rather than
     * implying the rider has never ridden.
     */
    suspend fun personalBests(userId: Int): PersonalBests {
        backfillPowerFacts(userId)

        val efforts = workoutPowerBestDao.bestsFor(userId).map { row ->
            PersonalBest(
                windowSec = row.windowSec,
                watts = row.watts,
                workoutId = row.workoutId,
                atEpochMs = row.recordedAt,
                classTitle = row.classTitle
            )
        }

        return PersonalBests(
            efforts = MeanMaximalPower.strongest(efforts),
            ridesCounted = workoutDao.ridesWithBestsCount(userId),
            ridesSkipped = workoutDao.ridesWithoutBestsCount(userId)
        )
    }

    /**
     * Works out the efforts of any measured ride that has never been scanned
     * (16.3.3a).
     *
     * The whole backfill, and it is deliberately not a migration: mean-maximal
     * power is a sliding window over a series with gaps in it, which SQL cannot
     * express and an approximation would falsify. So it is the old code path,
     * run once per ride instead of once per visit.
     *
     * A ride 23.4 has already trimmed is **not** here, and that is a clause in
     * the query rather than a property of the data — which is the correction.
     * It read "cannot be: it has no samples left to walk", and a trimmed ride
     * has plenty: the lowest and highest watt of every ten seconds are real
     * rows. So a ride trimmed before it was ever scanned was walked at a fifth
     * of its resolution and given a mean-maximal effort it never rode. It stays
     * uncounted now, which is the honest answer, and it is still the argument
     * for landing this before any trimming exists.
     *
     * **Provenance first, and the order is not incidental** (23.4.12): the list
     * this walks is now *"rides whose row says measured"*, so a ride with no
     * provenance written yet is not in it and would never be scanned. The launch
     * pass usually got there first; asking again here costs one `UPDATE` that
     * matches nothing and means *Your FTP* does not depend on that.
     */
    private suspend fun backfillPowerFacts(userId: Int) {
        backfillPowerProvenance()
        workoutDao.measuredRidesAwaitingBests(userId).forEach { ride ->
            recordPowerFacts(ride.workoutId)
        }
    }

    /**
     * Gives every finished ride a `power_provenance`, and returns how many it
     * had to write (23.4.12).
     *
     * Two callers, one on each side of the reason this exists. `PelonotApp` runs
     * it at launch because the six household queries gated on this column are
     * asked from screens all over the app and a ride missing it is a ride
     * missing from all of them; [personalBests] runs it because its own backfill
     * now reads the column. Idempotent, whole-tablet rather than per rider — a
     * housemate's ride is on the board too — and after the first run it writes
     * nothing.
     */
    suspend fun backfillPowerProvenance(): Int = workoutDao.backfillPowerProvenance()

    /**
     * Writes down where one ride's watts came from, and what its best efforts
     * were (23.4.12, 16.3.3a).
     *
     * Called at every finalise — the service's (`stopWorkout`), the crash
     * recovery's, and the second finalise of a ride resumed under 12.6.2 — so
     * the stored answers are never older than the samples they came from.
     *
     * **The provenance is written for every ride, the efforts only for a
     * measured one.** That asymmetry is the whole of 23.4.12: `Modelled` is a
     * real answer and a ride is entitled to have it recorded, whereas a personal
     * best derived from `PowerModel` — RMSE 137 W — would be a fiction filed as
     * a record. Before this, "modelled" and "never scanned" were the same null
     * in `power_bests_at`, so six leaderboards had to go back to the samples to
     * tell them apart.
     */
    private suspend fun recordPowerFacts(workoutId: String) {
        workoutPowerBestDao.clearFor(workoutId)

        val provenance = metricDao.getPowerProvenanceCounts(workoutId).provenance
        workoutDao.markPowerProvenance(workoutId, provenance)
        if (!provenance.isTrustworthyAsMeasured) return

        val samples = metricDao.getMetricsForWorkout(workoutId)
            .map { PowerSample(it.timestampSec, it.power) }
        val bests = MeanMaximalPower.bests(samples).map { (window, watts) ->
            WorkoutPowerBestEntity(workoutId = workoutId, windowSec = window, watts = watts)
        }
        if (bests.isNotEmpty()) workoutPowerBestDao.upsert(bests)

        // Last, and after the rows: a marker written before them would claim a
        // scan that a crash in between had not finished.
        workoutDao.markPowerBestsScanned(workoutId, System.currentTimeMillis())
    }

    /** The rider's own best earlier ride of this class (16.3.4). */
    suspend fun previousBestOfClass(
        classId: String,
        userId: Int,
        excludingWorkoutId: String,
        beforeMs: Long
    ): PreviousBestRow? =
        workoutDao.previousBestOfClass(classId, userId, excludingWorkoutId, beforeMs, sinceMs = 0L)

    /**
     * Everyone whose ride of this class could be raced live (24.3.3).
     *
     * The rider's own best comes first when they have one, because *"or
     * yourself"* is the case that makes the feature work at all: on a
     * household bike most riders are the only person who has ridden a given
     * class, and a ghost that only appears when a housemate happens to have
     * ridden it too is one most riders would never see.
     *
     * Both halves carry the measured-power exclusion the DAO already applies
     * (24.3.7), so a modelled ride is never offered as a race — `PowerModel`
     * is 137 W out at RMSE, and a rider cannot tell a fiction drawn to scale
     * from a real one.
     *
     * Empty is the ordinary answer and nothing may be drawn for it (24.1.6).
     */
    suspend fun rivalsForClass(classId: String, youId: Int?): List<ClassRival> {
        val yourBest = youId?.let { id ->
            workoutDao.previousBestOfClass(
                classId = classId,
                userId = id,
                // Nothing to exclude: there is no ride yet — this is asked on
                // the class detail screen, before one starts.
                excludingWorkoutId = "",
                beforeMs = Long.MAX_VALUE,
                sinceMs = 0L
            )?.let {
                ClassRival(
                    workoutId = it.workoutId,
                    name = "Your best",
                    outputKj = it.outputKj,
                    you = true
                )
            }
        }

        val housemates = workoutDao.householdRivals(
            classId = classId,
            excludingWorkoutId = "",
            // The guest sentinel, so a guest ride races the whole household
            // rather than silently excluding whichever profile holds id -1.
            excludingUserId = youId ?: GUEST_SENTINEL_USER_ID
        ).map {
            ClassRival(workoutId = it.workoutId, name = it.name, outputKj = it.outputKj)
        }

        return listOfNotNull(yourBest) + housemates
    }

    /**
     * Everybody the ride about to start is racing, for the live leaderboard
     * (24.3.12).
     *
     * Four kinds of row and they are **four queries rather than four formats**
     * — three windows onto the rider's own history and one onto the household.
     * The fifth kind, a friend's best, needs Phase 18 and their samples in the
     * cloud; it is absent rather than errored, which is rule 3 of the
     * connectivity model working as written.
     *
     * **The windows are rolling, not calendar**, and that is 22.5.1's finding
     * applied rather than rediscovered: a calendar month resets on the 1st, so
     * a rider who rode on the 29th and the 30th would open a class on the 1st
     * and find the reachable ghost they were chasing simply gone. The owner
     * asked for *"PB this month, PB this year"* and the honest version of that
     * on a bike somebody rides twice a week is the last thirty days and the
     * last twelve months. The labels say so.
     *
     * **The same ride qualifying twice appears once**, at its widest label —
     * see [RaceCompetitor.Kind.widerThan]. Nothing else in here can produce a
     * duplicate: the household query is one row per rider and excludes the
     * rider themselves.
     *
     * Empty is an ordinary answer and nothing may be drawn for it (24.1.6).
     */
    suspend fun raceBoardFor(
        classId: String,
        youId: Int?,
        excludingWorkoutId: String,
        nowMs: Long
    ): List<RaceCompetitor> {
        val yours = youId?.let { id ->
            listOf(
                RaceCompetitor.Kind.YourBestEver to 0L,
                RaceCompetitor.Kind.YourBestYear to nowMs - RACE_YEAR_MS,
                RaceCompetitor.Kind.YourBestMonth to nowMs - RACE_RECENT_MS
            ).mapNotNull { (kind, sinceMs) ->
                workoutDao.previousBestOfClass(
                    classId = classId,
                    userId = id,
                    excludingWorkoutId = excludingWorkoutId,
                    beforeMs = Long.MAX_VALUE,
                    sinceMs = sinceMs
                )?.let { row ->
                    RaceCompetitor(
                        workoutId = row.workoutId,
                        name = kind.label,
                        kind = kind,
                        outputKj = row.outputKj
                    )
                }
            }
        }.orEmpty()

        val housemates = workoutDao.householdRivals(
            classId = classId,
            excludingWorkoutId = excludingWorkoutId,
            // The guest sentinel, so a guest ride races the whole household
            // rather than silently excluding whichever profile holds id -1.
            excludingUserId = youId ?: GUEST_SENTINEL_USER_ID
        ).map {
            RaceCompetitor(
                workoutId = it.workoutId,
                name = it.name,
                kind = RaceCompetitor.Kind.Housemate,
                outputKj = it.outputKj
            )
        }

        // 24.3.18b — activity, not just achievement. A best can be two years
        // old; a last ride says somebody was on this bike recently, which is
        // the owner's *"exciting to see activity"*. Where the two are the same
        // ride, `oneRowPerRide` keeps the prouder label.
        val latest = workoutDao.householdLatestRides(
            classId = classId,
            excludingWorkoutId = excludingWorkoutId,
            excludingUserId = youId ?: GUEST_SENTINEL_USER_ID
        ).map {
            RaceCompetitor(
                workoutId = it.workoutId,
                name = "${it.name}'s last ride",
                kind = RaceCompetitor.Kind.HousemateLatest,
                outputKj = it.outputKj
            )
        }

        return RaceCompetitor.oneRowPerRide(yours + housemates + latest).take(MAX_RACE_FIELD)
    }

    /**
     * Every measured total this rider has recorded on one class, for *your
     * usual* (24.3.18b).
     */
    suspend fun ownTotalsForClass(classId: String, userId: Int): List<Double> =
        workoutDao.ownTotalsForClass(userId = userId, classId = classId)

    /**
     * Records which rival a ride in progress is racing, so it can be read
     * back if the app dies mid-ride (24.3.8). Must not be called before the
     * `workouts` row exists — the table's foreign key requires it.
     */
    suspend fun setActiveRival(workoutId: String, rivalWorkoutId: String) =
        activeRideRivalDao.set(ActiveRideRivalEntity(workoutId, rivalWorkoutId))

    /** The rival a resumed ride was racing before the crash, if any (24.3.8). */
    suspend fun getActiveRival(workoutId: String): String? =
        activeRideRivalDao.get(workoutId)?.rivalWorkoutId

    /** No longer needed once the ride is finished. */
    suspend fun clearActiveRival(workoutId: String) = activeRideRivalDao.clear(workoutId)

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
                ),
                // 22.5.2. Both are computed from the same timestamps rather
                // than one from the other: a run of weeks is not a run of days
                // divided by seven, and deriving it that way would report a
                // rider who rode Sunday and Monday as two weeks.
                streakWeeks = StreakCalculator.currentWeeklyStreak(
                    rideTimestamps = rows.map { it.timestamp },
                    now = at
                )
            )
        }
    }

    /**
     * Every profile's riding level, keyed by profile (26.4.1).
     *
     * One flow for the whole tablet rather than one per rider: the dashboard
     * needs the current rider's and the household panel needs everybody's, and
     * two sources for one number is how two surfaces come to disagree.
     *
     * A profile with no finished rides is **absent from the map**, not present
     * at level 1 — same rule as the household panel's missing rows. The caller
     * decides what an absence draws, and on the dashboard it is level 1,
     * because *your* level before your first ride is a real answer.
     */
    fun observeRiderLevels(): Flow<Map<Int, RiderLevel>> =
        workoutDao.observeRiderTotals().map { rows ->
            rows.associate { row ->
                row.localUserId to RiderLevel.of(
                    RidingTotals(
                        rides = row.rides,
                        durationSec = row.durationSec,
                        outputKj = row.outputKj
                    )
                )
            }
        }

    /**
     * **Both flows are needed and neither is redundant.** The count is what
     * mentions `profiles`, so it is the only one of the two that re-emits when
     * somebody turns `household_visible` off; the levels are what mention
     * nothing but `workouts`. Dropping either leaves the panel stale for one of
     * the two things that can change it, which is the defect
     * [WorkoutDao.observeAnyCompletedCount] was written to fix.
     */
    fun observeHousehold(): Flow<List<HouseholdRider>> =
        combine(workoutDao.observeAnyCompletedCount(), observeRiderLevels()) { _, levels -> levels }
            .map { levels -> householdRecent(levels = levels) }

    /**
     * Who on this bike has ridden **in the last 30 days** (24.2, 22.5.4).
     *
     * It counted a *week* until 22.5.4, and that was the same defect the
     * dashboard's own card had: on the owner's stated cadence — at most one
     * ride a week each — a household of three shows an empty board most days,
     * and the panel whose job is *other people are riding too* spends six days
     * in seven saying nobody is. The window is the same rolling 30 days
     * `RidingWindow` uses, for the same reason: it never resets, so it never
     * hands anybody a zero the morning after they rode.
     *
     * The streak comes with it. A run of *days* is meaningless at this cadence
     * (22.5.2) — a rider who has never missed a Sunday scores 1 — so this is a
     * run of **weeks**, which is the thing a once-a-week rider is actually
     * keeping up.
     */
    suspend fun householdRecent(
        now: Long = System.currentTimeMillis(),
        levels: Map<Int, RiderLevel> = emptyMap()
    ): List<HouseholdRider> {
        val since = now - HOUSEHOLD_WINDOW_MS
        return workoutDao.householdRecent(since).map { row ->
            HouseholdRider(
                localUserId = row.localUserId,
                name = row.name,
                rides = row.rides,
                outputKj = row.outputKj,
                lastRideAt = row.lastRideAt,
                // A row exists only because this rider has ridden inside the
                // window, so an absent level means the two queries were read
                // either side of a delete rather than that they never rode.
                level = levels[row.localUserId] ?: RiderLevel.of(RidingTotals()),
                streakWeeks = StreakCalculator.currentWeeklyStreak(
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

        /** How many unclaimed rides history offers to file at once (12.4.1). */
        private const val UNCLAIMED_WINDOW = 20

        /**
         * How many recent rides the suggestion reads to decide how long this
         * rider rides (22.8.6). Ten is the same window `ClassToRide` takes its
         * median over — read here so the query does not fetch rows the rule
         * throws away.
         */
        private const val RIDE_HISTORY_WINDOW = 10

        /**
         * Stands in for "no profile" when excluding the rider's own rides
         * from a rival list. Profile ids are autogenerated from 1, so this
         * matches nobody — which is what a guest needs, since a guest has no
         * earlier rides of their own to leave off the list.
         */
        private const val GUEST_SENTINEL_USER_ID = -1

        private const val DURATION_TOLERANCE = 0.10
        private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

        /**
         * The live leaderboard's two rolling windows (24.3.12).
         *
         * `RECENT_WINDOW_DAYS` rather than a literal 30, so the *30 days* row
         * on the board covers the same span as the dashboard's *Last 30 days*
         * card. Two figures about the same rider that disagree about what
         * recently means is the kind of thing nobody notices and nobody can
         * explain afterwards.
         */
        private const val RACE_RECENT_MS = RECENT_WINDOW_DAYS * 24L * 60 * 60 * 1000
        private const val RACE_YEAR_MS = 365L * 24 * 60 * 60 * 1000

        /**
         * A ceiling on the field, and it is a safety valve rather than a
         * design (24.3.12).
         *
         * A household is a handful of profiles, so nothing today comes near
         * it. What it bounds is the cost of being wrong later: every ghost is
         * a whole ride's samples read off disk at ride start and held in
         * memory for the length of the class — a 45-minute rival is around
         * 2,700 points — and Phase 18's friends are an unbounded list from a
         * network. Eight is more competitors than 24.3.13's three-row window
         * can usefully hide behind.
         */
        /**
         * How many competitors the live board may carry (24.3.18c).
         *
         * Eight until the thirty-second sitting. The owner's *"the more the
         * merrier within reason"* plus `HousemateLatest` doubling the number
         * of household rows made eight the thing doing the truncating rather
         * than the screen — and since 24.3.13 the screen shows a window of six
         * and scrolls for the rest, so a bigger field costs a longer scroll
         * rather than a taller card. Sixteen is still a bound: every one of
         * these is a `workout_metrics` read at ride start.
         */
        private const val MAX_RACE_FIELD = 16

        /**
         * How far back the household panel looks (22.5.4).
         *
         * `RECENT_WINDOW_DAYS`, so the panel and the rider's own *Last 30 days*
         * card answer over the same window — two figures on one screen that
         * disagree about what "recently" means is worse than either window
         * alone.
         */
        private const val HOUSEHOLD_WINDOW_MS =
            RECENT_WINDOW_DAYS * 24L * 60 * 60 * 1000

        /**
         * How far back a streak is allowed to reach. Long enough that no real
         * streak is truncated, short enough that this stays one small query
         * per rider rather than the whole history.
         */
        const val STREAK_WINDOW_MS = 400L * 24 * 60 * 60 * 1000
    }
}
