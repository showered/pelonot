package com.pelonot.data.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pelonot.MainActivity
import com.pelonot.PelonotApp
import com.pelonot.R
import com.pelonot.data.local.AppDatabase
import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.data.local.entity.WorkoutMetricEntity
import com.pelonot.data.sensor.SensorRepository
import com.pelonot.ui.overlay.HudOverlayManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

/**
 * Foreground Service that manages workout state, telemetry collection, and metric recording.
 */
class WorkoutService : Service() {

    companion object {
        private const val TAG = "WorkoutService"
        private const val NOTIFICATION_ID = 101
        private const val CHANNEL_ID = PelonotApp.NOTIFICATION_CHANNEL_WORKOUT
        
        // Action and extras for starting workout
        const val ACTION_START_WORKOUT = "com.pelonot.START_WORKOUT"
        const val EXTRA_CLASS_ID = "class_id"
        const val EXTRA_INTENT_MODIFIER = "intent_modifier"
    }

    private val binder = WorkoutBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var metricsJob: Job? = null
    private var timerJob: Job? = null

    private lateinit var sensorRepository: SensorRepository
    private lateinit var database: AppDatabase
    private var hudOverlayManager: HudOverlayManager? = null

    private val _workoutState = MutableStateFlow(WorkoutState.Idle)
    val workoutState: StateFlow<WorkoutState> = _workoutState.asStateFlow()

    private val _currentSession = MutableStateFlow<WorkoutSession?>(null)
    val currentSession: StateFlow<WorkoutSession?> = _currentSession.asStateFlow()

    inner class WorkoutBinder : Binder() {
        fun getService(): WorkoutService = this@WorkoutService
    }

    override fun onCreate() {
        super.onCreate()
        sensorRepository = SensorRepository.getInstance(this)
        database = AppDatabase.getInstance(this)
        hudOverlayManager = HudOverlayManager(this)

        // Check for incomplete workout (crash recovery)
        serviceScope.launch {
            recoverIncompleteWorkout()
        }

        serviceScope.launch {
            _workoutState.collect { state ->
                when (state) {
                    WorkoutState.Active -> hudOverlayManager?.show()
                    WorkoutState.Idle, WorkoutState.Completed -> hudOverlayManager?.hide()
                    else -> {} // Keep showing on Pause
                }
            }
        }
        Log.d(TAG, "WorkoutService created")
    }
    
    /**
     * Recover state from an incomplete workout after app crash.
     * Looks for the most recent workout that was not properly completed.
     */
    private suspend fun recoverIncompleteWorkout() {
        val incompleteWorkout = database.workoutDao().getIncompleteWorkout() ?: return
        
        // Get the last recorded metric to determine elapsed time
        val lastMetric = database.workoutMetricDao().getLastMetricForWorkout(incompleteWorkout.id)
        
        val session = WorkoutSession(
            workoutId = incompleteWorkout.id,
            classId = incompleteWorkout.classId?.toIntOrNull() ?: 0,
            startTime = incompleteWorkout.timestamp,
            elapsedSeconds = lastMetric?.timestampSec ?: 0,
            intentModifier = incompleteWorkout.intentModifier.toString()
        )
        
        _currentSession.value = session
        _workoutState.value = WorkoutState.Paused // Resume paused so user can decide
        
        Log.d(TAG, "Recovered incomplete workout: ${incompleteWorkout.id}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "WorkoutService started with intent: ${intent?.action}")
        
        intent?.let {
            if (it.action == ACTION_START_WORKOUT) {
                val classId = it.getIntExtra(EXTRA_CLASS_ID, 0)
                val intentModifier = it.getStringExtra(EXTRA_INTENT_MODIFIER) ?: "Unknown"
                Log.d(TAG, "Starting workout: classId=$classId, modifier=$intentModifier")
                startWorkout(classId, intentModifier)
            }
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    /**
     * Start a new workout session.
     */
    fun startWorkout(classId: Int, intentModifier: String) {
        if (_workoutState.value != WorkoutState.Idle) return

        val workoutId = UUID.randomUUID().toString()
        val session = WorkoutSession(
            workoutId = workoutId,
            classId = classId,
            startTime = System.currentTimeMillis(),
            intentModifier = intentModifier
        )

        _currentSession.value = session
        _workoutState.value = WorkoutState.Active

        startForegroundService()
        sensorRepository.start()
        startMetricsCollection()
        startTimer()

        Log.d(TAG, "Workout started: $workoutId")
    }

    /**
     * Pause the current workout session.
     */
    fun pauseWorkout() {
        if (_workoutState.value != WorkoutState.Active) return
        _workoutState.value = WorkoutState.Paused
        updateNotification()
        Log.d(TAG, "Workout paused")
    }

    /**
     * Resume the current workout session.
     */
    fun resumeWorkout() {
        if (_workoutState.value != WorkoutState.Paused) return
        _workoutState.value = WorkoutState.Active
        updateNotification()
        Log.d(TAG, "Workout resumed")
    }

    /**
     * Stop and save the current workout session.
     */
    fun stopWorkout() {
        if (_workoutState.value == WorkoutState.Idle) return

        val session = _currentSession.value ?: return
        _workoutState.value = WorkoutState.Completed

        metricsJob?.cancel()
        timerJob?.cancel()
        sensorRepository.stop()

        serviceScope.launch {
            saveWorkoutToDb(session)
            _workoutState.value = WorkoutState.Idle
            _currentSession.value = null
            stopForeground(true)
            stopSelf()
        }

        Log.d(TAG, "Workout stopped: ${session.workoutId}")
    }

    private fun startForegroundService() {
        val notification = createNotification("Pelonot — Riding", "Ready to start...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val session = _currentSession.value ?: return
        val state = _workoutState.value
        val title = if (state == WorkoutState.Paused) "Pelonot — Paused" else "Pelonot — Riding"
        val reading = sensorRepository.sensorReading.value
        val content = "Time: ${formatDuration(session.elapsedSeconds)} | Power: ${reading.powerWatts.toInt()}W | Cadence: ${reading.cadenceRpm.toInt()} RPM"
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(title, content))
    }

    private fun createNotification(title: String, content: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startMetricsCollection() {
        metricsJob?.cancel()
        metricsJob = serviceScope.launch {
            while (isActive) {
                if (_workoutState.value == WorkoutState.Active) {
                    recordMetric()
                }
                delay(1000) // Record every 1 second
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive) {
                if (_workoutState.value == WorkoutState.Active) {
                    _currentSession.value?.let { session ->
                        session.elapsedSeconds++
                        _currentSession.value = session.copy() // Trigger state update
                        if (session.elapsedSeconds % 5 == 0) {
                            updateNotification()
                        }
                    }
                }
                delay(1000)
            }
        }
    }

    private suspend fun recordMetric() {
        val session = _currentSession.value ?: return
        val reading = sensorRepository.sensorReading.value

        val metric = WorkoutMetricEntity(
            workoutId = session.workoutId,
            timestampSec = session.elapsedSeconds,
            cadence = reading.cadenceRpm,
            resistance = reading.resistancePercent,
            power = reading.powerWatts,
            heartRate = reading.heartRateBpm
        )

        session.metrics.add(metric)
        database.workoutMetricDao().insertMetric(metric)
    }

    private suspend fun saveWorkoutToDb(session: WorkoutSession) {
        val metrics = session.metrics
        if (metrics.isEmpty()) return

        val avgCadence = metrics.map { it.cadence }.average()
        val avgPower = metrics.map { it.power }.average()
        val avgHr = metrics.mapNotNull { it.heartRate }.average().takeIf { !it.isNaN() }?.toInt()
        
        // Basic kJ calculation: sum of power per second / 1000
        val totalOutputKj = metrics.sumOf { it.power } / 1000.0

        val workout = WorkoutEntity(
            id = session.workoutId,
            userId = 1, // Default user for now
            classId = if (session.classId > 0) session.classId.toString() else null,
            durationSec = session.elapsedSeconds,
            totalOutputKj = totalOutputKj,
            totalDistanceKm = 0.0, // Calculated later
            avgCadence = avgCadence,
            avgPower = avgPower,
            avgHr = avgHr?.toDouble(),
            intentModifier = 1.0,
            timestamp = System.currentTimeMillis()
        )

        database.workoutDao().insertWorkout(workout)
        Log.d(TAG, "Workout saved to database")
    }

    private fun formatDuration(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return "%02d:%02d".format(mins, secs)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "WorkoutService destroyed")
    }
}