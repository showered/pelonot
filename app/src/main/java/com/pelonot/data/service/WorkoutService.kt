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
import com.pelonot.core.Formatters
import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.data.local.entity.WorkoutMetricEntity
import com.pelonot.data.repository.CalibrationRepository
import com.pelonot.data.repository.ClassRepository
import com.pelonot.data.repository.SettingsRepository
import com.pelonot.data.repository.WorkoutRepository
import com.pelonot.data.sensor.PowerModel
import com.pelonot.data.sensor.SensorRepository
import com.pelonot.data.sensor.WorkoutMetricsCalculator
import com.pelonot.domain.calibration.CalibrationSample
import com.pelonot.data.worker.WorkoutSyncWorker
import com.pelonot.di.ServiceLocator
import com.pelonot.domain.coach.CoachInput
import com.pelonot.domain.coach.CoachStyle
import com.pelonot.domain.coach.RideCoachPolicy
import com.pelonot.domain.model.ClassIntervalEngine
import com.pelonot.domain.model.HudDock
import com.pelonot.domain.model.IntervalState
import com.pelonot.domain.model.RideIntent
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
    private val metricsCalculator = WorkoutMetricsCalculator()

    private var hudOverlay: HudOverlayManager? = null
    private var coach: RideCoach? = null
    private val coachPolicy = RideCoachPolicy()

    private var tickerJob: Job? = null
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
        if (intent?.action == ACTION_START_WORKOUT) {
            startWorkout(
                userId = intent.getIntExtra(EXTRA_USER_ID, GUEST_USER_ID)
                    .takeIf { it != GUEST_USER_ID },
                classId = intent.getStringExtra(EXTRA_CLASS_ID),
                intent = RideIntent.fromId(intent.getStringExtra(EXTRA_INTENT_ID)),
                ftpWatts = intent.getIntExtra(EXTRA_FTP_WATTS, WorkoutSession.DEFAULT_FTP)
            )
        }

        // NOT_STICKY: if the system kills us mid-ride we must not silently
        // restart with a null Intent and begin recording a phantom workout.
        // Recovery is offered explicitly through [recoverableWorkout].
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    // ── Controls ────────────────────────────────────────────────────

    fun startWorkout(
        userId: Int?,
        classId: String?,
        intent: RideIntent,
        ftpWatts: Int
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
        pendingMetrics.clear()
        calibrationSamples.clear()
        intervalEngine = null

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
            // The workout row must exist before any metric references it.
            workoutRepository.beginWorkout(session.toEntity())
        }

        if (classId != null) loadClass(classId)

        sensorRepository.start()
        startTicker()
        syncHudVisibility()

        Log.i(TAG, "Workout started: ${session.workoutId}")
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

    fun pauseWorkout() {
        if (_workoutState.value != WorkoutState.Active) return
        pausedAtRealtimeMs = SystemClock.elapsedRealtime()
        _workoutState.value = WorkoutState.Paused
        _rideSnapshot.update { it.copy(state = WorkoutState.Paused) }
        updateNotification()
    }

    fun resumeWorkout() {
        if (_workoutState.value != WorkoutState.Paused) return
        if (pausedAtRealtimeMs > 0) {
            accumulatedPausedMs += SystemClock.elapsedRealtime() - pausedAtRealtimeMs
            pausedAtRealtimeMs = 0L
        }
        _workoutState.value = WorkoutState.Active
        _rideSnapshot.update { it.copy(state = WorkoutState.Active) }
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

            // A ride against a profile is worth mirroring to the cloud. A guest
            // ride is not: it has no owner yet, and the rider is about to be
            // asked whether to keep it at all. PostRideViewModel enqueues it
            // if they save it to a profile.
            if (finalSession.userId != null) {
                WorkoutSyncWorker.enqueue(applicationContext, finalSession.workoutId)
            }

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

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            var lastRecordedSecond = -1
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
                delay(TICK_INTERVAL_MS)
            }

            if (classFinished) {
                Log.i(TAG, "Class timer finished; completing the ride")
                stopWorkout()
            }
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
                powerTarget = snapshot.powerTarget
            )
        )
        coach?.deliver(alerts)

        return intervalState.isComplete
    }

    private fun publishSnapshot(elapsedSec: Int, intervalState: IntervalState) {
        val session = _currentSession.value
        _rideSnapshot.update { current ->
            current.copy(
                state = _workoutState.value,
                elapsedSeconds = elapsedSec,
                interval = intervalState,
                totalOutputKj = session?.totalOutputKj ?: current.totalOutputKj,
                distanceKm = session?.distanceKm ?: current.distanceKm
            )
        }
    }

    private suspend fun recordMetric(elapsedSec: Int) {
        val session = _currentSession.value ?: return
        val reading = sensorRepository.sensorReading.value
        val derived = metricsCalculator.processReading(reading, session.ftpWatts)

        pendingMetrics += WorkoutMetricEntity(
            workoutId = session.workoutId,
            timestampSec = elapsedSec,
            cadence = reading.cadenceRpm,
            resistance = reading.resistancePercent,
            power = reading.powerWatts,
            heartRate = reading.heartRateBpm
        )

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
        timestamp = startedAtEpochMs
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
        const val EXTRA_USER_ID = "com.pelonot.extra.USER_ID"
        const val EXTRA_CLASS_ID = "com.pelonot.extra.CLASS_ID"
        const val EXTRA_INTENT_ID = "com.pelonot.extra.INTENT_ID"
        const val EXTRA_FTP_WATTS = "com.pelonot.extra.FTP_WATTS"

        fun startIntent(
            context: Context,
            userId: Int?,
            classId: String?,
            intent: RideIntent,
            ftpWatts: Int
        ): Intent = Intent(context, WorkoutService::class.java).apply {
            action = ACTION_START_WORKOUT
            putExtra(EXTRA_USER_ID, userId ?: GUEST_USER_ID)
            putExtra(EXTRA_CLASS_ID, classId)
            putExtra(EXTRA_INTENT_ID, intent.id)
            putExtra(EXTRA_FTP_WATTS, ftpWatts)
        }
    }
}
