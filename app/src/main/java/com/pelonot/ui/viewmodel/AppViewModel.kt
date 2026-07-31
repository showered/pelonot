package com.pelonot.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.data.repository.AppSettings
import com.pelonot.data.repository.ClassPlan
import com.pelonot.data.repository.ClassRepository
import com.pelonot.data.repository.DashboardStats
import com.pelonot.data.repository.SettingsRepository
import com.pelonot.data.repository.UserRepository
import com.pelonot.data.repository.WorkoutRepository
import com.pelonot.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Everything the navigation graph needs, sourced from Room and DataStore.
 *
 * @property isLoading True until the first emission from every source, so the
 *   UI can tell "no profiles yet" apart from "profiles not loaded yet". The
 *   previous code could not distinguish these and briefly rendered an empty
 *   profile grid on every launch.
 */
data class AppUiState(
    val settings: AppSettings = AppSettings(),
    val profiles: List<UserEntity> = emptyList(),
    val classes: List<ClassPlan> = emptyList(),
    val dashboardStats: DashboardStats = DashboardStats(),
    val isLoading: Boolean = true,
    /**
     * A ride the app was killed in the middle of. Non-null means the rider is
     * about to be asked what to do with it.
     */
    val recoverableWorkout: WorkoutEntity? = null
) {
    val selectedProfile: UserEntity?
        get() = profiles.firstOrNull { it.localUserId == settings.lastProfileId }
}

/**
 * Replaces reading Room directly from inside composables, which ran database
 * queries on every recomposition path and lost all its state on rotation.
 */
@Suppress("OPT_IN_USAGE") // flatMapLatest
class AppViewModel(
    private val settingsRepository: SettingsRepository,
    private val userRepository: UserRepository,
    classRepository: ClassRepository,
    private val workoutRepository: WorkoutRepository
) : ViewModel() {

    /**
     * Checked once at launch rather than through `WorkoutService`.
     *
     * The service does expose this, but nothing binds to it on a cold start —
     * which is exactly the case a crash leaves behind — so the prompt would
     * never have appeared.
     */
    private val _recoverableWorkout = MutableStateFlow<WorkoutEntity?>(null)

    init {
        viewModelScope.launch {
            _recoverableWorkout.value = workoutRepository.findRecoverableWorkout()
        }
    }

    private val dashboardStats = settingsRepository.settings
        .map { it.lastProfileId }
        .flatMapLatest { profileId ->
            // A guest has no history to summarise.
            if (profileId == null) flowOf(DashboardStats())
            else workoutRepository.observeDashboardStats(profileId)
        }

    val uiState: StateFlow<AppUiState> = combine(
        settingsRepository.settings,
        userRepository.allUsers,
        classRepository.allPlans,
        dashboardStats,
        _recoverableWorkout
    ) { settings, profiles, classes, stats, recoverable ->
        AppUiState(
            settings = settings,
            profiles = profiles,
            classes = classes,
            dashboardStats = stats,
            isLoading = false,
            recoverableWorkout = recoverable
        )
    }.stateIn(
        scope = viewModelScope,
        // Keeps the state alive briefly across configuration changes so a
        // rotation does not re-query the database.
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = AppUiState()
    )

    fun createProfile(name: String, weightKg: Double?, ftpWatts: Int, onCreated: (Int) -> Unit) {
        viewModelScope.launch {
            val saved = userRepository.save(
                UserEntity(
                    name = name.trim(),
                    weightKg = weightKg ?: DEFAULT_WEIGHT_KG,
                    ftpWatts = ftpWatts
                )
            )
            settingsRepository.setLastProfileId(saved.localUserId)
            onCreated(saved.localUserId)
        }
    }

    fun selectProfile(userId: Int?) {
        viewModelScope.launch { settingsRepository.setLastProfileId(userId) }
    }

    /**
     * Keeps an interrupted ride, rebuilding its totals from the samples that
     * were written before the process died.
     */
    fun recoverWorkout(onRecovered: (String) -> Unit) {
        val workoutId = _recoverableWorkout.value?.id ?: return
        viewModelScope.launch {
            val recovered = workoutRepository.recoverWorkout(workoutId)
            _recoverableWorkout.value = null
            if (recovered != null) onRecovered(recovered.id)
        }
    }

    fun discardRecoverableWorkout() {
        viewModelScope.launch {
            workoutRepository.clearRecoverableWorkouts()
            _recoverableWorkout.value = null
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val DEFAULT_WEIGHT_KG = 70.0

        val Factory = viewModelFactory {
            AppViewModel(
                settingsRepository = ServiceLocator.settingsRepository,
                userRepository = ServiceLocator.userRepository,
                classRepository = ServiceLocator.classRepository,
                workoutRepository = ServiceLocator.workoutRepository
            )
        }
    }
}

/** Small helper so each ViewModel's factory is a single expression. */
inline fun <reified VM : ViewModel> viewModelFactory(
    crossinline create: () -> VM
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
}
