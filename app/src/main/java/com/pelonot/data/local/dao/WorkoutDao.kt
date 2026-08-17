package com.pelonot.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.domain.model.PowerProvenance
import kotlinx.coroutines.flow.Flow

/** One rider's week on the dashboard's household panel — see [WorkoutDao.householdRecent]. */
data class HouseholdRiderRow(
    val localUserId: Int,
    val name: String,
    /** The stored `profiles.avatar`, or null for a rider who never chose (20.2.6). */
    val avatar: String?,
    val rides: Int,
    val outputKj: Double,
    val lastRideAt: Long
)

/**
 * Everything one rider has ever ridden here, in three numbers (26.4.1).
 *
 * Deliberately not windowed and deliberately not joined to anything: the level
 * this feeds is the app's only figure that cannot go down, and every window in
 * the project — 30 days, 17 weeks, the last ten rides — is a thing a rider can
 * fall out of.
 */
data class RiderTotalsRow(
    val localUserId: Int,
    val rides: Int,
    val durationSec: Long,
    val outputKj: Double
)

/** One rider's place on a class's household board — see [WorkoutDao.householdLeaderboard]. */
data class ClassLeaderboardRow(
    val localUserId: Int,
    val name: String,
    val weightKg: Double,
    val bestOutputKj: Double,
    /**
     * Their cloud account, or null for a housemate who has never signed in.
     *
     * Carried purely so a rider who is on **both** boards can be recognised and
     * shown once (18.9). Nothing displays it, and it never leaves the tablet by
     * this route — the cloud already knows its own ids.
     */
    val authUserId: String?
)

/** A housemate's ride of the same class, ready to draw behind yours (24.3.1). */
/** A ride whose power came off the board, for the personal-best scan (16.3.3). */
data class MeasuredRideRow(
    val workoutId: String,
    val recordedAt: Long,
    val classTitle: String?
)

/**
 * One ride that can speak about whether a rider's FTP is set too high (7.11).
 *
 * Every column is something the ride wrote down at the time. `max_hr_bpm` is
 * the maximum *that ride* was judged against rather than the rider's today
 * (21.4.2a), and the twenty-minute effort is a stored one rather than a scan of
 * `workout_metrics`, because 23.4's trimmer would answer that scan off a fifth
 * of the seconds and be believed.
 */
data class FtpEvidenceRow(
    val workoutId: String,
    val recordedAt: Long,
    val peak20MinWatts: Double,
    val avgHr: Double?,
    val rideMaxHrBpm: Int?,
    val rpeRating: Int?
)

/** The rider's own best earlier ride of a class (16.3.4). */
data class PreviousBestRow(
    val workoutId: String,
    val outputKj: Double,
    val recordedAt: Long
)

data class HouseholdRivalRow(
    val localUserId: Int,
    val name: String,
    val workoutId: String,
    val outputKj: Double,
    val durationSec: Int,
    /**
     * The two identity columns the live board's row draws (24.3.19a, 24.3.19b).
     *
     * They come off `profiles` in the same join that is already there, rather
     * than from a second read keyed by [localUserId], because the join is what
     * carries `household_visible` — and that switch **is** the consent for both
     * of them (24.2.3). Reading them separately would let a rider who is off the
     * household panel keep a face and an FTP on the board.
     */
    val avatar: String?,
    val ftpWatts: Int
)

/**
 * A housemate's most recent ride of one class — see
 * [WorkoutDao.householdLatestRides] (24.3.18b).
 *
 * Separate from [HouseholdRivalRow] only because it carries [lastRideAt], and
 * that column is what the row is *for*: it is on the board because it is news,
 * not because it is good.
 */
data class HouseholdLatestRow(
    val localUserId: Int,
    val name: String,
    val workoutId: String,
    val outputKj: Double,
    val durationSec: Int,
    val lastRideAt: Long,
    /** See [HouseholdRivalRow.avatar] — the same two columns off the same join. */
    val avatar: String?,
    val ftpWatts: Int
)

/**
 * How far behind a rider's cloud backup is (PLAN 14.2.3, 14.2.4).
 *
 * `oldestTimestamp` is **nullable and null means there is no backlog** — it is
 * `MIN()` over an empty set, which SQL gives as NULL, and that is the honest
 * shape rather than a sentinel. A caller reading it as 0 would place the
 * oldest unsynced ride at the epoch and report a backup 56 years behind.
 */
data class SyncBacklog(
    val pending: Int,
    val oldestTimestamp: Long?
) {
    /** Nothing is waiting. Not the same as "backup is off" — see `CloudAccess`. */
    val isClear: Boolean get() = pending == 0
}

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

    /**
     * Records that the cloud has this ride (PLAN 14.2.4).
     *
     * A targeted `UPDATE` rather than a read-modify-write through
     * `updateWorkout`, and that is not a micro-optimisation — it is the rule
     * from 7.9/7.10.3. The sync worker runs on a background thread while the
     * rider may be sitting on the post-ride summary setting an RPE, and two
     * coroutines each reading a `WorkoutEntity` and writing back a copy is
     * exactly how **typing a new FTP and pressing Save left the old one in the
     * database**. One fact, one column, one statement.
     */
    @Query("UPDATE workouts SET synced_at = :syncedAt WHERE id = :id")
    suspend fun markSynced(id: String, syncedAt: Long)

    /**
     * Forgets that the cloud has any of this profile's rides (PLAN 15.3.1).
     *
     * Run when an account is **attached**, and the reason is that `synced_at`
     * records a fact about *a* cloud without saying whose. A rider who signs
     * out and signs in as somebody else — or on a different endpoint, or after
     * the household's project was rebuilt — has a tablet full of rides marked
     * as backed up into an account that has never seen them. There is no
     * message that could be shown about this and no way for the rider to
     * suspect it: the app would simply say "nothing waiting to go up" forever.
     *
     * So attaching an account re-asks the question from scratch. The upload is
     * an upsert keyed by the ride's own UUID (15.3.3), so re-sending a ride the
     * cloud already has costs bandwidth and changes nothing — which is the
     * right way for this trade to fall, because the other way round loses a
     * history silently.
     */
    @Query("UPDATE workouts SET synced_at = NULL WHERE user_id = :userId")
    suspend fun clearSyncedFor(userId: Int)

    /**
     * The rides belonging to this profile that the cloud has never accepted,
     * **oldest first** (PLAN 14.2.5, 14.2.6, 15.3.1).
     *
     * Oldest first because a backlog drained newest-first leaves the oldest
     * rides permanently at the back of a queue that keeps being overtaken, and
     * a rider's first month is exactly the part they would most miss.
     *
     * `is_complete = 1` because an unfinished row is a ride in progress or a
     * crashed one, and neither is a thing to upload — the first is still being
     * written to, and the second has no aggregates until 8.3's recovery has run
     * over it.
     *
     * This is the query 15.3.1's first-sign-in backfill is made of: a rider who
     * has just attached an account has a whole history where every row has
     * `synced_at IS NULL`, which is the same question as "what is in the
     * backlog" and wants the same answer in the same order.
     */
    @Query(
        """
        SELECT * FROM workouts
        WHERE user_id = :userId AND is_complete = 1 AND synced_at IS NULL
        ORDER BY timestamp ASC
        LIMIT :limit
        """
    )
    suspend fun unsyncedWorkouts(userId: Int, limit: Int): List<WorkoutEntity>

    /**
     * How far behind this rider's backup is — the count, and the date of the
     * oldest ride the cloud has not got.
     *
     * A `Flow` so Settings can say it without polling (14.2.3). The count alone
     * is not enough to write an honest sentence: "3 rides waiting" reads as a
     * transient queue whether the oldest is from this morning or from March,
     * and those are a shrug and an alarm respectively.
     */
    @Query(
        """
        SELECT COUNT(*) AS pending, MIN(timestamp) AS oldestTimestamp
        FROM workouts
        WHERE user_id = :userId AND is_complete = 1 AND synced_at IS NULL
        """
    )
    fun observeBacklog(userId: Int): Flow<SyncBacklog>

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

    /**
     * Rides nobody has claimed — `user_id IS NULL` (PLAN 12.4.1).
     *
     * Every other query on this table is filtered to one profile, which is
     * right for a household bike and has one consequence nobody had looked at:
     * **a guest ride is invisible the moment the post-ride screen closes**. It
     * is not merely hard to re-file, as 12.4.1 says — there is no surface in
     * the app that draws it at all, and the rider who walked away without
     * answering *whose ride was this?* cannot get back to the question.
     *
     * So this is the one deliberately owner-less query, and what it returns is
     * shown to *every* profile rather than folded into any one rider's list: an
     * unclaimed ride is a question, and putting it in somebody's history would
     * be answering it for them.
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
        WHERE w.user_id IS NULL AND w.is_complete = 1
        ORDER BY w.timestamp DESC
        LIMIT :limit
        """
    )
    fun observeUnclaimedRides(limit: Int): Flow<List<WorkoutListItem>>

    /**
     * The most recent finished ride, with the class it was (22.1.5).
     *
     * A projection of its own rather than `observeHistory(limit = 1)`, because
     * the dashboard needs the class **id** as well as its title — the title is
     * what the rider reads and the id is what the comparison is made against —
     * and history has no use for the id. `LEFT JOIN`, so a Just Ride and a ride
     * of a class since retired both still come back with the ride intact.
     *
     * It carries `power_provenance` since 23.4.12, and that is what took the
     * dashboard's last remaining read of `workout_metrics` away: whether the
     * ride's watts can be compared with anything used to be a count over its
     * whole sample series, asked on the first screen the app shows (22.1.8).
     */
    @Query(
        """
        SELECT w.id AS id,
               w.class_id AS class_id,
               c.title AS class_title,
               w.duration_sec AS duration_sec,
               w.total_output_kj AS total_output_kj,
               w.power_provenance AS power_provenance,
               w.timestamp AS timestamp
        FROM workouts w
        LEFT JOIN class_templates c ON c.id = w.class_id
        WHERE w.user_id = :userId AND w.is_complete = 1
        ORDER BY w.timestamp DESC
        LIMIT 1
        """
    )
    fun observeLastRide(userId: Int): Flow<LastRideRow?>

    /** How many rides exist, so the screen knows whether the window is full. */
    @Query("SELECT COUNT(*) FROM workouts WHERE user_id = :userId AND is_complete = 1")
    fun observeCompletedCount(userId: Int): Flow<Int>

    /** The same count, asked once (PLAN 15.3.2 — has this profile ever ridden here?). */
    @Query("SELECT COUNT(*) FROM workouts WHERE user_id = :userId AND is_complete = 1")
    suspend fun completedCountFor(userId: Int): Int

    /**
     * Which of these ride ids this tablet already has, **whoever they belong
     * to** (PLAN 15.3.2).
     *
     * Not scoped to a profile, and that is the point rather than an oversight: a
     * ride's id is the primary key here as well as in the cloud, so restoring
     * one this tablet already holds would not add a row, it would `@Upsert` over
     * the existing one — and on a household bike the row it overwrote could be a
     * housemate's. A restore only ever *adds* rides, so what it needs to know is
     * whether the id is taken, not whose it is.
     */
    @Query("SELECT id FROM workouts WHERE id IN (:ids)")
    suspend fun existingIds(ids: List<String>): List<String>

    /**
     * How much of each class this rider has ridden, and when they last did
     * (22.8.6) — the two facts the suggestion ranks on.
     *
     * Over all of history rather than a window, because *"you have never ridden
     * this"* is a claim about all of it: a rider told a class is new to them
     * when they rode it last spring has been told something false, and it is the
     * kind of false a rider notices immediately.
     *
     * `class_id` is not null by the predicate, so the projection is non-null —
     * a Just Ride is not a class anybody can be offered again.
     */
    @Query(
        """
        SELECT class_id AS class_id,
               COUNT(*) AS rides,
               MAX(timestamp) AS last_ridden_at
        FROM workouts
        WHERE user_id = :userId AND is_complete = 1 AND class_id IS NOT NULL
        GROUP BY class_id
        """
    )
    fun observeClassRideCounts(userId: Int): Flow<List<ClassRideCountRow>>

    /**
     * The rider's last few rides, newest first — how long they ride, and what
     * they last did (22.8.6).
     *
     * Three columns and a `LIMIT`, for the same reason [observeLastRide] is a
     * projection rather than a `WorkoutEntity`: the suggestion needs the length,
     * the class and the clock, and a screen handed thirty columns finds uses for
     * them.
     *
     * **Every ride, not only classes.** The length a rider rides is a fact about
     * their evening, and a Just Ride takes exactly as long as a class does.
     */
    @Query(
        """
        SELECT class_id AS class_id,
               timestamp AS timestamp,
               duration_sec AS duration_sec
        FROM workouts
        WHERE user_id = :userId AND is_complete = 1
        ORDER BY timestamp DESC
        LIMIT :limit
        """
    )
    fun observeRecentRides(userId: Int, limit: Int): Flow<List<RecentRideRow>>

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
    //
    // **`power_provenance = 'Measured'` is the measured-power rule (24.4.2) in
    // the seven queries below, and it used to be a pair of correlated
    // subqueries over `workout_metrics`.** The rule is unchanged: a simulated
    // ride's watts are `PowerModel`'s output, RMSE 137 W against the real
    // board, so ranking one beside a measured ride ranks a rider against a
    // number the app made up — and a ride with no samples at all is not
    // "measured all the way through", it is evidence-free (22.1.7 found that
    // one the hard way). What changed is *where the answer lives*: on the ride,
    // written at finalise, rather than re-reduced from samples 23.4 is about to
    // delete (23.4.12). Null therefore excludes a ride, which is the same
    // answer the old `EXISTS` gave for a ride with nothing to reduce.

    /**
     * Each rider's **best** ride of one class, best first.
     *
     * Three exclusions, each of which is a rule rather than a filter:
     *
     * - `user_id IS NOT NULL` via the join — a guest ride has no owner, so
     *   there is nobody to put on the board (24.1.4).
     * - `household_visible` — the per-profile opt-out (24.2.3).
     * - the measured-power rule above, which is also what excludes a ride
     *   carrying no evidence of the work it claims.
     *
     * One row per rider rather than per ride: a leaderboard listing somebody's
     * six attempts is a personal history, not a comparison.
     */
    @Query(
        """
        SELECT p.local_user_id AS localUserId,
               p.name AS name,
               p.weight_kg AS weightKg,
               p.auth_user_id AS authUserId,
               MAX(w.total_output_kj) AS bestOutputKj
        FROM workouts w
        JOIN profiles p ON p.local_user_id = w.user_id
        WHERE w.class_id = :classId
          AND w.is_complete = 1
          AND p.household_visible = 1
          AND w.power_provenance = 'Measured'
        GROUP BY p.local_user_id
        ORDER BY bestOutputKj DESC
        """
    )
    suspend fun householdLeaderboard(classId: String): List<ClassLeaderboardRow>

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
               w.duration_sec AS durationSec,
               p.avatar AS avatar,
               p.ftp_watts AS ftpWatts
        FROM workouts w
        JOIN profiles p ON p.local_user_id = w.user_id
        WHERE w.class_id = :classId
          AND w.id != :excludingWorkoutId
          AND w.user_id != :excludingUserId
          AND w.is_complete = 1
          AND p.household_visible = 1
          AND w.power_provenance = 'Measured'
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
     * Each housemate's **most recent** raceable ride of this class, rather than
     * their best (24.3.18b).
     *
     * The owner's note: *"Can we also add more things like 'Tom's last ride'
     * … it's exciting to see activity."* A best is a monument and can be two
     * years old; a last ride is **news**, and it is the row that makes the
     * board feel like other people are actually using the bike.
     *
     * `MAX(w.timestamp)` with `GROUP BY` rather than a window function, which
     * SQLite on API 24 does not have. Every other clause is
     * [householdRivals]'s, unchanged and deliberately so: a row that is not
     * raceable as somebody's best is not raceable as their latest either.
     *
     * A housemate whose latest ride *is* their best appears once —
     * `RaceCompetitor.oneRowPerRide` collapses them on the workout id, and the
     * best is the wider label.
     */
    @Query(
        """
        SELECT p.local_user_id AS localUserId,
               p.name AS name,
               w.id AS workoutId,
               w.total_output_kj AS outputKj,
               w.duration_sec AS durationSec,
               MAX(w.timestamp) AS lastRideAt,
               p.avatar AS avatar,
               p.ftp_watts AS ftpWatts
        FROM workouts w
        JOIN profiles p ON p.local_user_id = w.user_id
        WHERE w.class_id = :classId
          AND w.id != :excludingWorkoutId
          AND w.user_id != :excludingUserId
          AND w.is_complete = 1
          AND w.total_output_kj > 0
          AND p.household_visible = 1
          AND w.power_provenance = 'Measured'
        GROUP BY p.local_user_id
        ORDER BY lastRideAt DESC
        """
    )
    suspend fun householdLatestRides(
        classId: String,
        excludingWorkoutId: String,
        excludingUserId: Int
    ): List<HouseholdLatestRow>

    /**
     * The rider's own best ride of this class **before** the one being looked
     * at (16.3.4).
     *
     * Before, not best-ever, and that is the whole decision in this query. A
     * ride is compared with what the rider had already done when they rode it —
     * so the comparison on a ride from March is the same comparison next year,
     * and a personal best is not quietly measured against the ride that beat it.
     *
     * The same measured-power exclusion as [householdRivals], for the same
     * reason and now facing the rider's own history: a modelled trace drawn to
     * scale against a measured one is a fiction that looks exact.
     *
     * [sinceMs] is what makes this three of the live leaderboard's four kinds
     * of row rather than one (24.3.12): the same query with a floor on it
     * answers *your best ever*, *your best of the last twelve months* and
     * *your best of the last thirty days*. Pass 0 for no floor.
     */
    @Query(
        """
        SELECT w.id AS workoutId,
               w.total_output_kj AS outputKj,
               w.timestamp AS recordedAt
        FROM workouts w
        WHERE w.class_id = :classId
          AND w.user_id = :userId
          AND w.id != :excludingWorkoutId
          AND w.timestamp < :beforeMs
          AND w.timestamp >= :sinceMs
          AND w.is_complete = 1
          AND w.power_provenance = 'Measured'
        ORDER BY w.total_output_kj DESC
        LIMIT 1
        """
    )
    suspend fun previousBestOfClass(
        classId: String,
        userId: Int,
        excludingWorkoutId: String,
        beforeMs: Long,
        sinceMs: Long
    ): PreviousBestRow?

    /**
     * The rides whose watts the **bike measured** and whose efforts have not
     * been worked out yet (16.3.3, 16.3.3a).
     *
     * The measured gate is not decoration: a personal best derived from
     * `PowerModel` — RMSE 137 W — is a fiction filed as a record, and it would
     * sit in the same list as real ones. Same clause as [householdRivals],
     * pointed at one rider's own history.
     *
     * `power_bests_at IS NULL` is what makes this the **backfill** rather than
     * the read: rides recorded before that column existed, and any ride whose
     * scan did not happen. The list empties itself and stays empty, which is
     * how the sample scan the old shape paid for on every load of *Your FTP*
     * became a cost paid once per ride.
     *
     * **The two columns now answer one question each** (23.4.12): the
     * provenance says whether this ride's watts can be trusted as measurement,
     * and `power_bests_at` says only whether the scan has run. That is what the
     * older shape had wrong — the marker was carrying both facts, so "modelled"
     * and "not scanned yet" were the same value.
     *
     * **And a condensed ride is excluded, which is a third column answering a
     * third question** (23.4.3). `WorkoutRepository.backfillPowerFacts` has
     * always claimed a trimmed ride "is not here and cannot be", and until this
     * clause that was a claim rather than a filter: a trim leaves the lowest and
     * highest watt of every ten seconds — real rows, not an empty table — so a
     * ride trimmed *before* it was ever scanned came back here and had a
     * mean-maximal effort computed over a fifth of its seconds. That is the same
     * defect as recomputing time in zone off an outline (23.4.2), except the
     * result is filed permanently as a personal best. Null in `power_bests_at`
     * therefore keeps meaning all three things 16.3.3a says it does, and
     * *"trimmed without ever being scanned"* now stays uncounted instead of
     * acquiring a record it never set.
     */
    @Query(
        """
        SELECT w.id AS workoutId,
               w.timestamp AS recordedAt,
               c.title AS classTitle
        FROM workouts w
        LEFT JOIN class_templates c ON c.id = w.class_id
        WHERE w.user_id = :userId
          AND w.is_complete = 1
          AND w.power_bests_at IS NULL
          AND w.power_provenance = 'Measured'
          AND w.metrics_detail_sec IS NULL
        ORDER BY w.timestamp DESC
        """
    )
    suspend fun measuredRidesAwaitingBests(userId: Int): List<MeasuredRideRow>

    /** This ride's samples have been walked for mean-maximal efforts. */
    @Query("UPDATE workouts SET power_bests_at = :at WHERE id = :workoutId")
    suspend fun markPowerBestsScanned(workoutId: String, at: Long)

    /**
     * The rides that can say whether this rider's FTP is set too high (7.11).
     *
     * **The join to `workout_power_bests` is the whole point.** A downward
     * proposal needs each ride's twenty-minute effort, and recomputing one from
     * `workout_metrics` is exactly what 23.4.2 forbids: a condensed ride still
     * has real rows in that table — the lowest and highest watt of every ten
     * seconds — so the scan would return a number rather than nothing. These
     * rows were computed at finalise while the seconds still existed, and
     * `workout_power_bests`' own KDoc says it: **the existence of a row is
     * itself the claim** that the ride was measured and was scanned in time.
     *
     * `power_provenance = 'Measured'` is stated anyway, and belt-and-braces is
     * deliberate here rather than sloppy — 7.11.2 holds a downward claim about
     * a rider's body to Phase 27's bar, and this is the one gate that stops a
     * simulated ride ever reaching it.
     *
     * [sinceMs] is the cooldown the upward path has never had (AUTO_FTP.md
     * names its absence as a gap). It is the last moment the rider settled the
     * question — their FTP moving, or their answering a proposal — and evidence
     * from before it has already been answered.
     */
    @Query(
        """
        SELECT w.id AS workoutId,
               w.timestamp AS recordedAt,
               b.watts AS peak20MinWatts,
               w.avg_hr AS avgHr,
               w.max_hr_bpm AS rideMaxHrBpm,
               w.rpe_rating AS rpeRating
        FROM workouts w
        JOIN workout_power_bests b
          ON b.workout_id = w.id AND b.window_sec = :windowSec
        WHERE w.user_id = :userId
          AND w.is_complete = 1
          AND w.power_provenance = 'Measured'
          AND w.timestamp > :sinceMs
        ORDER BY w.timestamp DESC
        LIMIT :limit
        """
    )
    suspend fun ftpEvidenceRides(
        userId: Int,
        windowSec: Int,
        sinceMs: Long,
        limit: Int
    ): List<FtpEvidenceRow>

    /**
     * When this rider last answered an FTP proposal, or 0 if they never have.
     *
     * `ftp_proposal_declined` is per ride (7.10.5), and the ride's own timestamp
     * is therefore the moment. It covers a declined *breakthrough* as well as a
     * declined reduction, and that conflation is deliberate: both are the rider
     * saying *this number is right*, and the direction they said it in does not
     * make the older evidence any fresher.
     */
    @Query(
        """
        SELECT MAX(timestamp) FROM workouts
        WHERE user_id = :userId AND is_complete = 1 AND ftp_proposal_declined = 1
        """
    )
    suspend fun lastDeclinedProposalAt(userId: Int): Long?

    /**
     * Writes where a ride's watts came from onto the ride (23.4.12).
     *
     * An `UPDATE` of the one column rather than a whole-entity write, because
     * this runs immediately after the finalise has written every other column
     * off `WorkoutSession` — and the session does not carry this one, so a
     * round trip through the entity would be a second chance to hand back a
     * stale copy of something (8.3d.4).
     */
    @Query("UPDATE workouts SET power_provenance = :provenance WHERE id = :workoutId")
    suspend fun markPowerProvenance(workoutId: String, provenance: PowerProvenance)

    /**
     * Works out `power_provenance` for every finished ride that has none
     * (23.4.12), and returns how many it wrote.
     *
     * **`PowerProvenance.of` in SQL, and it must stay equal to it.** The order
     * of the branches is the whole of it: any sample nobody wrote down makes the
     * ride `Unknown` outright — one unrecorded sample means it can no longer be
     * *shown* to be measurement all the way through — and only then does an
     * absence of modelled samples mean `Measured`. A ride with no samples at all
     * is `Unknown` rather than measured, which is 22.1.7's defect fixed rather
     * than reintroduced.
     *
     * Unlike 16.3.3a's backfill this **is** expressible here, and that is the
     * only reason it is one statement instead of a walk: mean-maximal power is a
     * sliding window over a series with gaps, whereas provenance is a reduction
     * of one nullable flag. So the choice of runtime over migration is not about
     * capability (18 → 19 says why) — it is that a pass which can run again
     * covers the ride whose finalise was interrupted as well as the rides that
     * predate the column.
     *
     * `is_complete = 1` deliberately: a ride being pedalled has no final answer
     * yet, and every one of the seven queries above excludes it anyway.
     */
    @Query(
        """
        UPDATE workouts SET power_provenance = CASE
            WHEN NOT EXISTS (
                SELECT 1 FROM workout_metrics m WHERE m.workout_id = workouts.id
            ) THEN 'Unknown'
            WHEN EXISTS (
                SELECT 1 FROM workout_metrics m
                WHERE m.workout_id = workouts.id AND m.power_is_measured IS NULL
            ) THEN 'Unknown'
            WHEN NOT EXISTS (
                SELECT 1 FROM workout_metrics m
                WHERE m.workout_id = workouts.id AND m.power_is_measured = 0
            ) THEN 'Measured'
            WHEN NOT EXISTS (
                SELECT 1 FROM workout_metrics m
                WHERE m.workout_id = workouts.id AND m.power_is_measured = 1
            ) THEN 'Modelled'
            ELSE 'Mixed'
        END
        WHERE is_complete = 1 AND power_provenance IS NULL
        """
    )
    suspend fun backfillPowerProvenance(): Int

    /**
     * Finished rides still carrying no provenance — nothing but a fence.
     *
     * The invariant [backfillPowerProvenance] exists to hold: after it has run,
     * this is 0. A test asserts that rather than a comment claiming it, because
     * a ride left out of this column is a ride left off six leaderboards with
     * nothing visibly wrong.
     */
    @Query(
        "SELECT COUNT(*) FROM workouts WHERE is_complete = 1 AND power_provenance IS NULL"
    )
    suspend fun completeRidesWithoutProvenance(): Int

    /**
     * The rides old enough to trim, oldest first (PLAN 23.4.2).
     *
     * **Four clauses and three of them are somebody's defect written down.**
     *
     * `is_complete = 1` is 8.3b's rule reaching a fourth place: three resume
     * paths read a ride's samples (`recoverWorkout`,
     * `resumeInterruptedWorkout`, `interruptionFor`), and every one of them
     * operates on `is_complete = 0`. A trimmer that took an unfinished ride
     * would be deleting the record of the class somebody is pedalling right now.
     * [excludingWorkoutId] is the same guard from the other side, for the ride
     * that 12.6.2 has re-opened.
     *
     * `metrics_detail_sec IS NULL` keeps it idempotent: a ride is trimmed once
     * and never re-bucketed, which matters because trimming an outline again
     * would be a downsample of a downsample.
     *
     * And the join is **23.4.6, enforced in the trimmer** rather than in the
     * sync worker, which is 23.4.9's own finding: by the time the worker runs
     * the samples are already gone and it would upload an authoritative-looking
     * short version. So a rider with an account only has rides trimmed that the
     * cloud has taken (`synced_at`), and an offline rider — which under rule 1
     * is everybody today — is gated by nothing, because there is no copy for
     * them to be ahead of. `LEFT JOIN`, so a ride with no rider on it is still
     * eligible.
     */
    @Query(
        """
        SELECT w.id FROM workouts w
        LEFT JOIN profiles p ON p.local_user_id = w.user_id
        WHERE w.is_complete = 1
          AND w.metrics_detail_sec IS NULL
          AND w.timestamp < :cutoffMs
          AND w.id <> :excludingWorkoutId
          AND (p.auth_user_id IS NULL OR w.synced_at IS NOT NULL)
        ORDER BY w.timestamp ASC
        """
    )
    suspend fun trimmableRides(cutoffMs: Long, excludingWorkoutId: String): List<String>

    /** [trimmableRides]' own count, for the sentence Settings shows first. */
    @Query(
        """
        SELECT COUNT(*) FROM workouts w
        LEFT JOIN profiles p ON p.local_user_id = w.user_id
        WHERE w.is_complete = 1
          AND w.metrics_detail_sec IS NULL
          AND w.timestamp < :cutoffMs
          AND w.id <> :excludingWorkoutId
          AND (p.auth_user_id IS NULL OR w.synced_at IS NOT NULL)
        """
    )
    suspend fun trimmableRideCount(cutoffMs: Long, excludingWorkoutId: String): Int

    /**
     * Says a ride is now an outline, and keeps what its seconds counted
     * (23.4.3).
     *
     * One statement for both, because they are one fact: a ride whose detail
     * column said 10 while its distributions were missing would be an outline
     * that had thrown its own time-in-zone away, and the screen would have no
     * way to tell that from a ride that never had one.
     */
    @Query(
        """
        UPDATE workouts
        SET metrics_detail_sec = :detailSec, distributions_json = :distributionsJson
        WHERE id = :workoutId
        """
    )
    suspend fun markTrimmed(workoutId: String, detailSec: Int, distributionsJson: String?)

    /** How many seconds of riding this tablet is holding (23.4.1). */
    @Query("SELECT COUNT(*) FROM workout_metrics")
    suspend fun storedSampleCount(): Int

    /**
     * This rider's rides that are outlines and that a cloud has taken (15.4.2).
     *
     * The one sentence a rider deleting their cloud copy is owed beyond
     * *"nothing on this bike changes"*: for these rides the bike is holding the
     * outline and the seconds behind it are not here. 23.4.6 is why the two
     * conditions belong together — a signed-in rider's ride is only ever
     * condensed *because* the cloud had taken it, so `synced_at` is what
     * separates "the copy up there is fuller than the one down here" from a
     * ride that was condensed while the rider was offline and never had a
     * fuller copy anywhere.
     */
    @Query(
        """
        SELECT COUNT(*) FROM workouts
        WHERE user_id = :userId
          AND metrics_detail_sec IS NOT NULL
          AND synced_at IS NOT NULL
        """
    )
    suspend fun condensedSyncedRideCount(userId: Int): Int

    /** And over how many finished rides. */
    @Query("SELECT COUNT(*) FROM workouts WHERE is_complete = 1")
    suspend fun completeRideCount(): Int

    /**
     * Rides counted towards this rider's bests, and rides not.
     *
     * Asked of `power_bests_at` rather than of the samples, which is the point
     * of the column: after 23.4 has trimmed a ride the samples cannot answer
     * either question, and a rider watching the sentence *"from 22 rides the
     * bike measured, of 23"* change because of housekeeping would be right to
     * distrust the number above it.
     *
     * The pair is deliberately exhaustive — skipped is *everything not
     * counted*, so a ride with no samples at all lands in it. Under the old
     * sample-derived pair it fell out of both and was in neither total, which
     * is the same trivially-passed-`NOT EXISTS` family as 22.1.7.
     */
    @Query(
        """
        SELECT COUNT(*) FROM workouts w
        WHERE w.user_id = :userId
          AND w.is_complete = 1
          AND w.power_bests_at IS NOT NULL
        """
    )
    suspend fun ridesWithBestsCount(userId: Int): Int

    @Query(
        """
        SELECT COUNT(*) FROM workouts w
        WHERE w.user_id = :userId
          AND w.is_complete = 1
          AND w.power_bests_at IS NULL
        """
    )
    suspend fun ridesWithoutBestsCount(userId: Int): Int

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
               p.avatar AS avatar,
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
    suspend fun householdRecent(sinceMs: Long): List<HouseholdRiderRow>

    /**
     * Lifetime totals for every profile that has ever finished a ride here
     * (26.4.1), keyed by profile so a panel can look one up.
     *
     * **One grouped query rather than one per rider.** The streak beside it on
     * the same panel costs a query a head, and that is a cost worth not paying
     * twice — a household of six would otherwise be twelve round trips to draw
     * one card.
     *
     * **No `household_visible` predicate, and it is not an oversight.** This
     * says *how much has this profile ridden* and nothing about who may see it;
     * the callers filter. Putting the opt-out here would silently give a rider
     * who left the household panel a level of 1 on their own dashboard.
     */
    @Query(
        """
        SELECT user_id AS localUserId,
               COUNT(*) AS rides,
               COALESCE(SUM(duration_sec), 0) AS durationSec,
               COALESCE(SUM(total_output_kj), 0) AS outputKj
        FROM workouts
        WHERE is_complete = 1 AND user_id IS NOT NULL
        GROUP BY user_id
        """
    )
    fun observeRiderTotals(): Flow<List<RiderTotalsRow>>

    /**
     * The same totals, read once (24.3.19a).
     *
     * The live board is assembled at ride start and then never touches the
     * database again — the tick reads arrays — so it wants an answer rather
     * than a subscription. **A level that moved mid-ride would be wrong
     * anyway**: the ride that is going to move it is the one being ridden, and
     * a housemate's face changing level while somebody else pedals is a
     * distraction on the screen 11.6.8 exists to keep still.
     *
     * The query is [observeRiderTotals]'s, deliberately not a second rule about
     * what counts towards a level.
     */
    @Query(
        """
        SELECT user_id AS localUserId,
               COUNT(*) AS rides,
               COALESCE(SUM(duration_sec), 0) AS durationSec,
               COALESCE(SUM(total_output_kj), 0) AS outputKj
        FROM workouts
        WHERE is_complete = 1 AND user_id IS NOT NULL
        GROUP BY user_id
        """
    )
    suspend fun riderTotals(): List<RiderTotalsRow>

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

    /**
     * The three columns a volume trend needs, per ride (16.3.2, 16.3.5).
     *
     * Deliberately not `SELECT *` and deliberately nowhere near
     * `workout_metrics`: four months of daily riding is ~120 rows here and
     * ~320,000 samples there, and *how much and how often* is answerable from
     * the summary columns alone.
     *
     * A `Flow`, so finishing a ride redraws the screen — the query mentions only
     * `workouts`, which is the table that changes when it does.
     */
    @Query(
        """
        SELECT timestamp, duration_sec, total_output_kj FROM workouts
        WHERE user_id = :userId AND is_complete = 1 AND timestamp >= :sinceMs
        ORDER BY timestamp
        """
    )
    fun observeRideRecords(userId: Int, sinceMs: Long): Flow<List<RideRecordRow>>

    /**
     * The rides a window's time in zone could be built from (21.4.3).
     *
     * **A `Flow` that mentions `workouts` and nothing else, on purpose.** The
     * seconds themselves come from [powerSecondsForRides] below, which is a
     * one-shot query rather than an observed one — a `Flow` over
     * `workout_metrics` re-emits on every batch of samples a ride writes, so
     * observing it would re-count a month of riding a few times a minute for the
     * whole of every ride. Here the rule that usually bites (a `Flow` only
     * re-emits when a table its query *mentions* is written) is the one being
     * used: this redraws when a ride finishes, which is exactly when the answer
     * changes.
     */
    @Query(
        """
        SELECT id, timestamp, ftp_watts, metrics_detail_sec, distributions_json
        FROM workouts
        WHERE user_id = :userId AND is_complete = 1 AND timestamp >= :sinceMs
        ORDER BY timestamp
        """
    )
    fun observeRideZoneSources(userId: Int, sinceMs: Long): Flow<List<RideZoneSourceRow>>

    /**
     * Seconds by whole watt, per ride, split by whether the cranks were turning.
     *
     * See [RidePowerSecondsRow] for why the grouping is in SQL and the zone
     * boundaries are not. [pedallingRpm] is passed in rather than written here
     * for the same reason: `AutoPausePolicy` owns what counts as pedalling, and
     * a second copy of that threshold is a second answer to *was the rider
     * riding*.
     */
    @Query(
        """
        SELECT workout_id,
               CAST(ROUND(power) AS INTEGER) AS watts,
               SUM(CASE WHEN cadence >= :pedallingRpm THEN 1 ELSE 0 END) AS ridden_seconds,
               SUM(CASE WHEN cadence < :pedallingRpm THEN 1 ELSE 0 END) AS stopped_seconds
        FROM workout_metrics
        WHERE workout_id IN (:workoutIds)
        GROUP BY workout_id, CAST(ROUND(power) AS INTEGER)
        """
    )
    suspend fun powerSecondsForRides(
        workoutIds: List<String>,
        pedallingRpm: Double
    ): List<RidePowerSecondsRow>

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
     * Every total this rider has recorded on one class, for *your usual*
     * (24.3.18b).
     *
     * **Measured watts only**, by the same one clause every other raceable query
     * carries (24.4.2): a median that includes simulated rides is a target built
     * partly out of `PowerModel`, and the whole reason `power_is_measured`
     * exists is that the two must never be averaged together.
     * `total_output_kj > 0` drops the abandoned ten-second attempts that would
     * otherwise drag a median down.
     *
     * **22.1.7's defect is why this is one column rather than two subqueries.**
     * The gate used to be `EXISTS (a sample) AND NOT EXISTS (a sample that is
     * not a measurement)`, and the second half alone is passed *trivially* by a
     * ride with **no samples at all** — which is a state a ride reaches
     * honestly, since `total_output_kj` comes from the session while the samples
     * are what survived the plausibility fence. This query was missing the
     * `EXISTS` for a sitting and quietly counted such rides. Asking the row for
     * a `PowerProvenance` cannot be got half right in the same way: an
     * evidence-free ride is `Unknown` (`of(0, 0, 0)`) and no reader can spell
     * that gate two ways.
     */
    @Query(
        """
        SELECT total_output_kj FROM workouts w
        WHERE w.class_id = :classId
          AND w.user_id = :userId
          AND w.is_complete = 1
          AND w.total_output_kj > 0
          AND w.power_provenance = 'Measured'
        """
    )
    suspend fun ownTotalsForClass(userId: Int, classId: String): List<Double>

    /**
     * The same totals, minus one ride — for asking whether *that* ride was the
     * best of them (22.1.5).
     *
     * A separate query rather than a filter in Kotlin because the answer is a
     * list of doubles with no ids on it: dropping "the one equal to this ride's
     * total" would also drop a genuine earlier ride that happened to tie, and
     * a rider who matched their best to the kilojoule would be told they had
     * beaten it.
     *
     * Measured watts only, by the one clause every comparison in this app
     * carries (24.4.2) — and now literally the same clause as the other side of
     * this comparison, which is the point of 23.4.12. Both sides used to spell
     * the rule out for themselves, one of them got it wrong (22.1.7), and two
     * sides of a comparison disagreeing about what counts as measured is how a
     * rider gets told they beat something that was never ridden.
     */
    @Query(
        """
        SELECT total_output_kj FROM workouts w
        WHERE w.class_id = :classId
          AND w.user_id = :userId
          AND w.id != :excludingWorkoutId
          AND w.is_complete = 1
          AND w.total_output_kj > 0
          AND w.power_provenance = 'Measured'
        """
    )
    suspend fun ownTotalsForClassExcluding(
        userId: Int,
        classId: String,
        excludingWorkoutId: String
    ): List<Double>

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

    // `observeOutputSince` and `observeLatestWorkout` lived here and are gone
    // with the two cards they fed (22.1.2, 22.1.8). `observeLastRide` above is
    // the replacement for the second, projecting six columns rather than
    // handing a screen a whole `WorkoutEntity` to find uses for.

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
