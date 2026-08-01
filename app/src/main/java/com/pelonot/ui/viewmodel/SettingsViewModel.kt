package com.pelonot.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelonot.core.Formatters
import com.pelonot.data.audio.VolumeController
import com.pelonot.data.backup.DatabaseBackup
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.data.repository.AppSettings
import com.pelonot.data.repository.CalibrationRepository
import com.pelonot.data.repository.CalibrationState
import com.pelonot.data.repository.SettingsRepository
import com.pelonot.data.repository.ThemeMode
import com.pelonot.data.repository.UserRepository
import com.pelonot.data.sensor.HeartRateDevice
import com.pelonot.data.sensor.PowerModel
import com.pelonot.data.sensor.HeartRateStatus
import com.pelonot.data.sensor.SensorMode
import com.pelonot.data.sensor.SensorRepository
import com.pelonot.di.ServiceLocator
import com.pelonot.domain.coach.CoachStyle
import com.pelonot.domain.model.HudDock
import com.pelonot.domain.model.UnitSystem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val profile: UserEntity? = null,
    val heartRateStatus: HeartRateStatus = HeartRateStatus.Idle,
    val heartRateDevices: List<HeartRateDevice> = emptyList(),
    /** System media volume as 0..1 — read live, not stored by us (11.5.1). */
    val mediaVolume: Float = 0f,
    val volumeError: String? = null,
    /** What this bike has learnt about its own power curve (2.2a.6). */
    val calibration: CalibrationState = CalibrationState()
) {
    val ftpWatts: Int get() = profile?.ftpWatts ?: UserEntity.DEFAULT_FTP
    val weightKg: Double? get() = profile?.weightKg
    val isGuest: Boolean get() = profile == null
}

/**
 * Backs the settings screen.
 *
 * FTP and weight are written through to the rider's profile row. Previously
 * they were held in `remember { mutableStateOf(200) }` inside the navigation
 * graph, so "Save Settings" updated a variable that was discarded the moment
 * the user navigated back, and the value never reached the database at all.
 */
@Suppress("OPT_IN_USAGE") // flatMapLatest
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val userRepository: UserRepository,
    private val sensorRepository: SensorRepository,
    private val volumeController: VolumeController,
    private val calibrationRepository: CalibrationRepository,
    private val databaseBackup: DatabaseBackup
) : ViewModel() {

    private val profile = settingsRepository.settings
        .map { it.lastProfileId }
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else userRepository.observeUser(id)
        }

    private val sensors = combine(
        sensorRepository.heartRateStatus,
        sensorRepository.discoveredHeartRateDevices
    ) { status, devices -> status to devices }

    private val volume = combine(
        volumeController.mediaVolume,
        volumeController.lastError
    ) { level, error -> level to error }

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        profile,
        sensors,
        volume,
        calibrationRepository.state
    ) { settings, user, (hrStatus, hrDevices), (mediaVolume, volumeError), calibration ->
        SettingsUiState(
            settings = settings,
            profile = user,
            heartRateStatus = hrStatus,
            heartRateDevices = hrDevices,
            mediaVolume = mediaVolume,
            volumeError = volumeError,
            calibration = calibration
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = SettingsUiState()
    )

    fun setFtp(ftpWatts: Int) {
        val userId = uiState.value.profile?.localUserId ?: return
        viewModelScope.launch { userRepository.updateFtp(userId, ftpWatts) }
    }

    fun setWeight(weightKg: Double) {
        val userId = uiState.value.profile?.localUserId ?: return
        viewModelScope.launch { userRepository.updateWeight(userId, weightKg) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    }

    /**
     * Writes the preference and nothing else (2.4.6).
     *
     * It used to call `sensorRepository.setMode` here as well, which is why the
     * choice appeared to work: it took effect in the session it was made in and
     * was forgotten at the next launch, because nothing else ever applied it.
     * `PelonotApp` now collects the preference for the life of the process, so
     * this writing to it *is* applying it — and there is one path rather than
     * two that could disagree.
     */
    fun setSensorMode(mode: SensorMode) {
        viewModelScope.launch { settingsRepository.setSensorMode(mode) }
    }

    fun setCloudSyncEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setCloudSyncEnabled(enabled) }
    }

    fun setCoachStyle(style: CoachStyle) {
        viewModelScope.launch { settingsRepository.setCoachStyle(style) }
    }

    /**
     * The system's own media volume, so no preference of ours needs writing —
     * only re-reading, which the controller does after the write lands.
     */
    fun setMediaVolume(fraction: Float) = volumeController.setMediaVolume(fraction)

    fun setCoachVolume(fraction: Float) {
        viewModelScope.launch { settingsRepository.setCoachVolume(fraction) }
    }

    /** Anything on the device may have moved it since this screen opened. */
    fun refreshVolume() = volumeController.refresh()

    fun setHudEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHudEnabled(enabled) }
    }

    fun setHudDock(dock: HudDock) {
        viewModelScope.launch { settingsRepository.setHudDock(dock) }
    }

    /** 11.1b.1. The HUD collects this live, so the strip changes as it moves. */
    fun setHudOpacity(opacity: Float) {
        viewModelScope.launch { settingsRepository.setHudOpacity(opacity) }
    }

    fun setUnitSystem(units: UnitSystem) {
        viewModelScope.launch { settingsRepository.setUnitSystem(units) }
    }

    fun scanForHeartRateMonitors() = sensorRepository.scanForHeartRateDevices()

    fun stopHeartRateScan() = sensorRepository.stopHeartRateScan()

    fun selectHeartRateDevice(address: String?) {
        viewModelScope.launch {
            settingsRepository.setHeartRateDeviceAddress(address)
            sensorRepository.selectHeartRateDevice(address)
        }
    }

    fun heartRatePermissions(): List<String> = sensorRepository.heartRatePermissions()

    /**
     * Throws away what this bike has learnt and returns to the shipped curve.
     *
     * Worth offering because calibration is derived from measurement: a
     * sensor board replaced, a resistance mechanism serviced, or simply a
     * suspicion that the numbers have gone wrong all leave a rider with no
     * other way to start again.
     */
    fun resetCalibration() {
        viewModelScope.launch {
            calibrationRepository.reset()
            PowerModel.useShippedCurve()
        }
    }

    // ── Backup and restore (19.1.3 / 12.4.4) ────────────────────────

    /** The name the file picker opens with. */
    fun backupFileName(): String = databaseBackup.suggestedFileName()

    /**
     * Both of these report their outcome as a sentence for the rider rather
     * than as a Boolean nothing reads. A backup that silently did nothing is
     * indistinguishable from one that worked, which is precisely how a rider
     * discovers their safety net was imaginary — on the day they need it.
     */
    fun backupTo(target: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = databaseBackup.backupTo(target)
            onResult(
                result.fold(
                    onSuccess = { "Backed up ${Formatters.fileSize(it)} — keep it somewhere else too." },
                    onFailure = { "Could not write the backup: ${it.message}" }
                )
            )
        }
    }

    /** @param onRestored called only when the app now has to restart. */
    fun restoreFrom(source: Uri, onRefused: (String) -> Unit, onRestored: () -> Unit) {
        viewModelScope.launch {
            when (val outcome = databaseBackup.restoreFrom(source)) {
                is DatabaseBackup.RestoreOutcome.Refused -> onRefused(outcome.reason)
                DatabaseBackup.RestoreOutcome.RestartRequired -> onRestored()
            }
        }
    }

    override fun onCleared() {
        // A scan left running is a meaningful battery drain.
        sensorRepository.stopHeartRateScan()
        super.onCleared()
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory = viewModelFactory {
            SettingsViewModel(
                settingsRepository = ServiceLocator.settingsRepository,
                userRepository = ServiceLocator.userRepository,
                sensorRepository = ServiceLocator.sensorRepository,
                volumeController = ServiceLocator.volumeController,
                calibrationRepository = ServiceLocator.calibrationRepository,
                databaseBackup = ServiceLocator.databaseBackup
            )
        }
    }
}
