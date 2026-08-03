package com.pelonot.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelonot.core.Formatters
import com.pelonot.data.audio.VolumeController
import com.pelonot.data.backup.DatabaseBackup
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.data.local.entity.FtpChangeSource
import com.pelonot.data.local.entity.FtpHistoryEntity
import com.pelonot.data.repository.AccountRepository
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
import com.pelonot.data.remote.CloudAccess
import com.pelonot.data.repository.WorkoutRepository
import com.pelonot.domain.cloud.CloudSyncStatus
import com.pelonot.domain.model.MaxHeartRate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    val calibration: CalibrationState = CalibrationState(),
    /** This rider's FTP over time, oldest first (7.9). */
    val ftpHistory: List<FtpHistoryEntity> = emptyList(),

    /** Whether their rides are reaching the cloud, and what if not (14.2.3). */
    val cloudSync: CloudSyncStatus = CloudSyncStatus.Off,

    /**
     * Whether the session this tablet is holding is **this** rider's (15.2.8).
     *
     * Distinct from `profile.hasAccount`, which says only that the rider has
     * signed in *at some point*. A tablet with no session, or one carrying a
     * housemate's, has an `auth_user_id` on the row and can send nothing.
     */
    val sessionMatchesProfile: Boolean = false,

    /** Rides this profile has that the cloud has not — for the signed-out line. */
    val ridesWaiting: Int = 0,

    /**
     * Whether this *build* has a cloud at all (14.10.3, 23.1.5).
     *
     * The one place the app is allowed to consult the build rather than the
     * rider, and only to decide whether to draw an offer: there is no point
     * showing *Back up my rides* in a clone with no endpoint compiled into it.
     * It must never stand in for consent — that is `CloudAccess`'s job and
     * confusing the two is what put two requests on the wire for a rider who
     * had agreed to nothing.
     */
    val cloudConfigured: Boolean = false,

    /**
     * The highest heart rate this rider has ever recorded, offered as a
     * starting point rather than written for them (21.1.3). Null until it is
     * looked up, and null for a rider who has never worn a strap.
     */
    val highestRecordedHr: Int? = null
) {
    val ftpWatts: Int get() = profile?.ftpWatts ?: UserEntity.DEFAULT_FTP
    val weightKg: Double? get() = profile?.weightKg
    val isGuest: Boolean get() = profile == null

    /**
     * The most recent *move*, or null when the number has never moved (7.10.3).
     *
     * A history of one is the value the profile started with, and calling that
     * "last changed" would be the app reporting an event that never happened —
     * on a brand-new rider's very first visit to Settings, which is the worst
     * possible moment to be wrong about their record.
     */
    val lastFtpChange: FtpHistoryEntity? get() = ftpHistory.takeIf { it.size > 1 }?.last()

    /**
     * The maximum heart rate zones are computed from, and where it came from —
     * or **null, which is a real state** (21.1, 21.3.3).
     *
     * Resolved here rather than stored, so it follows a rider correcting their
     * date of birth without anything having to be recomputed and written back.
     */
    val maxHeartRate: MaxHeartRate? get() =
        MaxHeartRate.resolve(profile?.maxHrBpm, profile?.birthDate)

    /** What it moved from, for the direction. */
    val previousFtpWatts: Int? get() =
        ftpHistory.takeIf { it.size > 1 }?.let { it[it.size - 2].ftpWatts }
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
    private val databaseBackup: DatabaseBackup,
    private val workoutRepository: WorkoutRepository,
    private val cloudAccess: CloudAccess,
    private val accountRepository: AccountRepository,
    /** Whether this build has an endpoint at all — see [SettingsUiState]. */
    private val cloudConfigured: Boolean
) : ViewModel() {

    /** The three cloud facts this screen needs, gathered in one place. */
    private data class CloudState(
        val status: CloudSyncStatus = CloudSyncStatus.Off,
        val sessionMatchesProfile: Boolean = false,
        val ridesWaiting: Int = 0
    )

    /**
     * The rider and their FTP history together, because the state combine is
     * already at the width of its typed overload and these two are read as one
     * thing: what the number is now, and when it last moved (7.10.3).
     */
    private val profile = settingsRepository.settings
        .map { it.lastProfileId }
        .flatMapLatest { id ->
            if (id == null) {
                flowOf<Pair<UserEntity?, List<FtpHistoryEntity>>>(null to emptyList())
            } else {
                combine(
                    userRepository.observeUser(id),
                    userRepository.observeFtpHistory(id)
                ) { user, history -> user to history }
            }
        }

    /**
     * Whether this rider's rides are reaching the cloud (14.2.3).
     *
     * Folded in beside the sensor pair rather than added as a sixth flow to the
     * typed `combine` below, which is already at the width of its overload.
     *
     * The account question goes through [CloudAccess] rather than being
     * recomputed from `authUserId` here — a second implementation of the gate
     * is how a screen comes to say "backed up" for a rider the gate would
     * refuse, and `isAllowedFor` already folds in the build's credentials and
     * the rider's own backup switch.
     */
    private val cloudSync = settingsRepository.settings
        .map { it.lastProfileId }
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(CloudState())
            } else {
                combine(
                    workoutRepository.observeBacklog(id),
                    settingsRepository.settings,
                    accountRepository.accountState
                ) { backlog, settings, session ->
                    CloudState(
                        status = CloudSyncStatus.from(
                            hasAccount = cloudAccess.isAllowedFor(id),
                            pending = backlog.pending,
                            oldestRideAtMs = backlog.oldestTimestamp,
                            lastSyncAtMs = settings.lastCloudSyncAtMs,
                            lastError = settings.lastCloudSyncError,
                            lastErrorAtMs = settings.lastCloudSyncErrorAtMs
                        ),
                        // 15.2.8, and driving the AVD is what showed this was
                        // needed: the section read `hasAccount` off the profile
                        // row, so a tablet holding **no session at all** said
                        // "Backed up to your account" — with the status line
                        // below it silently absent, because that one does ask
                        // the gate. Two surfaces one card apart, disagreeing.
                        sessionMatchesProfile = session.accountIdOrNull != null &&
                            session.accountIdOrNull == userRepository.getUser(id)?.authUserId,
                        ridesWaiting = backlog.pending
                    )
                }
            }
        }

    private val sensors = combine(
        sensorRepository.heartRateStatus,
        sensorRepository.discoveredHeartRateDevices,
        cloudSync
    ) { status, devices, cloud -> Triple(status, devices, cloud) }

    /**
     * 21.1.3. Looked up on demand rather than observed: it is a one-off opening
     * guess offered while the rider is typing, not a number any screen shows.
     */
    private val _highestRecordedHr = MutableStateFlow<Int?>(null)

    private val volume = combine(
        volumeController.mediaVolume,
        volumeController.lastError,
        _highestRecordedHr
    ) { level, error, highestHr -> Triple(level, error, highestHr) }

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        profile,
        sensors,
        volume,
        calibrationRepository.state
    ) { settings, (user, ftpHistory), (hrStatus, hrDevices, cloudSync), (mediaVolume, volumeError, highestHr), calibration ->
        SettingsUiState(
            settings = settings,
            profile = user,
            ftpHistory = ftpHistory,
            heartRateStatus = hrStatus,
            heartRateDevices = hrDevices,
            mediaVolume = mediaVolume,
            volumeError = volumeError,
            calibration = calibration,
            cloudSync = cloudSync.status,
            sessionMatchesProfile = cloudSync.sessionMatchesProfile,
            ridesWaiting = cloudSync.ridesWaiting,
            cloudConfigured = cloudConfigured,
            highestRecordedHr = highestHr
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = SettingsUiState(cloudConfigured = cloudConfigured)
    )

    /**
     * FTP and weight in **one** write.
     *
     * They used to be two — `setFtp` and `setWeight`, each launching its own
     * coroutine off one tap of Save, each doing read-modify-write on the same
     * profile row. That is a race, and it silently ate the rider's edit: the
     * weight write read the profile before the FTP write committed, then put
     * the *old* FTP back on its way past. Typing 215 into the field and
     * pressing Save left 200 in the database, with the screen showing 215 until
     * the next launch.
     *
     * It had been there the whole time and was invisible. What found it was
     * 7.9's history — two `ManualEdit` rows for the same value, twenty-three
     * seconds apart, which can only happen if the number went back to 200 in
     * between. Build the feature that reads the data, then look at the data.
     *
     * Nulls mean "unchanged", not zero, so a rider who edits one field does not
     * have to have a valid value in the other.
     */
    fun saveRider(ftpWatts: Int?, weightKg: Double?) {
        val profile = uiState.value.profile ?: return
        viewModelScope.launch {
            userRepository.save(
                profile.copy(
                    ftpWatts = ftpWatts ?: profile.ftpWatts,
                    weightKg = weightKg ?: profile.weightKg
                ),
                // 7.9.2. A number the rider typed is a *claim*; the chart draws
                // it differently from one the app measured off a 20-minute peak.
                // Ignored when the FTP has not moved — `save` records a change,
                // not a save.
                ftpSource = FtpChangeSource.ManualEdit
            )
        }
    }

    /**
     * What heart-rate zones are computed from (21.1.1, 21.1.3).
     *
     * **One tap is one write**, both columns together, for exactly the reason
     * `saveRider` above carries at length: two coroutines doing read-modify-
     * write on one row eat each other's field, and it took 7.9's own history to
     * notice the last time.
     *
     * Null here means **clear it**, not "unchanged" — the opposite of
     * `saveRider`'s convention, and deliberately so. These two fields are the
     * only ones on the profile a rider can legitimately want to *remove*: the
     * app asked for personal data and taking it back has to be possible, so
     * "leave the box empty" cannot be the one instruction it ignores.
     */
    fun saveHeartRateBasis(maxHrBpm: Int?, birthDate: Long?) {
        val profile = uiState.value.profile ?: return
        viewModelScope.launch {
            userRepository.save(profile.copy(maxHrBpm = maxHrBpm, birthDate = birthDate))
        }
    }

    /**
     * 21.1.3. The best opening guess the app can make, from its own samples.
     *
     * The rider is resolved from the settings flow rather than from
     * `uiState.value.profile`, and that is not a stylistic choice — it is a
     * defect found by driving the screen. The section asks for this from a
     * `LaunchedEffect(Unit)` on its first composition, which happens while
     * `uiState` is still the default and the profile is still null, so the
     * offer read the id as absent and silently never appeared. A rider with 382
     * recorded samples was shown no suggestion at all, with nothing looking
     * broken.
     */
    fun loadHighestRecordedHeartRate() {
        viewModelScope.launch {
            val userId = settingsRepository.settings.first().lastProfileId ?: return@launch
            _highestRecordedHr.value = workoutRepository.highestHeartRate(userId)
        }
    }

    fun setHouseholdVisible(visible: Boolean) {
        val userId = uiState.value.profile?.localUserId ?: return
        viewModelScope.launch { userRepository.setHouseholdVisible(userId, visible) }
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
            // Marked only on success, and this is the whole reason the reminder
            // can be trusted (23.3.1): recording a backup that failed would
            // tell the rider they are safe on precisely the day they are not.
            if (result.isSuccess) settingsRepository.markBackedUp()
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
                databaseBackup = ServiceLocator.databaseBackup,
                workoutRepository = ServiceLocator.workoutRepository,
                cloudAccess = ServiceLocator.cloudAccess,
                accountRepository = ServiceLocator.accountRepository,
                cloudConfigured = ServiceLocator.authRepository.cloudConfigured
            )
        }
    }
}
