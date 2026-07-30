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
import com.pelonot.data.repository.WorkoutRepository
import com.pelonot.data.sensor.SensorRepository
import com.pelonot.data.sensor.WorkoutMetricsCalculator
import com.pelonot.di.ServiceLocator
import com.pelonot.domain.model.RideIntent
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
 * Foreground service owning workout lifecycle, telemetry collection and
 * per-second metric recording.
 *
 * Two structural fixes over the previous implementation:
 *
 *  - **Elapsed time is measured, not counted.** It used to increment a counter
 *    inside a `while (true) { delay(1000) }` loop, which drifts — `delay` is a
 *    lower bound, and the loop body's own cost accumulates. Over an hour that
 *    is tens of seconds of error, and any missed tick was lost outright.
 *    Elapsed time is now derived from [SystemClock.elapsedRealtime].
 *  - **Metrics are buffered and written in batches.** One insert per second on
 *    the IO dispatcher for the length of a ride is a lot of individual
 *    transactions on tablet-grade flash.
 */
class WorkoutService : Service() {

    private val binder = WorkoutBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var sensorRepository: SensorRepository
    private lateinit var workoutRepository: WorkoutRepository
    private val metricsCalculator = WorkoutMetricsCalculator()

    private var tickerJob: Job? = null

    private val _workoutState = MutableStateFlow(WorkoutState.Idle)
    val workoutState: StateFlow<WorkoutState> = _workoutState.asStateFlow()

    private val _currentSession = MutableStateFlow<WorkoutSession?>(null)
    val currentSession: StateFlow<WorkoutSession?> = _currentSession.asStateFlow()

    /** Set when a previous run was killed mid-ride; the UI offers to resume. */
    private val _recoverableWorkout = MutableStateFlow<WorkoutEntity?>(null)
    val recoverableWorkout: StateFlow<WorkoutEntity?> = _recoverableWorkout.asStateFlow()

    // Monotonic timing. elapsedRealtime survives sleep and is immune to the
    // wall clock being adjusted mid-ride.
    private var rideStartedAtRealtimeMs = 0L
    private var accumulatedPausedMs = 0L
    private var pausedAtRealtimeMs = 0L

    private val pendingMetrics = mutableListOf<WorkoutMetricEntity>()

    inner class WorkoutBinder : Binder() {
        fun getService(): WorkoutService = this@WorkoutService
    }

    override fun onCreate() {
        super.onCreate()
        sensorRepository = ServiceLocator.sensorRepository
        workoutRepository = ServiceLocator.workoutRepository

        serviceScope.launch {
            _recoverableWorkout.value = workoutRepository.findRecoverableWorkout()
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
        pendingMetrics.clear()

        _currentSession.value = session
        _workoutState.value = WorkoutState.Active

        startForegroundNotification()

        serviceScope.launch {
            // The workout row must exist before any metric references it.
            workoutRepository.beginWorkout(session.toEntity())
        }

        sensorRepository.start()
        startTicker()

        Log.i(TAG, "Workout started: ${session.workoutId}")
    }

    fun pauseWorkout() {
        if (_workoutState.value != WorkoutState.Active) return
        pausedAtRealtimeMs = SystemClock.elapsedRealtime()
        _workoutState.value = WorkoutState.Paused
        updateNotification()
    }

    fun resumeWorkout() {
        if (_workoutState.value != WorkoutState.Paused) return
        if (pausedAtRealtimeMs > 0) {
            accumulatedPausedMs += SystemClock.elapsedRealtime() - pausedAtRealtimeMs
            pausedAtRealtimeMs = 0L
        }
        _workoutState.value = WorkoutState.Active
        updateNotification()
    }

    /** Finalises and persists the ride, then stops the service. */
    fun stopWorkout() {
        val session = _currentSession.value ?: return
        if (_workoutState.value == WorkoutState.Idle) return

        tickerJob?.cancel()
        tickerJob = null
        sensorRepository.stop()

        val finalSession = session.copy(elapsedSeconds = elapsedSeconds())
        _currentSession.value = finalSession
        _workoutState.value = WorkoutState.Completed

        serviceScope.launch {
            flushPendingMetrics()
            workoutRepository.finaliseWorkout(finalSession.toEntity())
            Log.i(TAG, "Workout saved: ${finalSession.workoutId}")

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

    // ── Recording loop ──────────────────────────────────────────────

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            var lastRecordedSecond = -1
            while (isActive) {
                if (_workoutState.value == WorkoutState.Active) {
                    val elapsed = elapsedSeconds()
                    // Guard against recording the same second twice if a tick
                    // runs early, and backfill if one runs late.
                    if (elapsed > lastRecordedSecond) {
                        recordMetric(elapsed)
                        lastRecordedSecond = elapsed
                    }
                    if (elapsed % NOTIFICATION_REFRESH_SEC == 0) updateNotification()
                }
                delay(TICK_INTERVAL_MS)
            }
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

        val samples = session.sampleCount + 1
        _currentSession.update { current ->
            current?.copy(
                elapsedSeconds = elapsedSec,
                totalOutputKj = derived.totalOutputKj,
                distanceKm = derived.distanceKm,
                // Running means, so the whole ride never has to be held in memory.
                avgPower = runningMean(current.avgPower, reading.powerWatts, samples),
                avgCadence = runningMean(current.avgCadence, reading.cadenceRpm, samples),
                avgHeartRate = reading.heartRateBpm?.let { bpm ->
                    val previous = current.avgHeartRate?.toDouble() ?: bpm.toDouble()
                    runningMean(previous, bpm.toDouble(), samples).toInt()
                } ?: current.avgHeartRate,
                sampleCount = samples
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

    private fun runningMean(previousMean: Double, sample: Double, count: Int): Double =
        previousMean + (sample - previousMean) / count

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
        avgHr = avgHeartRate?.toDouble(),
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
        // A ride still in flight when the service dies must not lose the
        // metrics already buffered in memory.
        if (pendingMetrics.isNotEmpty()) {
            runCatching {
                runBlocking { flushPendingMetrics() }
            }.onFailure { Log.e(TAG, "Failed to flush metrics on destroy", it) }
        }
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
