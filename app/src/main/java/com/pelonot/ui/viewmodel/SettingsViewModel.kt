package com.pelonot.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val cloudConfigured: Boolean = false
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
    private val sensorRepository: SensorRepository
) : ViewModel() {

    private val profile = settingsRepository.settings
        .map { it.lastProfileId }
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else userRepository.observeUser(id)
        }

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        profile,
        sensorRepository.heartRateStatus,
        sensorRepository.discoveredHeartRateDevices
    ) { settings, user, hrStatus, hrDevices ->
        SettingsUiState(
            settings = settings,
            profile = user,
            heartRateStatus = hrStatus,
            heartRateDevices = hrDevices,
            cloudConfigured = SupabaseModule.isConfigured
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
                sensorRepository = ServiceLocator.sensorRepository
            )
        }
    }
}
