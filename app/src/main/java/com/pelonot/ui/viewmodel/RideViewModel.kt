package com.pelonot.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pelonot.data.sensor.SensorReading
import com.pelonot.data.sensor.SensorStatus
import com.pelonot.data.service.WorkoutService
import com.pelonot.data.service.WorkoutSession
import com.pelonot.data.service.WorkoutState
import com.pelonot.di.ServiceLocator
import com.pelonot.domain.model.PowerZone
import com.pelonot.domain.model.RideIntent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RideUiState(
    val reading: SensorReading = SensorReading.EMPTY,
    val session: WorkoutSession? = null,
    val workoutState: WorkoutState = WorkoutState.Idle,
    val sensorStatus: SensorStatus = SensorStatus.Stopped,
    val ftpWatts: Int = 0
) {
    val elapsedSeconds: Int get() = session?.elapsedSeconds ?: 0
    val isPaused: Boolean get() = workoutState == WorkoutState.Paused
    val currentZone: PowerZone get() = PowerZone.forPower(reading.powerWatts, ftpWatts.toDouble())

    /** True when telemetry is fabricated, so the UI can say so plainly. */
    val isSimulated: Boolean
        get() = (sensorStatus as? SensorStatus.Streaming)?.simulated == true
}

/**
 * Owns the connection to [WorkoutService] for the ride screen.
 *
 * The screen previously called `startService(...)` and then ignored the
 * service entirely, reading the sensor repository directly and rendering a
 * hardcoded `00:00` timer. Binding means the screen shows the service's own
 * authoritative session state — elapsed time, totals, pause state — and can
 * drive its controls.
 */
class RideViewModel(application: Application) : AndroidViewModel(application) {

    private val sensorRepository = ServiceLocator.sensorRepository

    private val _uiState = MutableStateFlow(RideUiState())
    val uiState: StateFlow<RideUiState> = _uiState.asStateFlow()

    private var service: WorkoutService? = null
    private var bound = false
    private var sessionJob: Job? = null
    private var stateJob: Job? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val workoutService = (binder as? WorkoutService.WorkoutBinder)?.getService() ?: return
            service = workoutService
            bound = true

            sessionJob = viewModelScope.launch {
                workoutService.currentSession.collect { session ->
                    _uiState.update { it.copy(session = session) }
                }
            }
            stateJob = viewModelScope.launch {
                workoutService.workoutState.collect { state ->
                    _uiState.update { it.copy(workoutState = state) }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
        }
    }

    init {
        viewModelScope.launch {
            sensorRepository.sensorReading.collect { reading ->
                _uiState.update { it.copy(reading = reading) }
            }
        }
        viewModelScope.launch {
            sensorRepository.status.collect { status ->
                _uiState.update { it.copy(sensorStatus = status) }
            }
        }
    }

    /** Starts the foreground service for this ride and binds to it. */
    fun startRide(userId: Int?, classId: String?, intent: RideIntent, ftpWatts: Int) {
        if (bound) return
        _uiState.update { it.copy(ftpWatts = ftpWatts) }

        val context = getApplication<Application>()
        val serviceIntent = WorkoutService.startIntent(context, userId, classId, intent, ftpWatts)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
        context.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
    }

    fun pause() = service?.pauseWorkout()

    fun resume() = service?.resumeWorkout()

    /** Ends the ride and returns the completed workout's id, if there was one. */
    fun endRide(): String? {
        val workoutId = service?.currentSession?.value?.workoutId
        service?.stopWorkout()
        unbind()
        return workoutId
    }

    private fun unbind() {
        sessionJob?.cancel()
        stateJob?.cancel()
        if (bound) {
            runCatching { getApplication<Application>().unbindService(connection) }
            bound = false
        }
        service = null
    }

    override fun onCleared() {
        // Unbinding alone does not stop the service: a ride deliberately keeps
        // running when the screen goes away, which is the point of it being a
        // foreground service.
        unbind()
        super.onCleared()
    }
}
