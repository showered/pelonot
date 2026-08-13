package com.pelonot.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.pelonot.data.local.AppDatabase
import com.pelonot.data.local.dao.UserDao
import com.pelonot.data.local.dao.WorkoutDao
import com.pelonot.data.local.dao.WorkoutMetricDao
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.data.local.entity.WorkoutMetricEntity
import com.pelonot.data.service.RideInProgress
import com.pelonot.domain.chart.ChartSample
import com.pelonot.domain.chart.RideChartBuilder
import com.pelonot.domain.chart.RideDistributions
import com.pelonot.domain.model.MaxHeartRate
import com.pelonot.domain.retention.MetricTrim
import com.pelonot.domain.retention.RetentionAge
import java.io.File

/**
 * What this tablet is actually holding, and what trimming would take.
 *
 * [bytes] is the database file rather than an estimate, because 23.4.1's whole
 * point is that everything in this feature was sized off a model and a real
 * tablet can contradict it.
 */
data class StorageFacts(
    val rides: Int = 0,
    val samples: Int = 0,
    val bytes: Long = 0,
    /**
     * How many rides each age would condense **now**, one entry per age that
     * is on.
     *
     * A map rather than a single count for the rider's current choice, and
     * driving the AVD is what showed the difference: the dialog asks about the
     * age being *offered*, so a rider on `Never` — which is everybody the first
     * time — was told *"nothing is old enough yet"* about a fourteen-month-old
     * ride. The count belongs to the question, not to the setting.
     */
    val trimmableByAge: Map<RetentionAge, Int> = emptyMap()
) {
    fun trimmable(age: RetentionAge): Int = trimmableByAge[age] ?: 0
}

/** What one pass of the trimmer did. */
data class TrimResult(
    val ridesTrimmed: Int = 0,
    val samplesDropped: Int = 0
) {
    val didAnything: Boolean get() = ridesTrimmed > 0
}

/**
 * Old rides condensed to an outline, on the rider's own instruction (PLAN
 * 23.4).
 *
 * **This is the one feature in the project that destroys data on purpose**, so
 * it is a separate class rather than four more methods on `WorkoutRepository` —
 * everything that can delete a rider's seconds is in one file, with the rules
 * beside it.
 *
 * The rules, and each one is somebody's near-miss:
 *
 * 1. **Off unless the rider turned it on** (23.4.4). [RetentionAge.Never] is
 *    the default and nothing here runs under it.
 * 2. **Never an unfinished ride** — `WorkoutDao.trimmableRides` holds it, and
 *    [RideInProgress] is asked as well, because a ride re-opened by 12.6.2 is
 *    complete on disk right up until the moment it is not.
 * 3. **Never a ride the cloud has not taken, for a rider with an account**
 *    (23.4.6), and it is enforced *here* rather than in the sync worker, which
 *    is 23.4.9's finding: by the time the worker runs the samples are gone.
 * 4. **What the seconds counted is written down before they go** (23.4.2), and
 *    the ride is marked as an outline (23.4.3) in the same statement.
 *
 * And the thing 23.4.10 says must not be papered over: **for a rider with no
 * account this is deletion, not eviction.** There is no cloud copy to pull the
 * seconds back from, and this class does not pretend otherwise — nothing here
 * is called "cache". The offline-safe half is what is built; rehydration for a
 * signed-in rider is its own item and does not exist.
 */
class RetentionRepository(
    private val database: AppDatabase,
    private val workoutDao: WorkoutDao,
    private val metricDao: WorkoutMetricDao,
    private val userDao: UserDao,
    private val databaseBytes: () -> Long
) {

    constructor(context: Context, database: AppDatabase) : this(
        database = database,
        workoutDao = database.workoutDao(),
        metricDao = database.workoutMetricDao(),
        userDao = database.userDao(),
        databaseBytes = {
            // The name is asked of the open database rather than held as a
            // constant beside it — 19.1.3a is what a number kept equal to
            // another number by a comment costs, and this one would fail by
            // silently reporting 0 bytes.
            //
            // The write-ahead log is part of what the tablet is holding, and on
            // a database written to once a second it is not a rounding error.
            val db = context.getDatabasePath(database.openHelper.databaseName)
            listOf(db, File(db.path + "-wal"), File(db.path + "-shm"))
                .filter { it.exists() }
                .sumOf { it.length() }
        }
    )

    suspend fun facts(nowMs: Long = System.currentTimeMillis()): StorageFacts {
        checkpoint()
        return StorageFacts(
            rides = workoutDao.completeRideCount(),
            samples = workoutDao.storedSampleCount(),
            bytes = databaseBytes(),
            trimmableByAge = RetentionAge.entries
                .mapNotNull { age ->
                    age.cutoffMs(nowMs)?.let { cutoff ->
                        age to workoutDao.trimmableRideCount(cutoff, currentRideId())
                    }
                }
                .toMap()
        )
    }

    /**
     * Folds the write-ahead log back into the database before it is measured,
     * and after it is vacuumed.
     *
     * **Without it the figure goes up when a rider condenses their rides**,
     * which is worse than the figure not moving: `VACUUM` in WAL mode writes
     * the whole rewritten database through the log, so the first measurement
     * afterwards counts the same pages twice. Observed on the tablet AVD —
     * 436 kB before a trim and 782 kB after it, with 1,200 samples gone.
     *
     * A checkpoint is not a write to anybody's data; it is the same tidy-up
     * Room does on its own schedule, asked for at the one moment the number is
     * about to be read out loud.
     */
    private fun checkpoint() {
        runCatching {
            database.openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(TRUNCATE)")
                .use { it.moveToFirst() }
        }
    }

    /**
     * Condenses every ride old enough, and returns what it did.
     *
     * Ride by ride rather than one big statement: each one has to have its own
     * distributions computed from its own samples first, and a rider's history
     * is not a thing to hold a single transaction open across. A failure
     * therefore leaves the rides it has already done trimmed and the rest
     * untouched, which is the safe direction — every ride is independently
     * either intact or an outline that says so.
     */
    suspend fun trim(
        age: RetentionAge,
        nowMs: Long = System.currentTimeMillis()
    ): TrimResult {
        val cutoff = age.cutoffMs(nowMs) ?: return TrimResult()
        val ids = workoutDao.trimmableRides(cutoff, currentRideId())
        if (ids.isEmpty()) return TrimResult()

        var rides = 0
        var dropped = 0
        ids.forEach { id ->
            dropped += trimOne(id)
            rides++
        }

        // **Without this the file does not get smaller**, and the rider is
        // looking at a number that says so. SQLite hands a deleted row's page
        // to its own free list rather than back to the filesystem, so a trim
        // that removed 200,000 rows leaves Settings still reporting 61 MB —
        // which reads as a feature that did nothing. It runs once per pass,
        // outside the per-ride transactions (`VACUUM` cannot run inside one),
        // and only when something was actually removed.
        if (rides > 0) {
            runCatching { database.openHelper.writableDatabase.execSQL("VACUUM") }
            checkpoint()
        }

        return TrimResult(ridesTrimmed = rides, samplesDropped = dropped)
    }

    /**
     * One ride: work out what its seconds said, keep the outline, drop the rest.
     *
     * In a transaction because the three writes are one fact. A crash between
     * the delete and the mark would leave a ride with a fifth of its samples and
     * nothing saying so — which is precisely the state 23.4.3 exists to make
     * impossible, arriving by accident instead of by design.
     */
    private suspend fun trimOne(workoutId: String): Int {
        val samples = metricDao.getMetricsForWorkout(workoutId)
        val keep = MetricTrim.keep(
            samples = samples,
            timestampSec = { it.timestampSec },
            watts = { it.power }
        )

        val distributions = distributionsFor(workoutId, samples)

        database.withTransaction {
            metricDao.deleteMetricsForWorkout(workoutId)
            if (keep.isNotEmpty()) metricDao.insertMetrics(keep.map { it.copy(id = 0) })
            workoutDao.markTrimmed(
                workoutId = workoutId,
                detailSec = MetricTrim.BUCKET_SEC,
                distributionsJson = distributions?.encode()
            )
        }

        return samples.size - keep.size
    }

    /**
     * Time in zone, the cadence spread and the heart's time in zone, counted at
     * full resolution one last time (23.4.2, 21.4.1).
     *
     * The FTP is the ride's own, exactly as every other reader resolves it
     * (7.8): the rider's current number only stands in for a ride recorded
     * before that column existed, and it is stored *inside* the blob so a
     * screen can say which one the counts were made against rather than
     * implying today's.
     *
     * **The maximum heart rate has to be resolved the same way and is easy to
     * forget**, because until 21.4.1 it changed nothing here — it drew the
     * bands on a chart and this method draws nothing. Now it is a denominator,
     * and a trim run without it would freeze an empty heart-rate distribution
     * onto the row and lose the answer for good, which is precisely what this
     * whole file exists to prevent.
     *
     * Null for a ride with nothing to count, which is honest — a summary of no
     * samples is not a summary.
     */
    private suspend fun distributionsFor(
        workoutId: String,
        samples: List<WorkoutMetricEntity>
    ): RideDistributions? {
        if (samples.isEmpty()) return null

        val workout = workoutDao.getWorkoutById(workoutId) ?: return null
        val rider = workout.userId?.let { userDao.getUserById(it) }
        val ftp = workout.ftpWatts ?: rider?.ftpWatts ?: UserEntity.DEFAULT_FTP
        val maxHr = workout.maxHrBpm
            ?: rider?.let { MaxHeartRate.resolve(it.maxHrBpm, it.birthDate)?.bpm }

        val charts = RideChartBuilder.build(
            samples = samples.map {
                ChartSample(
                    timestampSec = it.timestampSec,
                    powerWatts = it.power,
                    cadenceRpm = it.cadence,
                    heartRateBpm = it.heartRate,
                    resistancePercent = it.resistance
                )
            },
            ftpWatts = ftp,
            maxHrBpm = maxHr
        )
        return RideDistributions.of(charts)
    }

    /**
     * The ride being pedalled right now, which is never eligible.
     *
     * `is_complete = 1` already excludes it and this is the belt to that
     * braces: 12.6.2 re-opens a *finished* ride, so there is a window in which
     * the row is complete on disk and the rider is on the bike. 8.3b is the same
     * guard for the same reason, and the string is never a real id when there is
     * no ride.
     */
    private fun currentRideId(): String = RideInProgress.workoutId ?: NO_RIDE

    private companion object {
        const val NO_RIDE = ""
    }
}
