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
import com.pelonot.data.service.RideSnapshot
import com.pelonot.data.service.WorkoutService
import com.pelonot.data.service.WorkoutSession
import com.pelonot.data.service.WorkoutState
import com.pelonot.di.ServiceLocator
import com.pelonot.domain.model.PowerZone
import com.pelonot.domain.model.RideIntent
import com.pelonot.domain.model.ZoneScale
import com.pelonot.ui.overlay.OverlayPermissionHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RideUiState(
    val reading: SensorReading = SensorReading.EMPTY,
    val snapshot: RideSnapshot = RideSnapshot.IDLE,
    val session: WorkoutSession? = null,
    val workoutState: WorkoutState = WorkoutState.Idle,
    val sensorStatus: SensorStatus = SensorStatus.Stopped,
    val ftpWatts: Int = 0,
    /**
     * Set when the ride wants to raise the HUD but has not been allowed to.
     * Asked at ride start rather than discovered later as "the overlay just
     * never appeared".
     */
    val overlayPermissionNeeded: Boolean = false,
    /**
     * Whether leaving this screen would actually produce a HUD (11.1a.2).
     * Offering "back to the HUD" when the overlay is off or ungranted would
     * drop the rider onto their home screen with nothing.
     */
    val hudAvailable: Boolean = false
) {
    val elapsedSeconds: Int get() = snapshot.elapsedSeconds
    val isPaused: Boolean get() = workoutState == WorkoutState.Paused
    val isFinished: Boolean get() = workoutState == WorkoutState.Completed
    /**
     * The zone the rider's power actually falls in (11.6.2), or null when there
     * is no power to speak of. The rule itself lives on [ZoneScale] so that the
     * overlay cannot answer this question differently.
     */
    val currentZone: PowerZone?
        get() = ZoneScale.currentZone(ftpWatts, reading.powerWatts, snapshot.telemetryLive)

    /**
     * The zone ladder to draw (11.6.2a): the boundaries in watts, where the
     * rider is standing on them, and where the class asked them to stand.
     */
    val zoneScale: ZoneScale
        get() = ZoneScale.forReading(
            ftp = ftpWatts,
            powerWatts = reading.powerWatts,
            telemetryLive = snapshot.telemetryLive,
            prescribed = if (snapshot.interval.hasClass) snapshot.interval.targetZone else null
        )

    /** True when telemetry is fabricated, so the UI can say so plainly. */
    val isSimulated: Boolean
        get() = (sensorStatus as? SensorStatus.Streaming)?.simulated == true

    /**
     * True while the pipeline is backing off and retrying the source (2.4.5).
     *
     * Distinct from `snapshot.telemetryLive`, which is about readings having
     * stopped arriving: a board can go silent for seconds before the source
     * gives up and fails the flow, and it can fail the flow instantly on a
     * broken bind without any reading ever having been stale. Either one means
     * the numbers on screen are not live, and the rider is told either way.
     */
    val isReconnecting: Boolean get() = sensorStatus is SensorStatus.Reconnecting
}

/**
 * Owns the connection to [WorkoutService] for the ride screen.
 *
 * The screen previously called `startService(...)` and then ignored the
 * service entirely, reading the sensor repository directly and rendering a
 * hardcoded `00:00` timer. Binding means the screen shows the service's own
 * authoritative state — elapsed time, interval, totals, pause state — and can
 * drive its controls.
 *
 * The service, not this class, owns the floating HUD: a ride outlives the
 * screen, and so must its overlay. All this does is tell the service whether
 * the screen is currently on top, so the two never draw at once.
 */
class RideViewModel(application: Application) : AndroidViewModel(application) {

    private val sensorRepository = ServiceLocator.sensorRepository
    private val settingsRepository = ServiceLocator.settingsRepository

    private val _uiState = MutableStateFlow(RideUiState())
    val uiState: StateFlow<RideUiState> = _uiState.asStateFlow()

    private var service: WorkoutService? = null
    private var bound = false
    private val serviceJobs = mutableListOf<Job>()

    /** Held so the screen can navigate to the summary after the ride ends. */
    private var finishedWorkoutId: String? = null

    private var screenVisible = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val workoutService = (binder as? WorkoutService.WorkoutBinder)?.getService() ?: return
            service = workoutService
            bound = true
            workoutService.setRideScreenVisible(screenVisible)

            serviceJobs += viewModelScope.launch {
                workoutService.currentSession.collect { session ->
                    if (session != null) finishedWorkoutId = session.workoutId
                    _uiState.update { it.copy(session = session) }
                }
            }
            serviceJobs += viewModelScope.launch {
                workoutService.workoutState.collect { state ->
                    _uiState.update { it.copy(workoutState = state) }
                }
            }
            serviceJobs += viewModelScope.launch {
                workoutService.rideSnapshot.collect { snapshot ->
                    _uiState.update {
                        it.copy(
                            snapshot = snapshot,
                            // Once a ride is live the service is the authority
                            // on the FTP it is being judged against — it is the
                            // ride's own, off the row (7.8), which the screen
                            // has no other way of knowing on a resume. Before
                            // that, `RideSnapshot.IDLE` carries a *default*
                            // rather than this rider's, so taking it while idle
                            // would replace a real 200 with a generic 150 and
                            // draw the whole zone ladder at the wrong watts.
                            ftpWatts = if (snapshot.state == WorkoutState.Idle) {
                                it.ftpWatts
                            } else {
                                snapshot.ftpWatts
                            }
                        )
                    }
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
            // 11.6.7: the paced flow, not the raw one. What the recorder
            // writes is unaffected — `WorkoutService` reads `sensorReading`.
            sensorRepository.displayReading.collect { reading ->
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

        checkOverlayPermission()
    }

    /**
     * Picks a ride back up where it was interrupted (8.3d).
     *
     * The counterpart of [startRide] and not a parameter on it, because the two
     * carry opposite things: starting a ride tells the service everything about
     * it, and resuming tells it only which ride — the class, the intent and the
     * FTP come off the row the service already has (7.8).
     *
     * `ftpWatts` is therefore left as it is here rather than being set from the
     * rider's profile; the service publishes the ride's own FTP in its snapshot
     * as soon as the bind lands.
     */
    fun resumeRide(workoutId: String) {
        if (bound) return

        val context = getApplication<Application>()
        val serviceIntent = WorkoutService.resumeIntent(context, workoutId)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
        context.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)

        checkOverlayPermission()
    }

    /**
     * Asks once, at ride start, rather than letting the rider discover
     * mid-class that the HUD they were promised is simply not there.
     */
    private fun checkOverlayPermission() {
        viewModelScope.launch {
            val wantsHud = settingsRepository.settings.first().hudEnabled
            val granted = OverlayPermissionHelper.canDrawOverlays(getApplication())
            _uiState.update {
                it.copy(
                    overlayPermissionNeeded = wantsHud && !granted,
                    hudAvailable = wantsHud && granted
                )
            }
        }
    }

    fun requestOverlayPermission() {
        OverlayPermissionHelper.requestOverlayPermission(getApplication())
        dismissOverlayPrompt()
    }

    fun dismissOverlayPrompt() {
        _uiState.update { it.copy(overlayPermissionNeeded = false) }
    }

    /** "Don't ask again" — the rider is happy riding on the app's own screen. */
    fun disableHud() {
        viewModelScope.launch { settingsRepository.setHudEnabled(false) }
        _uiState.update { it.copy(hudAvailable = false) }
        dismissOverlayPrompt()
    }

    /**
     * Whether the app's own ride screen is on top. The service raises or drops
     * the overlay accordingly, so the rider never sees two of everything.
     */
    fun setScreenVisible(visible: Boolean) {
        screenVisible = visible
        service?.setRideScreenVisible(visible)
        // Re-read on the way back in: the rider may have just come from the
        // system's overlay settings, having granted the very permission the
        // prompt sent them there for.
        if (visible) checkOverlayPermission()
    }

    fun pause() = service?.pauseWorkout()

    fun resume() = service?.resumeWorkout()

    /**
     * Ends the ride. Navigation does not happen here: the service publishes
     * [WorkoutState.Completed] and the screen reacts to that, so a ride ended
     * from the HUD — or by the class timer running out — takes exactly the
     * same path as one ended from this screen.
     */
    fun endRide() {
        service?.stopWorkout()
    }

    /** The id to hand to the summary screen, released as the screen leaves. */
    fun consumeFinishedWorkoutId(): String? {
        val id = finishedWorkoutId
        finishedWorkoutId = null
        unbind()
        return id
    }

    private fun unbind() {
        serviceJobs.forEach { it.cancel() }
        serviceJobs.clear()
        if (bound) {
            runCatching {
                service?.setRideScreenVisible(false)
                getApplication<Application>().unbindService(connection)
            }
            bound = false
        }
        service = null
    }

    override fun onCleared() {
        // Unbinding alone does not stop the service: a ride deliberately keeps
        // running when the screen goes away, which is the point of it being a
        // foreground service — and of the HUD.
        unbind()
        super.onCleared()
    }
}
