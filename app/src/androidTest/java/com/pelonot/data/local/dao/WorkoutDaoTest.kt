package com.pelonot.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pelonot.data.local.AppDatabase
import com.pelonot.data.local.entity.ClassTemplateEntity
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.data.local.entity.WorkoutMetricEntity
import com.pelonot.domain.model.PowerProvenance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DAO behaviour against a real in-memory SQLite database.
 *
 * The previous version of this file did not compile: it constructed
 * `WorkoutEntity` with `localUserId`, `classTemplateId` and a String
 * `intentModifier`, none of which have ever been fields on that entity. PLAN
 * item 8.8 was ticked regardless.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var workoutDao: WorkoutDao
    private lateinit var metricDao: WorkoutMetricDao
    private lateinit var userDao: UserDao

    /**
     * Note the explicit `Unit`: `= runBlocking { … }` infers its type from the
     * last expression, and `insertUser` returns the new row id. JUnit rejects a
     * `@Before` that does not return void, so this whole class failed to
     * initialise and **every test in it silently never ran** — while PLAN item
     * 8.8 was ticked. Same failure mode as the previous version of this file,
     * which did not compile at all.
     */
    @Before
    fun setup(): Unit = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        workoutDao = database.workoutDao()
        metricDao = database.workoutMetricDao()
        userDao = database.userDao()

        // workouts.user_id is a foreign key, so profiles must exist first.
        userDao.insertUser(UserEntity(localUserId = USER_ID, name = "Test Rider"))
        userDao.insertUser(UserEntity(localUserId = OTHER_USER_ID, name = "Housemate"))

        // workouts.class_id is one too, and the household leaderboard is
        // per class.
        database.classTemplateDao().upsertAll(
            listOf(CLASS_ID, "SS-04").map { id ->
                ClassTemplateEntity(
                    id = id,
                    title = id,
                    category = "Threshold",
                    durationSec = 1800,
                    intervalsJson = "[]"
                )
            }
        )
    }

    @After
    fun teardown() = database.close()

    private fun workout(
        id: String,
        userId: Int? = USER_ID,
        durationSec: Int = 1800,
        outputKj: Double = 150.0,
        isComplete: Boolean = true,
        timestamp: Long = System.currentTimeMillis(),
        classId: String? = null
    ) = WorkoutEntity(
        id = id,
        userId = userId,
        classId = classId,
        durationSec = durationSec,
        totalOutputKj = outputKj,
        totalDistanceKm = 10.0,
        avgCadence = 90.0,
        avgPower = 200.0,
        avgHr = 150.0,
        intentModifier = 1.05,
        isComplete = isComplete,
        timestamp = timestamp
    )

    @Test
    fun insertAndReadBackAWorkout() = runBlocking {
        workoutDao.insertWorkout(workout("w1"))

        val retrieved = workoutDao.getWorkoutById("w1")

        assertNotNull(retrieved)
        assertEquals(1800, retrieved!!.durationSec)
        assertEquals(150.0, retrieved.totalOutputKj, 0.001)
        assertEquals(USER_ID, retrieved.userId)
    }

    @Test
    fun metricsCanBeWrittenOnceTheParentWorkoutExists() = runBlocking {
        // Regression: metrics were previously written every second during a
        // ride while the workout row was only inserted at the end, so this
        // foreign key was always violated and no time series was ever stored.
        workoutDao.insertWorkout(workout("w1", isComplete = false))

        metricDao.insertMetrics(
            (0 until 10).map { second ->
                WorkoutMetricEntity(
                    workoutId = "w1",
                    timestampSec = second,
                    cadence = 90.0,
                    resistance = 40.0,
                    power = 200.0,
                    heartRate = 140
                )
            }
        )

        assertEquals(10, metricDao.getMetricCountForWorkout("w1"))
    }

    @Test
    fun deletingAWorkoutCascadesToItsMetrics() = runBlocking {
        workoutDao.insertWorkout(workout("w1"))
        metricDao.insertMetric(
            WorkoutMetricEntity(workoutId = "w1", timestampSec = 0, power = 100.0)
        )

        workoutDao.deleteWorkout("w1")

        assertEquals(0, metricDao.getMetricCountForWorkout("w1"))
    }

    @Test
    fun inProgressRidesAreExcludedFromHistoryAndLeaderboards() = runBlocking {
        workoutDao.insertWorkout(workout("finished", outputKj = 150.0))
        workoutDao.insertWorkout(workout("in-progress", outputKj = 0.0, isComplete = false))

        val history = workoutDao.getWorkoutsByUser(USER_ID).first()
        assertEquals(listOf("finished"), history.map { it.id })

        // Without the is_complete filter, the 0 kJ in-progress ride would drag
        // the personal average down.
        assertEquals(150.0, workoutDao.getPersonalAverageOutput(USER_ID, 0, 9999)!!, 0.001)
    }

    @Test
    fun personalBestOnlyConsidersComparableDurations() = runBlocking {
        workoutDao.insertWorkout(workout("30min", durationSec = 1800, outputKj = 150.0))
        workoutDao.insertWorkout(workout("90min", durationSec = 5400, outputKj = 400.0))

        val best = workoutDao.getPersonalBestOutput(USER_ID, 1620, 1980)

        assertEquals(150.0, best!!, 0.001)
    }

    @Test
    fun householdBestSpansEveryProfile() = runBlocking {
        workoutDao.insertWorkout(workout("mine", outputKj = 150.0))
        workoutDao.insertWorkout(workout("theirs", userId = OTHER_USER_ID, outputKj = 220.0))

        assertEquals(150.0, workoutDao.getPersonalBestOutput(USER_ID, 0, 9999)!!, 0.001)
        assertEquals(220.0, workoutDao.getHouseholdBestOutput(0, 9999)!!, 0.001)
    }

    @Test
    fun onlyAnUnfinishedRideIsOfferedForRecovery() = runBlocking {
        workoutDao.insertWorkout(workout("finished"))

        // Regression: the old query was `ORDER BY timestamp DESC LIMIT 1` with
        // no completion filter, so the app offered to resume the ride the user
        // had just finished, on every single launch.
        assertNull(workoutDao.getIncompleteWorkout(excludingId = null))

        workoutDao.insertWorkout(
            workout("crashed", isComplete = false, timestamp = System.currentTimeMillis() + 1000)
        )
        assertEquals("crashed", workoutDao.getIncompleteWorkout(excludingId = null)?.id)

        workoutDao.deleteIncompleteWorkouts(excludingId = null)
        assertNull(workoutDao.getIncompleteWorkout(excludingId = null))
        assertNotNull(workoutDao.getWorkoutById("finished"))
    }

    /**
     * 8.3b. A ride in progress is `is_complete = 0` exactly like a crashed one,
     * and the app used to offer to discard the class the rider was still on —
     * then delete the row out from under the running service.
     */
    @Test
    fun theRideInProgressIsNeitherOfferedForRecoveryNorDeleted() = runBlocking {
        val now = System.currentTimeMillis()
        workoutDao.insertWorkout(workout("crashed", isComplete = false, timestamp = now - 1000))
        workoutDao.insertWorkout(workout("live", isComplete = false, timestamp = now))

        // Newest first would otherwise hand back the live ride every time.
        assertEquals("crashed", workoutDao.getIncompleteWorkout(excludingId = "live")?.id)

        workoutDao.deleteIncompleteWorkouts(excludingId = "live")
        assertNull(workoutDao.getIncompleteWorkout(excludingId = "live"))
        assertNotNull(workoutDao.getWorkoutById("live"))
    }

    /**
     * The null case is spelled out in the SQL because `id != NULL` is never
     * true: written the obvious way, excluding nothing would exclude
     * everything and no ride could ever be recovered.
     */
    @Test
    fun excludingNothingStillFindsTheCrashedRide() = runBlocking {
        workoutDao.insertWorkout(workout("crashed", isComplete = false))

        assertEquals("crashed", workoutDao.getIncompleteWorkout(excludingId = null)?.id)
    }

    @Test
    fun recentWorkoutsAreOrderedNewestFirst() = runBlocking {
        val now = System.currentTimeMillis()
        workoutDao.insertWorkout(workout("old", timestamp = now - 10_000))
        workoutDao.insertWorkout(workout("new", timestamp = now))

        val recent = workoutDao.getRecentWorkouts(USER_ID, limit = 10)

        assertEquals(listOf("new", "old"), recent.map { it.id })
    }

    @Test
    fun rpeCanBeRecordedAfterTheRide() = runBlocking {
        workoutDao.insertWorkout(workout("w1"))

        workoutDao.setRpeRating("w1", 7)

        assertEquals(7, workoutDao.getWorkoutById("w1")?.rpeRating)
    }

    @Test
    fun historyJoinsTheClassTitleAndSkipsInProgressRides() = runBlocking {
        database.classTemplateDao().insert(
            ClassTemplateEntity(
                id = "TB-01",
                title = "Tabata Sprint 20",
                category = "Tabata Bursts",
                durationSec = 1200,
                intervalsJson = "[]"
            )
        )
        workoutDao.insertWorkout(workout("classRide").copy(classId = "TB-01"))
        workoutDao.insertWorkout(workout("justRide"))
        workoutDao.insertWorkout(workout("crashed", isComplete = false))

        val history = workoutDao.observeHistory(USER_ID, limit = 50).first()

        assertEquals(setOf("classRide", "justRide"), history.map { it.id }.toSet())
        assertEquals(
            "Tabata Sprint 20",
            history.first { it.id == "classRide" }.classTitle
        )
        // A Just Ride has no class, and the row says so rather than blank.
        assertNull(history.first { it.id == "justRide" }.classTitle)
        assertEquals("Just Ride", history.first { it.id == "justRide" }.displayTitle)
    }

    @Test
    fun historyWindowTakesTheNewestRidesAndTheCountKnowsTheRest() = runBlocking {
        val now = System.currentTimeMillis()
        repeat(5) { index ->
            workoutDao.insertWorkout(workout("w$index", timestamp = now + index * 1000L))
        }

        val page = workoutDao.observeHistory(USER_ID, limit = 2).first()

        assertEquals(listOf("w4", "w3"), page.map { it.id })
        assertEquals(5, workoutDao.observeCompletedCount(USER_ID).first())
    }

    /**
     * 12.4.1. The defect this closes is not that re-filing was awkward — it is
     * that a guest ride appeared in **no** list at all, because every other
     * query on this table is filtered to a profile. So the assertion worth
     * making is the pair: absent from both riders' history, present in the one
     * owner-less query, and moving from the second to the first when it is
     * claimed.
     */
    @Test
    fun anUnclaimedRideIsInNobodysHistoryUntilSomebodyClaimsIt() = runBlocking {
        workoutDao.insertWorkout(workout("mine"))
        workoutDao.insertWorkout(workout("guest", userId = null))
        // An unfinished guest ride is the one being pedalled right now, or a
        // crashed one — never an open question to put in front of a rider.
        workoutDao.insertWorkout(workout("guestInProgress", userId = null, isComplete = false))

        assertEquals(listOf("mine"), workoutDao.observeHistory(USER_ID, 50).first().map { it.id })
        assertEquals(emptyList<String>(), workoutDao.observeHistory(OTHER_USER_ID, 50).first().map { it.id })
        assertEquals(listOf("guest"), workoutDao.observeUnclaimedRides(50).first().map { it.id })

        workoutDao.assignWorkoutToUser("guest", OTHER_USER_ID)

        assertEquals(emptyList<String>(), workoutDao.observeUnclaimedRides(50).first().map { it.id })
        assertEquals(
            listOf("guest"),
            workoutDao.observeHistory(OTHER_USER_ID, 50).first().map { it.id }
        )
        // And it did not land on anybody else on the way past.
        assertEquals(listOf("mine"), workoutDao.observeHistory(USER_ID, 50).first().map { it.id })
    }

    /**
     * 12.3.4. The cascade is declared on the entity, but a declaration only
     * takes effect if SQLite has foreign keys switched on for the connection —
     * and an orphaned metric series is invisible from every screen and grows
     * forever. This asserts the pragma directly rather than trusting the
     * framework default.
     */
    @Test
    fun foreignKeysAreEnforcedOnTheRealConnection() = runBlocking {
        database.openHelper.readableDatabase
            .query("PRAGMA foreign_keys")
            .use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }

        workoutDao.insertWorkout(workout("w1"))
        metricDao.insertMetrics(
            (0 until 20).map {
                WorkoutMetricEntity(workoutId = "w1", timestampSec = it, power = 200.0)
            }
        )

        workoutDao.deleteWorkout("w1")

        assertEquals(0, metricDao.getMetricCountForWorkout("w1"))
    }

    @Test
    fun powerTimeSeriesComesBackInChronologicalOrder() = runBlocking {
        workoutDao.insertWorkout(workout("w1"))
        // Inserted out of order on purpose.
        metricDao.insertMetrics(
            listOf(
                WorkoutMetricEntity(workoutId = "w1", timestampSec = 2, power = 300.0),
                WorkoutMetricEntity(workoutId = "w1", timestampSec = 0, power = 100.0),
                WorkoutMetricEntity(workoutId = "w1", timestampSec = 1, power = 200.0)
            )
        )

        assertEquals(listOf(100.0, 200.0, 300.0), metricDao.getPowerTimeSeries("w1"))
    }

    // ── The household leaderboard (24.1) ────────────────────────────

    /**
     * Samples for a ride, all of one provenance.
     *
     * `null` is what a ride recorded before `power_is_measured` existed holds,
     * and it is not the same claim as `false` — but the leaderboard treats
     * both the same way, which is what these tests are checking.
     */
    /**
     * The DAO, after the provenance pass the app runs at launch (23.4.12).
     *
     * Every board below is gated on `workouts.power_provenance`, which is
     * written from the samples once rather than reduced from them on each read.
     * So the fixtures still say what they always said — *these samples, this
     * provenance* — and this call is the step in between, which means each test
     * checks the reduction and the board together instead of the board alone.
     * It is idempotent and matches nothing on a second call, exactly as at
     * launch.
     */
    private suspend fun settled(): WorkoutDao {
        workoutDao.backfillPowerProvenance()
        return workoutDao
    }

    private suspend fun samplesFor(workoutId: String, measured: Boolean?, count: Int = 5) {
        metricDao.insertMetrics(
            (0 until count).map { second ->
                WorkoutMetricEntity(
                    workoutId = workoutId,
                    timestampSec = second,
                    power = 200.0,
                    powerIsMeasured = measured
                )
            }
        )
    }

    @Test
    fun theHouseholdBoardRanksEachRidersBestRideOfOneClass() = runBlocking {
        workoutDao.insertWorkout(workout("mine-1", outputKj = 150.0, classId = CLASS_ID))
        samplesFor("mine-1", measured = true)
        workoutDao.insertWorkout(workout("mine-2", outputKj = 190.0, classId = CLASS_ID))
        samplesFor("mine-2", measured = true)
        workoutDao.insertWorkout(
            workout("theirs", userId = OTHER_USER_ID, outputKj = 240.0, classId = CLASS_ID)
        )
        samplesFor("theirs", measured = true)

        val board = settled().householdLeaderboard(CLASS_ID)

        // One row per rider, not per ride, and my *best* is the one that counts.
        assertEquals(2, board.size)
        assertEquals(OTHER_USER_ID, board.first().localUserId)
        assertEquals(240.0, board.first().bestOutputKj, 0.001)
        assertEquals(190.0, board.first { it.localUserId == USER_ID }.bestOutputKj, 0.001)
    }

    /** 24.1.4: a guest ride has no owner, so there is nobody to place. */
    @Test
    fun theHouseholdBoardExcludesGuestRides() = runBlocking {
        workoutDao.insertWorkout(workout("mine", classId = CLASS_ID))
        samplesFor("mine", measured = true)
        workoutDao.insertWorkout(workout("guest", userId = null, outputKj = 999.0, classId = CLASS_ID))
        samplesFor("guest", measured = true)

        val board = settled().householdLeaderboard(CLASS_ID)

        assertEquals(listOf(USER_ID), board.map { it.localUserId })
    }

    /**
     * 24.4.2, and the reason the `power_is_measured` column exists. A
     * simulated ride's watts are `PowerModel`'s output; ranking one beside a
     * measured ride would put a rider up against a number the app invented.
     */
    @Test
    fun theHouseholdBoardExcludesRidesWhosePowerWasNotMeasured() = runBlocking {
        workoutDao.insertWorkout(workout("measured", classId = CLASS_ID))
        samplesFor("measured", measured = true)
        workoutDao.insertWorkout(
            workout("simulated", userId = OTHER_USER_ID, outputKj = 999.0, classId = CLASS_ID)
        )
        samplesFor("simulated", measured = false)

        val board = settled().householdLeaderboard(CLASS_ID)

        assertEquals(listOf(USER_ID), board.map { it.localUserId })
    }

    @Test
    fun theHouseholdBoardExcludesARideRecordedBeforeProvenanceWasKept() = runBlocking {
        workoutDao.insertWorkout(workout("measured", classId = CLASS_ID))
        samplesFor("measured", measured = true)
        workoutDao.insertWorkout(
            workout("historic", userId = OTHER_USER_ID, outputKj = 999.0, classId = CLASS_ID)
        )
        samplesFor("historic", measured = null)

        assertEquals(
            listOf(USER_ID),
            settled().householdLeaderboard(CLASS_ID).map { it.localUserId }
        )
    }

    /**
     * One unmeasured second is enough. A ride the board would otherwise rank
     * cannot be shown to be measurement all the way through, and half a ride
     * of invented watts still moves a total.
     */
    @Test
    fun oneUnmeasuredSampleDisqualifiesTheWholeRide() = runBlocking {
        workoutDao.insertWorkout(workout("mostly-measured", classId = CLASS_ID))
        samplesFor("mostly-measured", measured = true, count = 100)
        metricDao.insertMetric(
            WorkoutMetricEntity(
                workoutId = "mostly-measured",
                timestampSec = 100,
                power = 200.0,
                powerIsMeasured = false
            )
        )

        assertEquals(emptyList<Int>(), settled().householdLeaderboard(CLASS_ID).map { it.localUserId })
    }

    @Test
    fun theHouseholdBoardIgnoresOtherClassesAndUnfinishedRides() = runBlocking {
        workoutDao.insertWorkout(workout("this-class", classId = CLASS_ID))
        samplesFor("this-class", measured = true)
        workoutDao.insertWorkout(
            workout("other-class", userId = OTHER_USER_ID, outputKj = 999.0, classId = "SS-04")
        )
        samplesFor("other-class", measured = true)
        workoutDao.insertWorkout(
            workout(
                "in-progress",
                userId = OTHER_USER_ID,
                outputKj = 999.0,
                classId = CLASS_ID,
                isComplete = false
            )
        )
        samplesFor("in-progress", measured = true)

        assertEquals(
            listOf(USER_ID),
            settled().householdLeaderboard(CLASS_ID).map { it.localUserId }
        )
    }

    // ── A ride with no samples at all (22.1.7) ──────────────────────
    //
    // The gap these three pin is a *trivially true* `NOT EXISTS`. Every
    // measured-power gate in this app is phrased "no sample here is
    // unmeasured", and a ride carrying no samples whatever satisfies that on
    // no evidence at all — so it arrives as measurement all the way through
    // and gets ranked, raced and averaged against rides somebody really rode.
    //
    // It is not a hypothetical shape: `total_output_kj` is written by the
    // finalise out of the session, while the samples are whatever survived the
    // plausibility fence, and 2.7's `TelemetryAssembler` throws away four
    // seconds of readings on every rejection. The other side of the same
    // comparison already refuses — `PowerProvenance.of(0, 0, 0)` is `Unknown`,
    // never `Measured` — and two halves of one comparison disagreeing about
    // what counts as measured is how a rider is told they beat something that
    // was never ridden.

    @Test
    fun theHouseholdBoardExcludesARideWithNoSamplesAtAll() = runBlocking {
        workoutDao.insertWorkout(workout("measured", classId = CLASS_ID))
        samplesFor("measured", measured = true)
        workoutDao.insertWorkout(
            workout("no-evidence", userId = OTHER_USER_ID, outputKj = 999.0, classId = CLASS_ID)
        )
        // Deliberately no `samplesFor` call: the row exists, the total is on
        // it, and nothing was ever recorded underneath it.

        assertEquals(
            listOf(USER_ID),
            settled().householdLeaderboard(CLASS_ID).map { it.localUserId }
        )
    }

    /** 24.3.18b — the same gap on the query that feeds the *usual* ghost. */
    @Test
    fun theUsualTotalsExcludeARideWithNoSamplesAtAll() = runBlocking {
        workoutDao.insertWorkout(workout("measured", outputKj = 150.0, classId = CLASS_ID))
        samplesFor("measured", measured = true)
        workoutDao.insertWorkout(workout("no-evidence", outputKj = 999.0, classId = CLASS_ID))

        assertEquals(
            listOf(150.0),
            settled().ownTotalsForClass(userId = USER_ID, classId = CLASS_ID)
        )
    }

    /** 22.1.5 — and the sibling query, which has carried the `EXISTS` since. */
    @Test
    fun theStandingTotalsExcludeARideWithNoSamplesAtAll() = runBlocking {
        workoutDao.insertWorkout(workout("measured", outputKj = 150.0, classId = CLASS_ID))
        samplesFor("measured", measured = true)
        workoutDao.insertWorkout(workout("no-evidence", outputKj = 999.0, classId = CLASS_ID))
        workoutDao.insertWorkout(workout("this-one", outputKj = 200.0, classId = CLASS_ID))
        samplesFor("this-one", measured = true)

        // Without the `EXISTS`, the 999 kJ phantom is in this list and today's
        // genuinely-best ride is told it came second.
        assertEquals(
            listOf(150.0),
            settled().ownTotalsForClassExcluding(
                userId = USER_ID,
                classId = CLASS_ID,
                excludingWorkoutId = "this-one"
            )
        )
    }

    /**
     * And the gate still passes the ordinary case, which is the half a fence
     * this tight can quietly break: a ride with samples, all measured, counts.
     */
    @Test
    fun theUsualTotalsStillIncludeAnOrdinaryMeasuredRide() = runBlocking {
        workoutDao.insertWorkout(workout("one", outputKj = 150.0, classId = CLASS_ID))
        samplesFor("one", measured = true)
        workoutDao.insertWorkout(workout("two", outputKj = 190.0, classId = CLASS_ID))
        samplesFor("two", measured = true)

        assertEquals(
            listOf(150.0, 190.0),
            settled().ownTotalsForClass(userId = USER_ID, classId = CLASS_ID).sorted()
        )
    }

    // ── Riding against a housemate (24.3.1) ─────────────────────────

    @Test
    fun theRivalQueryReturnsEachHousematesBestRideOfTheClass() = runBlocking {
        workoutDao.insertWorkout(workout("mine", classId = CLASS_ID))
        samplesFor("mine", measured = true)
        workoutDao.insertWorkout(
            workout("theirs-worse", userId = OTHER_USER_ID, outputKj = 120.0, classId = CLASS_ID)
        )
        samplesFor("theirs-worse", measured = true)
        workoutDao.insertWorkout(
            workout("theirs-best", userId = OTHER_USER_ID, outputKj = 240.0, classId = CLASS_ID)
        )
        samplesFor("theirs-best", measured = true)

        val rivals = settled().householdRivals(CLASS_ID, "mine", USER_ID)

        // One row per rider, and it carries the *ride* — a trace needs a
        // workout id, which is the whole reason this is not the board query.
        assertEquals(1, rivals.size)
        assertEquals("theirs-best", rivals.first().workoutId)
        assertEquals(240.0, rivals.first().outputKj, 0.001)
    }

    /**
     * The bare-column form this query relies on: with `MAX()` over a
     * `GROUP BY`, SQLite takes the other columns from the row the maximum came
     * from. Worth a test of its own — it is a documented SQLite guarantee
     * rather than standard SQL, and if it ever stopped holding the screen would
     * draw the *wrong ride* with the right number beside it, which is the kind
     * of defect nobody would spot.
     */
    @Test
    fun theRivalRowsWorkoutIdBelongsToTheRideItsOutputCameFrom() = runBlocking {
        workoutDao.insertWorkout(workout("mine", classId = CLASS_ID))
        samplesFor("mine", measured = true)
        // Inserted worst-last, so a query taking "any row of the group" would
        // pick this one and be caught.
        workoutDao.insertWorkout(
            workout("their-big", userId = OTHER_USER_ID, outputKj = 300.0,
                durationSec = 1800, classId = CLASS_ID)
        )
        samplesFor("their-big", measured = true)
        workoutDao.insertWorkout(
            workout("their-small", userId = OTHER_USER_ID, outputKj = 90.0,
                durationSec = 900, classId = CLASS_ID)
        )
        samplesFor("their-small", measured = true)

        val rival = settled().householdRivals(CLASS_ID, "mine", USER_ID).single()

        assertEquals("their-big", rival.workoutId)
        assertEquals(1800, rival.durationSec)
    }

    @Test
    fun theRivalQueryExcludesYourOwnOtherRidesOfTheSameClass() = runBlocking {
        workoutDao.insertWorkout(workout("mine", classId = CLASS_ID))
        samplesFor("mine", measured = true)
        workoutDao.insertWorkout(workout("mine-earlier", outputKj = 999.0, classId = CLASS_ID))
        samplesFor("mine-earlier", measured = true)

        // Beating yourself is a personal history, not a household comparison —
        // and it is 12.2's screen, not this one.
        assertEquals(emptyList<String>(), settled().householdRivals(CLASS_ID, "mine", USER_ID)
            .map { it.workoutId })
    }

    @Test
    fun theRivalQueryExcludesGuestsAndUnmeasuredRides() = runBlocking {
        workoutDao.insertWorkout(workout("mine", classId = CLASS_ID))
        samplesFor("mine", measured = true)
        workoutDao.insertWorkout(
            workout("guest", userId = null, outputKj = 999.0, classId = CLASS_ID)
        )
        samplesFor("guest", measured = true)
        workoutDao.insertWorkout(
            workout("simulated", userId = OTHER_USER_ID, outputKj = 999.0, classId = CLASS_ID)
        )
        samplesFor("simulated", measured = false)

        assertEquals(emptyList<String>(), settled().householdRivals(CLASS_ID, "mine", USER_ID)
            .map { it.workoutId })
    }

    /** 24.2.3's opt-out gates this too, through the same column. */
    @Test
    fun theRivalQueryRespectsTheHouseholdOptOut() = runBlocking {
        workoutDao.insertWorkout(workout("mine", classId = CLASS_ID))
        samplesFor("mine", measured = true)
        workoutDao.insertWorkout(
            workout("theirs", userId = OTHER_USER_ID, outputKj = 240.0, classId = CLASS_ID)
        )
        samplesFor("theirs", measured = true)
        assertEquals(1, settled().householdRivals(CLASS_ID, "mine", USER_ID).size)

        userDao.insertUser(
            UserEntity(
                localUserId = OTHER_USER_ID,
                name = "Housemate",
                householdVisible = false
            )
        )

        assertEquals(emptyList<String>(), settled().householdRivals(CLASS_ID, "mine", USER_ID)
            .map { it.workoutId })
    }

    /**
     * The backlog is what the cloud has not got, oldest first (PLAN 14.2.4,
     * 14.2.5).
     *
     * Oldest first is not a preference. A backlog drained newest-first leaves
     * the oldest rides permanently at the back of a queue that keeps being
     * overtaken by every ride the rider does next — and a rider's first month is
     * the part they would most miss.
     */
    @Test
    fun theBacklogIsWhatTheCloudHasNotGot_oldestFirst() = runBlocking {
        workoutDao.insertWorkout(workout("march", timestamp = 3_000))
        workoutDao.insertWorkout(workout("april", timestamp = 4_000))
        workoutDao.insertWorkout(workout("may", timestamp = 5_000))

        assertEquals(
            listOf("march", "april", "may"),
            workoutDao.unsyncedWorkouts(USER_ID, limit = 10).map { it.id }
        )

        workoutDao.markSynced("april", 9_999)

        assertEquals(
            listOf("march", "may"),
            workoutDao.unsyncedWorkouts(USER_ID, limit = 10).map { it.id }
        )
        assertEquals(9_999L, workoutDao.getWorkoutById("april")?.syncedAt)
    }

    /**
     * A ride still being written to is not a thing to upload, and neither is a
     * crashed one — the first is incomplete by definition and the second has no
     * aggregates until 8.3's recovery has run over it.
     */
    @Test
    fun aRideInProgressIsNotInTheBacklog() = runBlocking {
        workoutDao.insertWorkout(workout("finished", timestamp = 1_000))
        workoutDao.insertWorkout(workout("riding-now", isComplete = false, timestamp = 2_000))

        assertEquals(
            listOf("finished"),
            workoutDao.unsyncedWorkouts(USER_ID, limit = 10).map { it.id }
        )
    }

    /**
     * One rider's unsynced rides are not another's. The same rule as
     * `CloudAccess`: an account on one profile grants nothing to a housemate,
     * and a backlog drain runs for a named rider.
     */
    @Test
    fun aHousematesRidesAreNotInThisRidersBacklog() = runBlocking {
        workoutDao.insertWorkout(workout("mine", timestamp = 1_000))
        workoutDao.insertWorkout(workout("theirs", userId = OTHER_USER_ID, timestamp = 2_000))

        assertEquals(
            listOf("mine"),
            workoutDao.unsyncedWorkouts(USER_ID, limit = 10).map { it.id }
        )
    }

    /**
     * **An empty backlog has no oldest ride, and that has to be null rather
     * than zero** (14.2.3).
     *
     * `MIN()` over an empty set is NULL in SQL. A caller reading it as 0 would
     * place the oldest unsynced ride at the epoch and tell the rider their
     * backup was 56 years behind — on the screen whose entire job is to say
     * whether their history is safe.
     */
    @Test
    fun anEmptyBacklogHasNoOldestRide() = runBlocking {
        assertEquals(0, workoutDao.observeBacklog(USER_ID).first().pending)
        assertNull(workoutDao.observeBacklog(USER_ID).first().oldestTimestamp)

        workoutDao.insertWorkout(workout("march", timestamp = 3_000))
        workoutDao.insertWorkout(workout("may", timestamp = 5_000))

        val backlog = workoutDao.observeBacklog(USER_ID).first()
        assertEquals(2, backlog.pending)
        assertEquals(3_000L, backlog.oldestTimestamp)

        workoutDao.markSynced("march", 9_999)
        workoutDao.markSynced("may", 9_999)

        val cleared = workoutDao.observeBacklog(USER_ID).first()
        assertEquals(true, cleared.isClear)
        assertNull(cleared.oldestTimestamp)
    }

    /**
     * The live leaderboard's three own-history rows are **one query with a
     * floor on it** (24.3.12), which is worth a test precisely because it is
     * one query: a floor that leaked would put a ride from last year on the
     * board labelled as this month's best, and nothing on the screen would
     * look wrong.
     */
    @Test
    fun theBoardsOwnRowsAreTheSameQueryWithADateFloor() = runBlocking {
        // A realistic epoch, because `sinceMs = 0` is a real floor: with a
        // small `now` the 400-day-old ride lands *before* 1970 and is excluded
        // by the very clause the test is checking. That is the test's own
        // first failure, kept in mind rather than in a comment nobody reads.
        val now = 1_800_000_000_000L
        val day = 86_400_000L
        workoutDao.insertWorkout(
            workout("ancient", outputKj = 300.0, classId = CLASS_ID, timestamp = now - 400 * day)
        )
        samplesFor("ancient", measured = true)
        workoutDao.insertWorkout(
            workout("in-the-year", outputKj = 200.0, classId = CLASS_ID, timestamp = now - 200 * day)
        )
        samplesFor("in-the-year", measured = true)
        workoutDao.insertWorkout(
            workout("this-month", outputKj = 100.0, classId = CLASS_ID, timestamp = now - 5 * day)
        )
        samplesFor("this-month", measured = true)

        suspend fun bestSince(sinceMs: Long) = settled().previousBestOfClass(
            classId = CLASS_ID,
            userId = USER_ID,
            excludingWorkoutId = "",
            beforeMs = Long.MAX_VALUE,
            sinceMs = sinceMs
        )?.workoutId

        assertEquals("ancient", bestSince(0L))
        assertEquals("in-the-year", bestSince(now - 365 * day))
        assertEquals("this-month", bestSince(now - 30 * day))
    }

    /**
     * And the floor does not quietly drop the measured-power rule with it
     * (24.4.2). The window is the only thing that changes between those three
     * calls; the honesty gate is the same clause.
     */
    @Test
    fun aWindowedBestStillRefusesModelledPower() = runBlocking {
        val now = 1_800_000_000_000L
        workoutDao.insertWorkout(
            workout("modelled", outputKj = 300.0, classId = CLASS_ID, timestamp = now)
        )
        samplesFor("modelled", measured = false)

        assertNull(
            settled().previousBestOfClass(
                classId = CLASS_ID,
                userId = USER_ID,
                excludingWorkoutId = "",
                beforeMs = Long.MAX_VALUE,
                sinceMs = now - 86_400_000L
            )
        )
    }

    // ── Where a ride's watts came from, on the ride (23.4.12) ───────

    /**
     * The backfill is `PowerProvenance.of` written in SQL, so the two must give
     * the same answer for every shape a ride can be in — and the assertion is
     * against the enum itself rather than against four literals, because a
     * `CASE` that had drifted from the Kotlin would otherwise still pass a test
     * agreeing with the `CASE`.
     */
    @Test
    fun theProvenancePassAgreesWithPowerProvenance() = runBlocking {
        workoutDao.insertWorkout(workout("all-measured", classId = CLASS_ID))
        samplesFor("all-measured", measured = true)
        workoutDao.insertWorkout(workout("all-modelled", classId = CLASS_ID))
        samplesFor("all-modelled", measured = false)
        workoutDao.insertWorkout(workout("historic", classId = CLASS_ID))
        samplesFor("historic", measured = null)
        workoutDao.insertWorkout(workout("no-samples", classId = CLASS_ID))
        // A board that dropped out mid-ride, which is the only way `Mixed`
        // happens on real hardware.
        workoutDao.insertWorkout(workout("dropout", classId = CLASS_ID))
        samplesFor("dropout", measured = true, count = 10)
        metricDao.insertMetric(
            WorkoutMetricEntity(
                workoutId = "dropout",
                timestampSec = 10,
                power = 200.0,
                powerIsMeasured = false
            )
        )

        assertEquals(0, settled().completeRidesWithoutProvenance())

        suspend fun provenanceOf(id: String) = workoutDao.getWorkoutById(id)?.powerProvenance
        assertEquals(PowerProvenance.of(5, 0, 0), provenanceOf("all-measured"))
        assertEquals(PowerProvenance.of(0, 5, 0), provenanceOf("all-modelled"))
        assertEquals(PowerProvenance.of(0, 0, 5), provenanceOf("historic"))
        assertEquals(PowerProvenance.of(0, 0, 0), provenanceOf("no-samples"))
        assertEquals(PowerProvenance.of(10, 1, 0), provenanceOf("dropout"))
    }

    /**
     * The point of the column, and the defect 23.4.9 found live for six queries:
     * a ride whose samples have gone stays on the board it earned.
     *
     * The delete is 23.4.2 with the downsampled trace left out — the most
     * aggressive form trimming could take — and the assertion is that it changes
     * nothing. Before this column the same delete took the ride off the
     * household board, off *your usual* and out of the *best you've ridden it*
     * verdict, with nothing wrong on any screen.
     */
    @Test
    fun aTrimmedRideKeepsItsPlaceOnEveryBoard() = runBlocking {
        workoutDao.insertWorkout(workout("old", outputKj = 240.0, classId = CLASS_ID))
        samplesFor("old", measured = true)
        workoutDao.insertWorkout(
            workout("theirs", userId = OTHER_USER_ID, outputKj = 150.0, classId = CLASS_ID)
        )
        samplesFor("theirs", measured = true)

        val boardBefore = settled().householdLeaderboard(CLASS_ID).map { it.localUserId }
        val totalsBefore = workoutDao.ownTotalsForClass(userId = USER_ID, classId = CLASS_ID)

        metricDao.deleteMetricsForWorkout("old")
        metricDao.deleteMetricsForWorkout("theirs")

        // Not `settled()`: the pass must not be able to *undo* the answer for a
        // ride it can no longer work out. It only writes where there is nothing.
        assertEquals(0, workoutDao.backfillPowerProvenance())
        assertEquals(boardBefore, workoutDao.householdLeaderboard(CLASS_ID).map { it.localUserId })
        assertEquals(
            totalsBefore,
            workoutDao.ownTotalsForClass(userId = USER_ID, classId = CLASS_ID)
        )
        assertEquals(
            "old",
            workoutDao.householdRivals(CLASS_ID, "theirs", OTHER_USER_ID).single().workoutId
        )
    }

    /**
     * A ride still being pedalled has no answer yet, and the pass must not
     * invent one — the row's totals are zeroed at ride start (1.12) and its
     * samples are still arriving, so any word written now is about a ride that
     * has not happened.
     */
    @Test
    fun theProvenancePassLeavesARideInProgressAlone() = runBlocking {
        workoutDao.insertWorkout(workout("riding", classId = CLASS_ID, isComplete = false))
        samplesFor("riding", measured = true)

        assertEquals(0, workoutDao.backfillPowerProvenance())
        assertNull(workoutDao.getWorkoutById("riding")?.powerProvenance)
    }

    /**
     * The fence: after the pass, no finished ride is missing from the column.
     * A ride that is would be absent from six leaderboards with nothing
     * visibly wrong, which is why this is asserted rather than assumed.
     */
    @Test
    fun noFinishedRideIsLeftWithoutProvenance() = runBlocking {
        workoutDao.insertWorkout(workout("a", classId = CLASS_ID))
        samplesFor("a", measured = true)
        workoutDao.insertWorkout(workout("b", classId = CLASS_ID))
        workoutDao.insertWorkout(workout("c", userId = OTHER_USER_ID, classId = CLASS_ID))
        samplesFor("c", measured = false)

        assertEquals(0, settled().completeRidesWithoutProvenance())
    }

    private companion object {
        const val USER_ID = 1
        const val OTHER_USER_ID = 2
        const val CLASS_ID = "TH-01"
    }
}
