package com.pelonot.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelonot.data.audio.VolumeController
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.data.remote.SupabaseModule
import com.pelonot.data.repository.AppSettings
import com.pelonot.data.repository.SettingsRepository
import com.pelonot.data.repository.ThemeMode
import com.pelonot.data.repository.UserRepository
import com.pelonot.data.sensor.HeartRateDevice
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
    val cloudConfigured: Boolean = false,
    /** System media volume as 0..1 — read live, not stored by us (11.5.1). */
    val mediaVolume: Float = 0f,
    val volumeError: String? = null
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
    private val volumeController: VolumeController
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
        volume
    ) { settings, user, (hrStatus, hrDevices), (mediaVolume, volumeError) ->
        SettingsUiState(
            settings = settings,
            profile = user,
            heartRateStatus = hrStatus,
            heartRateDevices = hrDevices,
            cloudConfigured = SupabaseModule.isConfigured,
            mediaVolume = mediaVolume,
            volumeError = volumeError
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

    fun setSensorMode(mode: SensorMode) {
        viewModelScope.launch {
            settingsRepository.setSensorMode(mode)
            sensorRepository.setMode(mode)
        }
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
                volumeController = ServiceLocator.volumeController
            )
        }
    }
}
