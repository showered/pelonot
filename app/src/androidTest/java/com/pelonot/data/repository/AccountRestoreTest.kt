package com.pelonot.data.repository

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pelonot.data.local.AppDatabase
import com.pelonot.data.local.entity.ClassTemplateEntity
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.data.local.entity.WorkoutMetricEntity
import com.pelonot.data.remote.CloudAccess
import com.pelonot.data.remote.SupabaseSyncRepository
import com.pelonot.data.remote.SyncOutcome
import com.pelonot.data.remote.dto.MetricsPayload
import com.pelonot.data.remote.dto.ProfileDto
import com.pelonot.data.remote.dto.RideFacts
import com.pelonot.data.remote.dto.WorkoutDto
import com.pelonot.domain.chart.RideDistributions
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A rider's history coming back down (PLAN 15.3.2).
 *
 * The cloud half is three `select`s against policies verified from a second
 * account (`verify_rls.py`, 15.5.4). **Everything that can go wrong quietly is
 * on this side**, and it is the same list every time this project writes to the
 * database on behalf of something else: a foreign key that refuses the insert, a
 * row overwritten instead of added, a derived number recomputed from data that
 * has been thinned, and a date that parses to the epoch and puts a ride in 1970.
 *
 * The three network calls are lambdas, so all of this runs against a real
 * database with no endpoint anywhere near it.
 */
@RunWith(AndroidJUnit4::class)
class AccountRestoreTest {

    private lateinit var database: AppDatabase
    private lateinit var restores: RestoreRepository
    private lateinit var workouts: WorkoutRepository
    private var riderId = 0

    /** What the account is holding, in the shape the endpoint hands back. */
    private var cloudRides: List<WorkoutDto> = emptyList()
    private var cloudProfile: ProfileDto? = null
    private var idsFail = false

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        )
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()
        workouts = WorkoutRepository(
            database.workoutDao(),
            database.workoutMetricDao(),
            database.activeRideRivalDao(),
            database.workoutPowerBestDao()
        )
        riderId = database.userDao().insertUser(
            UserEntity(name = "Local Name", weightKg = 70.0, ftpWatts = 150, authUserId = ACCOUNT)
        ).toInt()
        database.classTemplateDao().insert(
            ClassTemplateEntity(
                id = "TB-01",
                title = "Twenty",
                category = "Threshold",
                durationSec = 1_200,
                intervalsJson = "[]"
            )
        )

        val userRepository = UserRepository(
            database = database,
            userDao = database.userDao(),
            ftpHistoryDao = database.ftpHistoryDao(),
            syncRepository = SupabaseSyncRepository(CloudAccess(database.userDao()))
        )
        restores = RestoreRepository(
            database = database,
            workoutDao = database.workoutDao(),
            metricDao = database.workoutMetricDao(),
            classTemplateDao = database.classTemplateDao(),
            userRepository = userRepository,
            fetchIds = {
                if (idsFail) SyncOutcome.Failed(RuntimeException("no network"))
                else SyncOutcome.Success(cloudRides.map { it.id })
            },
            fetchRides = { _, ids ->
                SyncOutcome.Success(cloudRides.filter { it.id in ids })
            },
            fetchProfile = { SyncOutcome.Success(cloudProfile) },
            now = { RESTORED_AT }
        )
    }

    @After
    fun tearDown() = database.close()

    /** A ride as the cloud holds it: the row's columns plus one payload. */
    private fun cloudRide(
        id: String,
        seconds: Int = 60,
        classId: String? = "TB-01",
        detailSec: Int? = null,
        facts: RideFacts? = null,
        payload: MetricsPayload? = null,
        recordedAt: String = "2025-07-30T18:26:40Z"
    ) = WorkoutDto(
        id = id,
        userId = ACCOUNT,
        classId = classId,
        durationSec = seconds,
        totalOutputKj = 12.0,
        totalDistanceKm = 4.0,
        avgCadence = 88.0,
        avgPower = 200.0,
        avgHr = null,
        intentModifier = 1.0,
        rpeRating = 6,
        recordedAt = recordedAt,
        powerProvenance = "Measured",
        metrics = payload ?: MetricsPayload(
            version = MetricsPayload.VERSION,
            timestampSec = (1..seconds).toList(),
            cadence = List(seconds) { 88.0 },
            resistance = List(seconds) { 40.0 },
            power = List(seconds) { 200.0 },
            powerIsMeasured = List(seconds) { true },
            detailSec = detailSec,
            ride = facts
        )
    )

    /** A ride recorded on this tablet, the way the service records one. */
    private suspend fun localRide(id: String, seconds: Int, watts: Double) {
        val workout = WorkoutEntity(
            id = id,
            userId = riderId,
            durationSec = seconds,
            totalOutputKj = watts * seconds / 1_000.0
        )
        workouts.beginWorkout(workout)
        workouts.recordMetrics(
            (1..seconds).map {
                WorkoutMetricEntity(
                    workoutId = id,
                    timestampSec = it,
                    cadence = 90.0,
                    resistance = 40.0,
                    power = watts,
                    heartRate = null,
                    powerIsMeasured = true
                )
            }
        )
        workouts.finaliseWorkout(workout)
    }

    private fun <T> outcome(result: SyncOutcome<T>): T =
        (result as SyncOutcome.Success).value

    @Test
    fun ridesInTheAccountComeDownWithTheirSeconds() = runBlocking {
        cloudRides = listOf(cloudRide("a", seconds = 60), cloudRide("b", seconds = 30))

        val restored = outcome(restores.restore(riderId))

        assertEquals(2, restored.rides)
        assertEquals(90, restored.samples)
        assertEquals(60, database.workoutMetricDao().getMetricsForWorkout("a").size)
        val row = database.workoutDao().getWorkoutById("a")
        assertEquals(riderId, row?.userId)
        assertTrue("a restored ride is a finished ride", row?.isComplete == true)
        // The date is the one it left as, to the millisecond. A ride restored at
        // the epoch would sit in January 1970 at the bottom of every list.
        assertEquals(1_753_900_000_000L, row?.timestamp)
    }

    /**
     * **Rule 2.** A restored ride is in the cloud by definition, so it is marked
     * as backed up rather than queued. Without this the next drain uploads
     * everything it just downloaded — and 23.4.6 reads a null `synced_at` as
     * *this tablet is the only copy*, which is the opposite of the truth.
     */
    @Test
    fun aRestoredRideIsAlreadyBackedUp() = runBlocking {
        cloudRides = listOf(cloudRide("a"))

        restores.restore(riderId)

        assertEquals(RESTORED_AT, database.workoutDao().getWorkoutById("a")?.syncedAt)
        assertEquals(0, database.workoutDao().unsyncedWorkouts(riderId, 10).size)
    }

    /**
     * **Rule 1, and it is the one with teeth.** `insertWorkout` is an `@Upsert`,
     * so a restore that did not check would not add a row — it would write over
     * the tablet's own record of that ride, and on a household bike the row it
     * overwrote could be a housemate's.
     */
    @Test
    fun aRideThisTabletAlreadyHasIsLeftExactlyAsItIs() = runBlocking {
        localRide("a", seconds = 120, watts = 250.0)
        cloudRides = listOf(cloudRide("a", seconds = 60))

        val restored = outcome(restores.restore(riderId))

        assertEquals(0, restored.rides)
        assertEquals(120, database.workoutMetricDao().getMetricsForWorkout("a").size)
        assertEquals(30.0, database.workoutDao().getWorkoutById("a")?.totalOutputKj ?: 0.0, 0.001)
    }

    /** And therefore it is safe to run twice, which a rider on a slow bike will. */
    @Test
    fun restoringTwiceBringsNothingDownTheSecondTime() = runBlocking {
        cloudRides = listOf(cloudRide("a"), cloudRide("b"))

        restores.restore(riderId)
        val second = outcome(restores.restore(riderId))

        assertEquals(0, second.rides)
        assertEquals(2, database.workoutDao().completedCountFor(riderId))
    }

    /**
     * A condensed ride comes back **saying it is one**, and carrying what its
     * seconds counted (23.4.2, 23.4.14).
     *
     * Without either half, `RideChartBuilder` would recount time in zone from
     * the outline's rows and report that a 25-minute ride pedalled for five —
     * the fabricated-record failure that the whole of 23.4 is written around,
     * arriving through a door 23.4 never had.
     */
    @Test
    fun aCondensedRideBringsItsOwnCountsWithIt() = runBlocking {
        cloudRides = listOf(
            cloudRide(
                id = "outline",
                seconds = 12,
                detailSec = 10,
                facts = RideFacts(
                    ftpWatts = 214,
                    distributions = RideDistributions(
                        secondsByZone = mapOf("Z4" to 1_464),
                        secondsByCadenceBand = mapOf(80 to 1_400),
                        ftpWatts = 214
                    )
                )
            )
        )

        restores.restore(riderId)

        val row = database.workoutDao().getWorkoutById("outline")
        assertEquals(10, row?.metricsDetailSec)
        assertEquals(214, row?.ftpWatts)
        assertEquals(
            1_464,
            RideDistributions.decode(row?.distributionsJson)?.secondsByZone?.get("Z4")
        )
    }

    /**
     * A ride of a class this build's library does not carry comes back as a
     * ride, not as an error.
     *
     * `workouts.class_id` is a foreign key onto `class_templates`, so the
     * alternative is not a smaller ride: the insert fails and the ride is lost
     * altogether. Classes are retired rather than deleted when a ride points at
     * one (23.2.6c), so the commonest case here is a bundle that has moved on.
     */
    @Test
    fun aRideWhoseClassIsNotOnThisBikeStillComesDown() = runBlocking {
        cloudRides = listOf(cloudRide("a", classId = "GONE-99"), cloudRide("b"))

        val restored = outcome(restores.restore(riderId))

        assertEquals(2, restored.rides)
        assertEquals(1, restored.classesNotHere)
        assertNull(database.workoutDao().getWorkoutById("a")?.classId)
        assertEquals("TB-01", database.workoutDao().getWorkoutById("b")?.classId)
    }

    /**
     * **Rule 3.** A payload whose columns disagree was truncated or written by
     * something that did not understand it, so sample 900's power lines up with
     * sample 900's cadence only by luck — the same failure as 2.7, and the same
     * answer: reject, do not repair. The ride is left in the account and the
     * rider is told a number rather than shown a ride missing without comment.
     */
    @Test
    fun aRideWithAnUnreadableRecordIsSkippedWholeAndCounted() = runBlocking {
        cloudRides = listOf(
            cloudRide(
                id = "broken",
                payload = MetricsPayload(
                    version = 1,
                    timestampSec = listOf(1, 2, 3),
                    cadence = listOf(80.0),
                    resistance = listOf(40.0, 40.0, 40.0),
                    power = listOf(200.0, 200.0, 200.0)
                )
            ),
            cloudRide("fine")
        )

        val restored = outcome(restores.restore(riderId))

        assertEquals(1, restored.rides)
        assertEquals(1, restored.unreadable)
        assertNull("a half-written ride must not land", database.workoutDao().getWorkoutById("broken"))
        assertNotNull(database.workoutDao().getWorkoutById("fine"))
    }

    /** A date that will not parse is the same claim, and the same answer. */
    @Test
    fun aRideWithAnUnreadableDateIsSkippedRatherThanFiledIn1970() = runBlocking {
        cloudRides = listOf(cloudRide("undated", recordedAt = "sometime last year"))

        val restored = outcome(restores.restore(riderId))

        assertEquals(0, restored.rides)
        assertEquals(1, restored.unreadable)
        assertNull(database.workoutDao().getWorkoutById("undated"))
    }

    /**
     * **Rule 4.** The account's name, weight and FTP are taken by a profile that
     * has never ridden on this bike — the new-device case, and the only one with
     * nothing to lose.
     */
    @Test
    fun aProfileThatHasNeverRiddenHereAdoptsTheAccountsOwn() = runBlocking {
        cloudProfile = ProfileDto(id = ACCOUNT, name = "Simon", ftpWatts = 214, weightKg = 78.0)
        cloudRides = listOf(cloudRide("a"))

        val restored = outcome(restores.restore(riderId))

        assertTrue(restored.profileAdopted)
        val user = database.userDao().getUserById(riderId)
        assertEquals("Simon", user?.name)
        assertEquals(214, user?.ftpWatts)
        // 7.9.4's funnel: the change is on the trend with a reason on it, not
        // an FTP that moved while nobody was looking.
        assertEquals(
            "PulledFromCloud",
            database.ftpHistoryDao().forUser(riderId).last().source
        )
    }

    /**
     * And a rider who has ridden here keeps their own numbers.
     *
     * The cloud profile carries no timestamp, so "last write wins" cannot be
     * asked of it — and an FTP typed into Settings on this bike after the last
     * upload is exactly what would be silently undone. Same family as 7.9's
     * read-modify-write defect: two writers, one row, the later one carrying a
     * stale copy.
     */
    @Test
    fun aProfileThatHasRiddenHereKeepsItsOwnNumbers() = runBlocking {
        localRide("already-here", seconds = 60, watts = 200.0)
        cloudProfile = ProfileDto(id = ACCOUNT, name = "Simon", ftpWatts = 214, weightKg = 78.0)
        cloudRides = listOf(cloudRide("a"))

        val restored = outcome(restores.restore(riderId))

        assertFalse(restored.profileAdopted)
        assertEquals(150, database.userDao().getUserById(riderId)?.ftpWatts)
        assertEquals("Local Name", database.userDao().getUserById(riderId)?.name)
        // The rides still came down; only the profile was left alone.
        assertEquals(1, restored.rides)
    }

    @Test
    fun theSurveySaysWhatIsUpThereAndWhatIsMissing() = runBlocking {
        localRide("a", seconds = 60, watts = 200.0)
        cloudRides = listOf(cloudRide("a"), cloudRide("b"), cloudRide("c"))

        val survey = outcome(restores.survey(riderId))

        assertEquals(3, survey.inCloud)
        assertEquals(2, survey.missingHere)
        assertTrue(survey.hasSomethingToBringDown)
    }

    /** A network failure is a failure, not an empty account. */
    @Test
    fun anUnreachableAccountIsNotAnEmptyOne() = runBlocking {
        idsFail = true

        assertTrue(restores.survey(riderId) is SyncOutcome.Failed)
        assertTrue(restores.restore(riderId) is SyncOutcome.Failed)
    }

    private companion object {
        const val ACCOUNT = "00000000-0000-0000-0000-0000000000aa"
        const val RESTORED_AT = 1_800_000_000_000L
    }
}
