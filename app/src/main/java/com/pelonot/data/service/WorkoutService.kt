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
        private const val CHANNEL_ID = PelonotApp.WORKOUT_CHANNEL_ID
    }

    private val binder = WorkoutBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var metricsJob: Job? = null
    private var timerJob: Job? = null

    private lateinit var sensorRepository: SensorRepository
    private lateinit var database: AppDatabase

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
        database = AppDatabase.getDatabase(this)
        Log.d(TAG, "WorkoutService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "WorkoutService started")
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
            .setSmallIcon(R.drawable.ic_launcher_foreground)
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
        database.workoutMetricDao().insert(metric)
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
            localUserId = 1, // Default user for now
            classTemplateId = if (session.classId > 0) session.classId else null,
            durationSec = session.elapsedSeconds,
            totalOutputKj = totalOutputKj,
            totalDistanceKm = 0.0, // Calculated later
            avgCadence = avgCadence,
            avgPower = avgPower,
            avgHr = avgHr,
            intentModifier = session.intentModifier,
            timestamp = System.currentTimeMillis()
        )

        database.workoutDao().insert(workout)
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
