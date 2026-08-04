package com.pelonot.data.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.pelonot.MainActivity
import com.pelonot.PelonotApp
import com.pelonot.R
import com.pelonot.core.Features
import com.pelonot.core.Formatters
import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.data.local.entity.WorkoutMetricEntity
import com.pelonot.data.repository.CalibrationRepository
import com.pelonot.data.repository.ClassRepository
import com.pelonot.data.repository.SettingsRepository
import com.pelonot.data.repository.UserRepository
import com.pelonot.data.repository.ResumedRide
import com.pelonot.data.repository.WorkoutRepository
import com.pelonot.data.sensor.PowerModel
import com.pelonot.data.sensor.SensorReading
import com.pelonot.data.sensor.SensorRepository
import com.pelonot.data.sensor.WorkoutMetricsCalculator
import com.pelonot.data.sensor.implausibleValues
import com.pelonot.domain.calibration.CalibrationSample
import com.pelonot.data.worker.WorkoutSyncWorker
import com.pelonot.di.ServiceLocator
import com.pelonot.domain.coach.CoachInput
import com.pelonot.domain.coach.CoachStyle
import com.pelonot.domain.coach.RideCoachPolicy
import com.pelonot.domain.model.AutoPausePolicy
import com.pelonot.domain.model.ClassIntervalEngine
import com.pelonot.domain.model.HudDock
import com.pelonot.domain.model.IntervalState
import com.pelonot.domain.model.LiveLeaderboard
import com.pelonot.domain.model.MaxHeartRate
import com.pelonot.domain.model.MetricSample
import com.pelonot.domain.model.RideIntent
import com.pelonot.domain.model.RaceMetric
import com.pelonot.domain.model.RivalTrace
import com.pelonot.ui.overlay.AppForeground
import com.pelonot.ui.overlay.HudOverlayManager
import com.pelonot.ui.overlay.RideCoach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID

/**
 * Foreground service owning workout lifecycle, telemetry collection,
 * per-second metric recording, the class interval clock and the floating HUD.
 *
 * It owns the HUD deliberately. The rider's ride does not stop when they leave
 * the app — that is the entire point of it being a foreground service — and the
 * HUD is what they look at while they are away from it. Hanging the overlay off
 * the ride screen's ViewModel would tear it down at exactly the moment it
 * becomes useful.
 *
 * Structural notes carried over from earlier fixes:
 *
 *  - **Elapsed time is measured, not counted.** It used to increment a counter
 *    inside a `while (true) { delay(1000) }` loop, which drifts — `delay` is a
 *    lower bound, and the loop body's own cost accumulates. Elapsed time comes
 *    from [SystemClock.elapsedRealtime].
 *  - **Metrics are buffered and written in batches.** One insert per second for
 *    the length of a ride is a lot of transactions on tablet-grade flash.
 *  - **The class clock is the same clock.** [ClassIntervalEngine] is a pure
 *    function of elapsed time evaluated here, rather than a second timer that
 *    would drift away from this one over a 45-minute class.
 */
class WorkoutService : Service() {

    private val binder = WorkoutBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Window and view work has to happen on the main thread, and the ride's own
     * work deliberately does not — the ticker and every database write run on
     * IO. Anything touching the overlay goes through here.
     */
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var sensorRepository: SensorRepository
    private lateinit var workoutRepository: WorkoutRepository
    private lateinit var classRepository: ClassRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var calibrationRepository: CalibrationRepository
    private lateinit var userRepository: UserRepository
    private val metricsCalculator = WorkoutMetricsCalculator()

    private var hudOverlay: HudOverlayManager? = null
    private var coach: RideCoach? = null
    private val coachPolicy = RideCoachPolicy()

    private var tickerJob: Job? = null
    private val autoPause = AutoPausePolicy()
    private var intervalEngine: ClassIntervalEngine? = null

    private val _workoutState = MutableStateFlow(WorkoutState.Idle)
    val workoutState: StateFlow<WorkoutState> = _workoutState.asStateFlow()

    private val _currentSession = MutableStateFlow<WorkoutSession?>(null)
    val currentSession: StateFlow<WorkoutSession?> = _currentSession.asStateFlow()

    /** Everything the HUD and the ride screen render from. */
    private val _rideSnapshot = MutableStateFlow(RideSnapshot.IDLE)
    val rideSnapshot: StateFlow<RideSnapshot> = _rideSnapshot.asStateFlow()

    private val _coachStyle = MutableStateFlow(CoachStyle.DEFAULT)

    /** Set when a previous run was killed mid-ride; the UI offers to resume. */
    private val _recoverableWorkout = MutableStateFlow<WorkoutEntity?>(null)
    val recoverableWorkout: StateFlow<WorkoutEntity?> = _recoverableWorkout.asStateFlow()

    // Monotonic timing. elapsedRealtime survives sleep and is immune to the
    // wall clock being adjusted mid-ride.
    private var rideStartedAtRealtimeMs = 0L
    private var accumulatedPausedMs = 0L
    private var pausedAtRealtimeMs = 0L

    private val pendingMetrics = mutableListOf<WorkoutMetricEntity>()

    /**
     * Measured operating points from this ride, for 2.2a.
     *
     * Collected only while `powerIsMeasured` is true. A simulated ride's watts
     * came out of `PowerModel` itself, so learning from one would be the model
     * teaching itself its own answer (2.2a.7).
     */
    private val calibrationSamples = mutableListOf<CalibrationSample>()

    /** Latches the stall so a dropout logs twice, not four times a second. */
    private var telemetryStalled = false

    /**
     * The rival this ride is racing (24.3.3), loaded once at ride start or on
     * resume and then read by elapsed second. Null means no ghost, which is
     * the ordinary case.
     */
    private var rivalTrace: RivalTrace? = null
    private var rivalName: String? = null

    /**
     * The live leaderboard this ride is on (24.3.10), loaded once at ride
     * start or on resume and then read by elapsed second.
     *
     * Mutually exclusive with [rivalTrace] above: the board is what a ride
     * gets unless [Features.singleRivalGhost] is on and somebody was picked.
     * Null means nobody has a measured ride of this class, which is the
     * ordinary case and draws nothing at all.
     */
    private var raceBoard: LiveLeaderboard? = null

    /**
     * Set the moment a sample lands whose watts were not measured (24.3.7).
     *
     * The gate has to be applied to *this* ride as well as the others', and
     * this is the only point at which it can be: their side is excluded by
     * the query before the board is built, but whether the ride about to
     * happen will be measured is not knowable in advance. Once set the race
     * is gone for the rest of the ride and never comes back — `Mixed` fails
     * `isTrustworthyAsMeasured` on purpose (24.4.2), and a race that was
     * honest for ten minutes and fiction for the next ten is worse than none.
     */
    private var raceDiscredited = false

    /** True while the in-app ride screen is on top; the HUD stands down. */
    private var rideScreenVisible = false
    private var hudEnabled = true
    private var hudDock = HudDock.DEFAULT

    inner class WorkoutBinder : Binder() {
        fun getService(): WorkoutService = this@WorkoutService
    }

    override fun onCreate() {
        super.onCreate()
        sensorRepository = ServiceLocator.sensorRepository
        workoutRepository = ServiceLocator.workoutRepository
        classRepository = ServiceLocator.classRepository
        settingsRepository = ServiceLocator.settingsRepository
        calibrationRepository = ServiceLocator.calibrationRepository
        userRepository = ServiceLocator.userRepository

        hudOverlay = HudOverlayManager(this).apply {
            onDockChanged = { dock ->
                hudDock = dock
                serviceScope.launch { settingsRepository.setHudDock(dock) }
            }
        }
        coach = RideCoach(this)

        serviceScope.launch {
            _recoverableWorkout.value = workoutRepository.findRecoverableWorkout()
        }
        serviceScope.launch {
            settingsRepository.settings.collect { settings ->
                _coachStyle.value = settings.coachStyle
                coach?.style = settings.coachStyle
                coach?.volume = settings.coachVolume
                hudEnabled = settings.hudEnabled
                hudDock = settings.hudDock
                syncHudVisibility()
            }
        }
        Log.d(TAG, "WorkoutService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_WORKOUT -> startWorkout(
                userId = intent.getIntExtra(EXTRA_USER_ID, GUEST_USER_ID)
                    .takeIf { it != GUEST_USER_ID },
                classId = intent.getStringExtra(EXTRA_CLASS_ID),
                intent = RideIntent.fromId(intent.getStringExtra(EXTRA_INTENT_ID)),
                ftpWatts = intent.getIntExtra(EXTRA_FTP_WATTS, WorkoutSession.DEFAULT_FTP),
                rivalWorkoutId = intent.getStringExtra(EXTRA_RIVAL_WORKOUT_ID)
            )

            ACTION_RESUME_WORKOUT -> intent.getStringExtra(EXTRA_WORKOUT_ID)
                ?.let { resumeInterruptedRide(it) }
        }

        // NOT_STICKY: if the system kills us mid-ride we must not silently
        // restart with a null Intent and begin recording a phantom workout.
        // Recovery is offered explicitly through [recoverableWorkout].
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    // ── Controls ────────────────────────────────────────────────────

    /**
     * @param rivalWorkoutId the ride being raced live (24.3.3), or null for
     *   the ordinary case of racing nobody.
     */
    fun startWorkout(
        userId: Int?,
        classId: String?,
        intent: RideIntent,
        ftpWatts: Int,
        rivalWorkoutId: String? = null
    ) {
        if (_workoutState.value != WorkoutState.Idle) return

        val session = WorkoutSession(
            workoutId = UUID.randomUUID().toString(),
            userId = userId,
            classId = classId,
            startedAtEpochMs = System.currentTimeMillis(),
            intent = intent,
            ftpWatts = ftpWatts
        )

        rideStartedAtRealtimeMs = SystemClock.elapsedRealtime()
        accumulatedPausedMs = 0L
        pausedAtRealtimeMs = 0L
        metricsCalculator.reset()
        coachPolicy.reset()
        telemetryStalled = false
        pendingMetrics.clear()
        calibrationSamples.clear()
        intervalEngine = null
        clearRace()

        // The curve this bike has earned, read once here rather than per
        // sample. On hardware it will not be consulted at all — the board
        // measures the watts — but the prescribed resistance band (11.2.1)
        // inverts it every tick.
        serviceScope.launch {
            PowerModel.curve = runCatching { calibrationRepository.activeCurve() }
                .getOrDefault(PowerModel.curve)
        }

        // Published before anything can ask: the recovery query (8.3b) has to
        // be able to tell this ride apart from a crashed one from the moment
        // the row exists, and the row is written a few lines below.
        RideInProgress.begin(
            ActiveRide(
                workoutId = session.workoutId,
                classId = classId,
                intentId = intent.id,
                ftpWatts = ftpWatts
            )
        )

        _currentSession.value = session
        _workoutState.value = WorkoutState.Active
        _rideSnapshot.value = RideSnapshot(
            state = WorkoutState.Active,
            ftpWatts = ftpWatts,
            intent = intent
        )

        startForegroundNotification()

        serviceScope.launch {
            // 21.2.3. Resolved here and stamped on the session **before** the
            // row is written, so the ride carries the maximum its heart-rate
            // zones are judged against rather than borrowing whatever the
            // profile says the next time somebody opens the ride. Null is a
            // real answer — a rider who has given the app neither a measured
            // maximum nor a date of birth has no zones, and inventing one would
            // be a guess about their body (21.1).
            //
            // Read from the profile rather than passed in the intent: the ride
            // screen resolves this too, but asynchronously, and a first ride
            // started before that landed would record null for no reason.
            val max = userId?.let { id ->
                userRepository.getUser(id)?.let { MaxHeartRate.resolve(it.maxHrBpm, it.birthDate) }
            }
            _currentSession.update { it?.copy(maxHrBpm = max?.bpm) }

            // The workout row must exist before any metric references it.
            workoutRepository.beginWorkout(
                (_currentSession.value ?: session).toEntity()
            )

            // After the row, never before: `active_ride_rival` has a foreign
            // key onto it, exactly as `workout_metrics` does (1.12).
            if (Features.singleRivalGhost && rivalWorkoutId != null) {
                workoutRepository.setActiveRival(session.workoutId, rivalWorkoutId)
                loadRival(rivalWorkoutId, userId)
            } else if (classId != null) {
                // 24.3.10. Nothing was chosen and nothing needs to be: the
                // board is everybody who qualifies, and the only condition is
                // that this ride is following a class — a free ride is not a
                // ride of anything, so there is nothing to be ranked against.
                loadRaceBoard(classId, userId, session.workoutId)
            }
        }

        if (classId != null) loadClass(classId)

        sensorRepository.start()
        startTicker()
        syncHudVisibility()

        Log.i(TAG, "Workout started: ${session.workoutId}")
    }

    /**
     * Picks up a ride the app was killed in the middle of (8.3d).
     *
     * The counterpart to [startWorkout] rather than a variant of it, because
     * three of that method's opening moves are exactly wrong here: it mints a
     * fresh `UUID`, it inserts a `workouts` row, and it starts the clock at
     * zero. This adopts the existing id, updates the row that is already there,
     * and starts the clock at the last second that recorded.
     *
     * Asynchronous because the totals have to be added back up off disk first.
     * Nothing is published until they are, so a caller that binds immediately
     * sees `Idle` and then a ride, rather than a ride with no history in it.
     *
     * Note the name: [resumeWorkout] is un-pausing, and has been since Phase 3.
     */
    fun resumeInterruptedRide(workoutId: String) {
        // `Completed` as well as `Idle` (12.6.2): a rider who ended a ride by
        // accident reaches the summary while this service is still shutting
        // itself down, and refusing them because `stopSelf()` has not landed
        // yet would be a button that works or does nothing depending on how
        // fast they tapped. Everything the ride needs is set up again below —
        // the clock, the calculator, the sensor, the ticker and the
        // notification — so a completed ride is as good a starting point as a
        // cold service.
        if (_workoutState.value == WorkoutState.Active) return
        if (_workoutState.value == WorkoutState.Paused) return

        serviceScope.launch {
            val interruption = workoutRepository.interruptionFor(workoutId)
            if (interruption == null || !interruption.isResumable) {
                Log.w(TAG, "Not resumable: $workoutId")
                return@launch
            }

            val resumed = workoutRepository.resumeInterruptedWorkout(workoutId, interruption)
            if (resumed == null) {
                Log.w(TAG, "Nothing to resume in $workoutId")
                return@launch
            }
            beginResumedRide(resumed)
        }
    }

    private fun beginResumedRide(resumed: ResumedRide) {
        val workout = resumed.workout
        val aggregates = resumed.aggregates
        val intent = RideIntent.fromMultiplier(workout.intentModifier)
        // From the row, never the profile: a breakthrough accepted between the
        // crash and the resume would otherwise rescore the ride it came from
        // (7.8).
        val ftpWatts = workout.ftpWatts ?: WorkoutSession.DEFAULT_FTP

        // The clock is wound back so elapsedSeconds() returns the last second
        // that recorded. That is what makes the class pick up where it left off
        // and the metric series continue rather than restart (8.3d.1).
        rideStartedAtRealtimeMs =
            SystemClock.elapsedRealtime() - aggregates.durationSec * 1000L
        accumulatedPausedMs = 0L
        pausedAtRealtimeMs = 0L
        metricsCalculator.restore(aggregates.totalOutputKj, aggregates.distanceKm)
        coachPolicy.reset()
        telemetryStalled = false
        pendingMetrics.clear()
        calibrationSamples.clear()
        intervalEngine = null
        clearRace()

        serviceScope.launch {
            PowerModel.curve = runCatching { calibrationRepository.activeCurve() }
                .getOrDefault(PowerModel.curve)
        }

        // 24.3.8. Off the table rather than out of the navigation route, for
        // the same reason the FTP comes off the row: a resume has to pick up
        // the race the rider was actually in, and nothing on the way back into
        // this ride knows what that was.
        serviceScope.launch {
            val rivalId = workoutRepository.getActiveRival(workout.id)
            if (Features.singleRivalGhost && rivalId != null) {
                loadRival(rivalId, workout.userId)
            } else if (workout.classId != null) {
                // 24.3.8 for the board as much as for the rival, and cheaper:
                // the board is derived from the class and the rider, both of
                // which the row already carries, so a resumed ride rejoins the
                // race it was in without anything having had to be written
                // down. It rejoins it at the right second too — the clock
                // above has already been wound back to the last one recorded.
                loadRaceBoard(workout.classId, workout.userId, workout.id)
            }
        }

        // Before anything can ask, exactly as in startWorkout — otherwise the
        // recovery query offers to recover the ride that has just been resumed,
        // which is 8.3b arriving by a new door (8.3d.4).
        RideInProgress.begin(
            ActiveRide(
                workoutId = workout.id,
                classId = workout.classId,
                intentId = intent.id,
                ftpWatts = ftpWatts
            )
        )

        val session = WorkoutSession(
            workoutId = workout.id,
            userId = workout.userId,
            classId = workout.classId,
            startedAtEpochMs = workout.timestamp,
            intent = intent,
            ftpWatts = ftpWatts,
            // 21.2.3, and the same rule as `ftpWatts` above: off the row, so a
            // maximum the rider changed between the crash and the resume does
            // not rescore the ride they are picking back up.
            maxHrBpm = workout.maxHrBpm,
            // From the row the repository has just stamped, so the finalise at
            // the end of this ride writes the interruption back rather than
            // over (8.3d.2).
            resumeCount = workout.resumeCount,
            interruptedSec = workout.interruptedSec
        ).restoredWith(aggregates)

        _currentSession.value = session
        _workoutState.value = WorkoutState.Active
        _rideSnapshot.value = RideSnapshot(
            state = WorkoutState.Active,
            ftpWatts = ftpWatts,
            intent = intent,
            elapsedSeconds = aggregates.durationSec,
            // Seeded, or the ride screen and the overlay both show a rider who
            // has just ridden twenty minutes a total output of zero until the
            // first tick lands on top of the restored calculator.
            totalOutputKj = aggregates.totalOutputKj,
            distanceKm = aggregates.distanceKm
        )
        // The prompt is answered; nothing should still be offering this ride.
        _recoverableWorkout.value = null

        startForegroundNotification()

        if (workout.classId != null) loadClass(workout.classId)

        sensorRepository.start()
        startTicker(fromSecond = aggregates.durationSec)
        syncHudVisibility()

        Log.i(
            TAG,
            "Workout resumed: ${workout.id} at ${aggregates.durationSec}s " +
                "after ${resumed.workout.interruptedSec}s not riding"
        )
    }

    /**
     * Loads the class the ride is following.
     *
     * Asynchronous, so the ride starts recording immediately rather than
     * waiting on a database read. Until it lands the ride simply has no
     * prescription, which is exactly what a free ride looks like.
     */
    private fun loadClass(classId: String) {
        serviceScope.launch {
            val plan = runCatching { classRepository.getPlan(classId) }
                .onFailure { Log.e(TAG, "Could not load class $classId", it) }
                .getOrNull() ?: return@launch

            if (plan.intervals.isEmpty()) {
                Log.w(TAG, "Class ${plan.id} has no usable intervals; riding free")
                _rideSnapshot.update { it.copy(classTitle = plan.title) }
                return@launch
            }

            val engine = ClassIntervalEngine(plan.intervals)
            intervalEngine = engine
            _rideSnapshot.update {
                it.copy(
                    classTitle = plan.title,
                    intervals = plan.intervals,
                    interval = engine.stateAt(elapsedSeconds())
                )
            }
            Log.i(TAG, "Class loaded: ${plan.title}, ${plan.intervals.size} intervals")
        }
    }

    /**
     * Loads the rival being raced, once (24.3.3).
     *
     * The whole series is read here and reduced to a cumulative-kJ lookup, so
     * the recording path never touches the database for it — the tick reads an
     * in-memory array by index. Same shape as [loadClass]: asynchronous, and
     * until it lands the ride simply has no ghost, which is exactly what a
     * ride racing nobody looks like.
     */
    private suspend fun loadRival(rivalWorkoutId: String, youId: Int?) {
        val rival = runCatching { workoutRepository.getWorkout(rivalWorkoutId) }
            .onFailure { Log.w(TAG, "Could not load the rival ride $rivalWorkoutId", it) }
            .getOrNull() ?: return

        val samples = runCatching { workoutRepository.getMetrics(rivalWorkoutId) }
            .getOrDefault(emptyList())
            .map { MetricSample(it.timestampSec, it.power, it.cadence, it.heartRate) }

        val trace = RivalTrace.from(samples)
        if (trace.isEmpty) {
            Log.w(TAG, "Rival ride $rivalWorkoutId has no samples; riding without a ghost")
            return
        }

        // "Your best" when it is the rider's own earlier ride, which the
        // owner's own note says is the case that makes this work at all — on
        // a household bike most riders are the only person who has ridden a
        // given class.
        rivalName = if (rival.userId != null && rival.userId == youId) {
            "Your best"
        } else {
            rival.userId?.let { id -> userRepository.getUser(id)?.name } ?: "Rival"
        }
        rivalTrace = trace

        Log.i(
            TAG,
            "Racing $rivalName by ${trace.metric}: " +
                "${trace.finalValue} over ${trace.finalSecond}s"
        )
    }

    /**
     * Builds the live leaderboard this ride is on (24.3.10, 24.3.12).
     *
     * Every competitor's whole series is read here and reduced to a cumulative
     * lookup, so the recording path never touches the database for the race:
     * the tick reads memory. Same shape as [loadClass] and [loadRival] —
     * asynchronous, and until it lands the ride simply has no board, which is
     * exactly what a class nobody has ridden looks like.
     *
     * A competitor whose ride left no samples is dropped rather than drawn at
     * zero. There is no honest number to put on that row and a competitor
     * pinned to the bottom of the board for the whole class is worse than an
     * absent one — 24.1.6's rule, applied per row.
     */
    private suspend fun loadRaceBoard(classId: String, youId: Int?, workoutId: String) {
        val field = runCatching {
            workoutRepository.raceBoardFor(
                classId = classId,
                youId = youId,
                excludingWorkoutId = workoutId,
                nowMs = System.currentTimeMillis()
            )
        }.onFailure { Log.w(TAG, "Could not build the leaderboard for $classId", it) }
            .getOrDefault(emptyList())

        val ghosts = field.mapNotNull { competitor ->
            val samples = runCatching { workoutRepository.getMetrics(competitor.workoutId) }
                .getOrDefault(emptyList())
                .map { MetricSample(it.timestampSec, it.power, it.cadence, it.heartRate) }

            val trace = RivalTrace.from(samples)
            if (trace.isEmpty) {
                Log.w(TAG, "${competitor.name} has no samples; leaving them off the board")
                null
            } else {
                LiveLeaderboard.Ghost(
                    name = competitor.name.ifBlank { "Rider" },
                    trace = trace
                )
            }
        }

        if (ghosts.isEmpty()) {
            Log.i(TAG, "Nobody has a raceable ride of $classId; no leaderboard")
            return
        }

        raceBoard = LiveLeaderboard(ghosts)
        Log.i(
            TAG,
            "Racing ${ghosts.size} on $classId: " +
                ghosts.joinToString { "${it.name} ${it.trace.finalValue.toInt()}" }
        )
    }

    private fun clearRace() {
        rivalTrace = null
        rivalName = null
        raceBoard = null
        raceDiscredited = false
    }

    /**
     * @param manual false only when [autoPause] asked for this (19.1.2). A
     *   pause the rider worked themselves is theirs to lift, so the policy is
     *   told to let go of it.
     */
    fun pauseWorkout(manual: Boolean = true) {
        if (_workoutState.value != WorkoutState.Active) return
        if (manual) autoPause.onManualControl()
        pausedAtRealtimeMs = SystemClock.elapsedRealtime()
        _workoutState.value = WorkoutState.Paused
        _rideSnapshot.update {
            it.copy(state = WorkoutState.Paused, autoPaused = autoPause.isAutoPaused)
        }
        updateNotification()
    }

    fun resumeWorkout(manual: Boolean = true) {
        if (_workoutState.value != WorkoutState.Paused) return
        if (manual) autoPause.onManualControl()
        if (pausedAtRealtimeMs > 0) {
            accumulatedPausedMs += SystemClock.elapsedRealtime() - pausedAtRealtimeMs
            pausedAtRealtimeMs = 0L
        }
        _workoutState.value = WorkoutState.Active
        _rideSnapshot.update { it.copy(state = WorkoutState.Active, autoPaused = false) }
        updateNotification()
    }

    /** Finalises and persists the ride, then stops the service. */
    fun stopWorkout() {
        val session = _currentSession.value ?: return
        if (_workoutState.value == WorkoutState.Idle) return
        if (_workoutState.value == WorkoutState.Completed) return

        tickerJob?.cancel()
        tickerJob = null
        sensorRepository.stop()

        // 11.1a.3: the class finishing is the one moment the rider definitely
        // wants the whole screen — the summary, the RPE question and any FTP
        // proposal are all there, and none of them fit on a strip. Read
        // *before* hideHud(), because a ride ended from the strip is exactly
        // the case that needs it. If the ride screen is already on top the app
        // is forward by definition and starting it again would only churn.
        val needsBringingForward = !rideScreenVisible

        hideHud()
        coach?.silence()

        val finalSession = session.copy(elapsedSeconds = elapsedSeconds())
        _currentSession.value = finalSession
        _workoutState.value = WorkoutState.Completed
        _rideSnapshot.update { it.copy(state = WorkoutState.Completed) }

        if (needsBringingForward) AppForeground.bringForward(this)

        serviceScope.launch {
            flushPendingMetrics()
            workoutRepository.finaliseWorkout(finalSession.toEntity())
            // 24.3.9: the race is over and nothing about it is written to the
            // ride. The row exists only to survive a crash *during* one.
            workoutRepository.clearActiveRival(finalSession.workoutId)
            // Only now: until the row is `is_complete = 1` it still looks like
            // a crash artifact, and a cold start in that window would offer to
            // recover the ride that is in the middle of being saved.
            RideInProgress.end()
            Log.i(TAG, "Workout saved: ${finalSession.workoutId}")

            // After the ride is safely on disk, never before: calibration is
            // derived state and losing it costs a few weeks of accumulation,
            // whereas losing the ride costs the rider their record.
            if (calibrationSamples.isNotEmpty()) {
                val samples = calibrationSamples.toList()
                calibrationSamples.clear()
                runCatching { calibrationRepository.recordRide(samples) }
                    .onFailure { Log.w(TAG, "Could not update this bike's calibration", it) }
            }

            // A ride belonging to a rider with an account is worth mirroring to
            // the cloud. Nothing else is: a guest ride has no owner yet, and a
            // ride by a profile with no account belongs to somebody who never
            // asked for a cloud and must not get one (23.1.2). The gate says
            // which; PostRideViewModel asks it again if a guest ride is later
            // saved to a profile.
            WorkoutSyncWorker.enqueueIfAllowed(
                applicationContext,
                finalSession.workoutId,
                finalSession.userId
            )

            ServiceCompat.stopForeground(this@WorkoutService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /** Discards a recovered ride the user chose not to resume. */
    fun discardRecoverableWorkout() {
        serviceScope.launch {
            workoutRepository.clearRecoverableWorkouts()
            _recoverableWorkout.value = null
        }
    }

    // ── The HUD ─────────────────────────────────────────────────────

    /**
     * Told by the ride screen whether it is on top.
     *
     * Two full-size readouts of the same ride, one over the other, is worse
     * than either alone — so the overlay stands down while the app's own ride
     * screen is visible and comes back the moment the rider switches to their
     * video app.
     */
    fun setRideScreenVisible(visible: Boolean) {
        if (rideScreenVisible == visible) return
        rideScreenVisible = visible
        syncHudVisibility()
    }

    private fun syncHudVisibility() = mainScope.launch {
        val overlay = hudOverlay ?: return@launch
        val shouldShow = hudEnabled &&
            !rideScreenVisible &&
            _workoutState.value.let { it == WorkoutState.Active || it == WorkoutState.Paused }

        if (shouldShow && !overlay.isShowing) {
            overlay.show(
                snapshotFlow = rideSnapshot,
                coachStyleFlow = _coachStyle,
                dock = hudDock,
                onOpenApp = { AppForeground.bringForward(this@WorkoutService) },
                onPause = ::pauseWorkout,
                onResume = ::resumeWorkout,
                onStop = ::stopWorkout
            )
        } else if (!shouldShow && overlay.isShowing) {
            overlay.hide()
        }
    }

    private fun hideHud() {
        mainScope.launch { hudOverlay?.hide() }
    }

    // ── Recording loop ──────────────────────────────────────────────

    /**
     * @param fromSecond the highest second already written for this ride, so a
     *   resumed ride does not record second *N* twice (8.3d.4). `-1` for a
     *   fresh ride, because second 0 is a second like any other.
     */
    private fun startTicker(fromSecond: Int = -1) {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            var lastRecordedSecond = fromSecond
            var classFinished = false

            while (isActive) {
                if (_workoutState.value == WorkoutState.Active) {
                    val elapsed = elapsedSeconds()
                    // Guard against recording the same second twice if a tick
                    // runs early, and backfill if one runs late.
                    if (elapsed > lastRecordedSecond) {
                        recordMetric(elapsed)
                        lastRecordedSecond = elapsed

                        if (advanceClass(elapsed)) {
                            classFinished = true
                            break
                        }
                    }
                    if (elapsed % NOTIFICATION_REFRESH_SEC == 0) updateNotification()
                }
                // 19.1.2. Runs while paused as well as while riding: the tick
                // that lifts an auto-pause is a tick the clock is not moving
                // on. `recordMetric` has already settled whether this second's
                // telemetry is live, so the policy reads the same answer the
                // rider is being shown.
                applyAutoPause()
                delay(TICK_INTERVAL_MS)
            }

            if (classFinished) {
                Log.i(TAG, "Class timer finished; completing the ride")
                stopWorkout()
            }
        }
    }

    /**
     * Pauses the ride when the rider has stopped, and picks it up again when
     * they start (19.1.2).
     *
     * The bottle stop is the case: the clock kept running through it, so the
     * minute at the tap was averaged into `avg_power` and `avg_cadence` as a
     * minute of very easy riding. Pausing stops the clock, stops the class
     * advancing and stops [recordMetric] writing, which are the three things
     * that were wrong about standing still.
     */
    private fun applyAutoPause() {
        val paused = _workoutState.value == WorkoutState.Paused
        if (!paused && _workoutState.value != WorkoutState.Active) return

        val decision = autoPause.onTick(
            elapsedSec = elapsedSeconds(),
            cadenceRpm = sensorRepository.sensorReading.value.cadenceRpm,
            telemetryLive = !telemetryStalled,
            isPaused = paused
        )
        when (decision) {
            AutoPausePolicy.Decision.Pause -> {
                Log.i(TAG, "Auto-pausing: no cadence for ${AutoPausePolicy.DEFAULT_STILLNESS_SEC}s")
                pauseWorkout(manual = false)
            }
            AutoPausePolicy.Decision.Resume -> {
                Log.i(TAG, "Auto-resuming: the rider is pedalling again")
                resumeWorkout(manual = false)
            }
            AutoPausePolicy.Decision.None -> Unit
        }
    }

    /**
     * Moves the class on and lets the coach react.
     *
     * @return true when the class has run out and the ride should finish.
     */
    private fun advanceClass(elapsedSec: Int): Boolean {
        val engine = intervalEngine ?: run {
            // No class: the snapshot still needs its clock and totals.
            publishSnapshot(elapsedSec, IntervalState.NONE)
            return false
        }

        val intervalState = engine.stateAt(elapsedSec)
        publishSnapshot(elapsedSec, intervalState)

        val snapshot = _rideSnapshot.value
        val reading = sensorRepository.sensorReading.value
        val alerts = coachPolicy.onTick(
            CoachInput(
                elapsedSec = elapsedSec,
                isPaused = false,
                interval = intervalState,
                cadence = reading.cadenceRpm,
                power = reading.powerWatts,
                cadenceTarget = snapshot.cadenceTarget,
                powerTarget = snapshot.powerTarget,
                governedBy = snapshot.governedBy
            )
        )
        coach?.deliver(alerts)

        return intervalState.isComplete
    }

    private fun publishSnapshot(elapsedSec: Int, intervalState: IntervalState) {
        val session = _currentSession.value
        val outputKj = session?.totalOutputKj ?: _rideSnapshot.value.totalOutputKj
        _rideSnapshot.update { current ->
            current.copy(
                state = _workoutState.value,
                elapsedSeconds = elapsedSec,
                interval = intervalState,
                totalOutputKj = outputKj,
                distanceKm = session?.distanceKm ?: current.distanceKm,
                // Decided in recordMetric, which runs first on every tick, so
                // what the rider is told matches what was recorded for the very
                // same second.
                telemetryLive = !telemetryStalled,
                rival = rivalStatus(
                    elapsedSec,
                    outputKj,
                    session?.distanceKm ?: current.distanceKm
                ),
                standings = standings(
                    elapsedSec,
                    outputKj,
                    session?.distanceKm ?: current.distanceKm
                )
            )
        }
    }

    /**
     * The gap against the rival at this second, or null when there is no
     * honest one to draw (24.3.4).
     *
     * Keyed off [elapsedSec] — the clock that already excludes paused time —
     * and nothing else. 24.3.8's rule: wall-clock anywhere in this feature is
     * a rider who stopped for a phone call losing a race they were winning.
     */
    private fun rivalStatus(elapsedSec: Int, outputKj: Double, distanceKm: Double) =
        rivalTrace
            ?.takeUnless { raceDiscredited }
            ?.let { trace ->
                // Your side of the comparison has to be the same quantity the
                // trace accumulated (24.3.14). Asked of the trace rather than
                // assumed here, so adding a third RaceMetric cannot leave this
                // silently racing kilojoules against kilometres.
                trace.statusAt(
                    elapsedSec,
                    yourValue(trace.metric, outputKj, distanceKm),
                    rivalName.orEmpty()
                )
            }

    /**
     * The live leaderboard at this second, or null when there is no honest one
     * to draw (24.3.10).
     *
     * Same clock and same gate as [rivalStatus], because it is the same race
     * with the `LIMIT 1` taken off.
     */
    private fun standings(elapsedSec: Int, outputKj: Double, distanceKm: Double) =
        raceBoard
            ?.takeUnless { raceDiscredited }
            ?.let { board ->
                board.standingsAt(
                    elapsedSec,
                    yourValue(board.metric, outputKj, distanceKm)
                )
            }

    /**
     * The rider's own side of a comparison, in whatever the race is measured
     * in (24.3.14).
     *
     * Asked of the metric rather than assumed, so adding a third [RaceMetric]
     * cannot leave a race silently comparing kilojoules against kilometres.
     */
    private fun yourValue(metric: RaceMetric, outputKj: Double, distanceKm: Double) =
        when (metric) {
            RaceMetric.Output -> outputKj
            RaceMetric.Distance -> distanceKm
        }

    private suspend fun recordMetric(elapsedSec: Int) {
        val session = _currentSession.value ?: return
        val reading = sensorRepository.sensorReading.value

        // 2.4.4. The reading flow holds its last value while the board is being
        // retried, so without this the ride writes that frozen sample once a
        // second for the length of the dropout — into `workout_metrics`, into
        // `avg_power` and `avg_cadence`, and into the calibration grid with
        // `powerIsMeasured` still true. The second is left with no sample
        // instead: a gap in the series is what actually happened, the charts
        // already draw gaps (16.1.2, 16.2.2), and the totals survive because
        // `WorkoutMetricsCalculator` clamps the step across a gap to five
        // seconds rather than integrating through it.
        if (reading.isStaleAt(System.currentTimeMillis(), SensorReading.MAX_AGE_MS)) {
            if (!telemetryStalled) {
                telemetryStalled = true
                Log.w(TAG, "Telemetry stale at ${elapsedSec}s; recording nothing until it returns")
            }
            return
        }
        if (telemetryStalled) {
            telemetryStalled = false
            Log.i(TAG, "Telemetry live again at ${elapsedSec}s")
        }

        // 2.7.3, and the reason it is checked here as well as at the point of
        // publication: this is the boundary that matters. Everything upstream
        // can be got wrong again by a future change, and only the line below
        // makes a number permanent. A sample that cannot be true is not
        // written and not clamped — the second is left empty, exactly as a
        // dropout leaves it.
        val impossible = reading.implausibleValues()
        if (impossible.isNotEmpty()) {
            Log.w(TAG, "Refusing to record ${impossible.joinToString()} at ${elapsedSec}s")
            return
        }

        val derived = metricsCalculator.processReading(reading, session.ftpWatts)

        pendingMetrics += WorkoutMetricEntity(
            workoutId = session.workoutId,
            timestampSec = elapsedSec,
            cadence = reading.cadenceRpm,
            resistance = reading.resistancePercent,
            power = reading.powerWatts,
            heartRate = reading.heartRateBpm,
            // 16.1.6 / 7.10.7 / 24.4.2: the one thing that says whether this
            // watt is a measurement or the model's guess. It has always been
            // on the reading and was thrown away here.
            powerIsMeasured = reading.powerIsMeasured
        )

        // 24.3.7, the symmetric half of the measured-power gate. Everybody
        // else's side was excluded by the query before the board was built;
        // this side cannot be known until the watts actually arrive, and one
        // modelled sample is enough — `Mixed` fails `isTrustworthyAsMeasured`
        // too.
        if (!reading.powerIsMeasured && !raceDiscredited &&
            (rivalTrace != null || raceBoard != null)
        ) {
            raceDiscredited = true
            Log.i(TAG, "Dropping the race at ${elapsedSec}s: these watts are modelled")
        }

        // 2.2a.1: the ride is already the calibration dataset. Kept in memory
        // for the length of the ride and folded in once at the end, so a fit
        // never runs on the recording path.
        if (reading.powerIsMeasured) {
            calibrationSamples += CalibrationSample(
                cadenceRpm = reading.cadenceRpm,
                resistancePercent = reading.resistancePercent,
                measuredWatts = reading.powerWatts
            )
        }

        _currentSession.update { current ->
            current
                ?.withSample(
                    powerWatts = reading.powerWatts,
                    cadenceRpm = reading.cadenceRpm,
                    heartRateBpm = reading.heartRateBpm
                )
                ?.copy(
                    elapsedSeconds = elapsedSec,
                    totalOutputKj = derived.totalOutputKj,
                    distanceKm = derived.distanceKm
                )
        }

        if (pendingMetrics.size >= METRIC_BATCH_SIZE) flushPendingMetrics()
    }

    private suspend fun flushPendingMetrics() {
        if (pendingMetrics.isEmpty()) return
        val batch = pendingMetrics.toList()
        pendingMetrics.clear()
        runCatching { workoutRepository.recordMetrics(batch) }
            .onFailure { Log.e(TAG, "Failed to persist ${batch.size} metrics", it) }
    }

    /** Ride time excluding paused periods, from the monotonic clock. */
    private fun elapsedSeconds(): Int {
        if (rideStartedAtRealtimeMs == 0L) return 0
        val now = SystemClock.elapsedRealtime()
        val pausedSoFar = accumulatedPausedMs +
            if (pausedAtRealtimeMs > 0) now - pausedAtRealtimeMs else 0L
        return ((now - rideStartedAtRealtimeMs - pausedSoFar) / 1000L).toInt().coerceAtLeast(0)
    }

    private fun WorkoutSession.toEntity() = WorkoutEntity(
        id = workoutId,
        userId = userId,
        classId = classId,
        durationSec = elapsedSeconds,
        totalOutputKj = totalOutputKj,
        totalDistanceKm = distanceKm,
        avgCadence = avgCadence,
        avgPower = avgPower,
        // The unrounded mean, matching how avg_power and avg_cadence are stored.
        avgHr = avgHeartRateBpm,
        intentModifier = intent.multiplier,
        // 7.8. Written at insert, which is at the *start* of the ride, so it is
        // the number the ride was actually judged against rather than one
        // reconstructed afterwards from a profile that may have moved.
        ftpWatts = ftpWatts,
        // 21.2.3, and the same sentence as the line above: the maximum this
        // ride's heart-rate zones are judged against, written at the start so
        // it is the number the rider actually had rather than one recovered
        // later from a profile that has moved.
        maxHrBpm = maxHrBpm,
        timestamp = startedAtEpochMs,
        // 8.3d.2. Carried through the session or the finalise silently writes
        // 0 over a resumed ride's own history of being resumed.
        resumeCount = resumeCount,
        interruptedSec = interruptedSec
    )

    // ── Notification ────────────────────────────────────────────────

    private fun startForegroundNotification() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val session = _currentSession.value
        val paused = _workoutState.value == WorkoutState.Paused
        val reading = sensorRepository.sensorReading.value

        val title = if (paused) "Pelonot — Paused" else "Pelonot — Riding"
        val content = if (session == null) {
            "Ready to start"
        } else {
            "${Formatters.duration(session.elapsedSeconds)}  ·  " +
                "${reading.powerWatts.toInt()} W  ·  " +
                "${reading.cadenceRpm.toInt()} RPM"
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, PelonotApp.NOTIFICATION_CHANNEL_WORKOUT)
            // Was android.R.drawable.ic_dialog_info — a stock system icon that
            // renders as a grey blob in the status bar.
            .setSmallIcon(R.drawable.ic_notification_ride)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .build()
    }

    override fun onDestroy() {
        // A ride whose service has gone really has become a crash artifact, so
        // it stops being the live ride and starts being recoverable. Ordinary
        // stops have already cleared this after finalising.
        RideInProgress.end()

        // A ride still in flight when the service dies must not lose the
        // metrics already buffered in memory.
        if (pendingMetrics.isNotEmpty()) {
            runCatching {
                runBlocking { flushPendingMetrics() }
            }.onFailure { Log.e(TAG, "Failed to flush metrics on destroy", it) }
        }
        // Not through hideHud(): mainScope is about to be cancelled, and the
        // window must come down before the service does or it is orphaned.
        runCatching { hudOverlay?.hide() }
        coach?.release()
        coach = null
        hudOverlay = null
        mainScope.cancel()
        serviceScope.cancel()
        super.onDestroy()
        Log.d(TAG, "WorkoutService destroyed")
    }

    companion object {
        private const val TAG = "WorkoutService"
        private const val NOTIFICATION_ID = 101

        private const val TICK_INTERVAL_MS = 250L
        private const val NOTIFICATION_REFRESH_SEC = 5
        private const val METRIC_BATCH_SIZE = 15

        /** Sentinel for "no profile", since Intent extras cannot carry null Ints. */
        const val GUEST_USER_ID = -1

        const val ACTION_START_WORKOUT = "com.pelonot.action.START_WORKOUT"

        /** 8.3d — pick up a ride the app was killed in the middle of. */
        const val ACTION_RESUME_WORKOUT = "com.pelonot.action.RESUME_WORKOUT"

        const val EXTRA_USER_ID = "com.pelonot.extra.USER_ID"
        const val EXTRA_CLASS_ID = "com.pelonot.extra.CLASS_ID"
        const val EXTRA_INTENT_ID = "com.pelonot.extra.INTENT_ID"
        const val EXTRA_FTP_WATTS = "com.pelonot.extra.FTP_WATTS"
        const val EXTRA_WORKOUT_ID = "com.pelonot.extra.WORKOUT_ID"

        /** 24.3.3 — the ride being raced live, chosen before the class starts. */
        const val EXTRA_RIVAL_WORKOUT_ID = "com.pelonot.extra.RIVAL_WORKOUT_ID"

        /**
         * Everything a resume needs is already on the row, so this carries only
         * the id — the class, the intent and the FTP the ride was started at
         * are read back from `workouts` rather than passed in, which is what
         * stops a resumed ride being rescored against a profile that has moved
         * since (7.8).
         */
        fun resumeIntent(context: Context, workoutId: String): Intent =
            Intent(context, WorkoutService::class.java).apply {
                action = ACTION_RESUME_WORKOUT
                putExtra(EXTRA_WORKOUT_ID, workoutId)
            }

        fun startIntent(
            context: Context,
            userId: Int?,
            classId: String?,
            intent: RideIntent,
            ftpWatts: Int,
            rivalWorkoutId: String? = null
        ): Intent = Intent(context, WorkoutService::class.java).apply {
            action = ACTION_START_WORKOUT
            putExtra(EXTRA_USER_ID, userId ?: GUEST_USER_ID)
            putExtra(EXTRA_CLASS_ID, classId)
            putExtra(EXTRA_INTENT_ID, intent.id)
            putExtra(EXTRA_FTP_WATTS, ftpWatts)
            putExtra(EXTRA_RIVAL_WORKOUT_ID, rivalWorkoutId)
        }
    }
}
