package com.pelonot.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.data.repository.SettingsRepository
import com.pelonot.data.repository.UserRepository
import com.pelonot.data.repository.WorkoutRepository
import com.pelonot.data.service.PostWorkoutAnalyzer
import com.pelonot.data.worker.WorkoutSyncWorker
import com.pelonot.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PostRideUiState(
    val workout: WorkoutEntity? = null,
    val isLoading: Boolean = true,
    val rpe: Int? = null,
    val currentFtp: Int = 0,
    val proposedFtp: Int? = null,
    val saved: Boolean = false,
    /** Profiles a guest ride could be filed against. */
    val profiles: List<UserEntity> = emptyList()
) {
    val hasBreakthrough: Boolean get() = proposedFtp != null
}

/**
 * Loads the completed ride's real figures from the database.
 *
 * The summary screen previously received hardcoded zeros from the navigation
 * graph — `durationSec = 0, totalOutputKj = 0.0, avgPower = 0.0, …` — so every
 * ride, however hard, reported an empty result. The RPE buttons were likewise
 * wired to `onClick = { /* TODO */ }` and recorded nothing.
 */
class PostRideViewModel(
    private val workoutRepository: WorkoutRepository,
    private val userRepository: UserRepository,
    private val settingsRepository: SettingsRepository,
    private val analyzer: PostWorkoutAnalyzer = PostWorkoutAnalyzer()
) : ViewModel() {

    private val _uiState = MutableStateFlow(PostRideUiState())
    val uiState: StateFlow<PostRideUiState> = _uiState.asStateFlow()

    fun load(workoutId: String) {
        if (workoutId.isBlank()) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }

        viewModelScope.launch {
            val workout = workoutRepository.getWorkout(workoutId)
            val profileId = settingsRepository.settings.first().lastProfileId
            val currentFtp = profileId?.let { userRepository.getUser(it)?.ftpWatts } ?: 0

            val proposed = if (workout != null && currentFtp > 0) {
                val metrics = workoutRepository.getMetrics(workoutId)
                analyzer.analyze(
                    metrics = metrics,
                    currentFtp = currentFtp.toDouble()
                ).proposedFtp?.toInt()
            } else {
                null
            }

            _uiState.update {
                it.copy(
                    workout = workout,
                    currentFtp = currentFtp,
                    proposedFtp = proposed,
                    isLoading = false
                )
            }
        }

        viewModelScope.launch {
            userRepository.allUsers.collect { users ->
                _uiState.update { it.copy(profiles = users) }
            }
        }
    }

    fun setRpe(rpe: Int) {
        val workoutId = _uiState.value.workout?.id ?: return
        _uiState.update { it.copy(rpe = rpe) }
        viewModelScope.launch { workoutRepository.setRpe(workoutId, rpe) }
    }

    fun acceptFtpProposal() {
        val proposed = _uiState.value.proposedFtp ?: return
        viewModelScope.launch {
            val profileId = settingsRepository.settings.first().lastProfileId ?: return@launch
            userRepository.updateFtp(profileId, proposed)
            _uiState.update { it.copy(currentFtp = proposed, proposedFtp = null) }
        }
    }

    fun declineFtpProposal() {
        _uiState.update { it.copy(proposedFtp = null) }
    }

    /**
     * Files a guest ride against an existing profile.
     *
     * The ride is already on disk with its full metric series; only its owner
     * changes. Syncing is enqueued here because the service deliberately did
     * not — at the time it finished, the ride had no owner to sync it for.
     */
    fun saveToProfile(context: Context, userId: Int, onSaved: () -> Unit) {
        val workoutId = _uiState.value.workout?.id ?: return onSaved()
        viewModelScope.launch {
            workoutRepository.assignToUser(workoutId, userId)
            WorkoutSyncWorker.enqueueIfAllowed(context.applicationContext, workoutId, userId)
            _uiState.update { it.copy(saved = true) }
            onSaved()
        }
    }

    /** Creates a profile for a guest who has decided to keep their rides. */
    fun saveToNewProfile(
        context: Context,
        name: String,
        weightKg: Double?,
        ftpWatts: Int,
        onSaved: () -> Unit
    ) {
        val workoutId = _uiState.value.workout?.id ?: return onSaved()
        viewModelScope.launch {
            val profile = userRepository.save(
                UserEntity(
                    name = name.trim(),
                    weightKg = weightKg ?: DEFAULT_WEIGHT_KG,
                    ftpWatts = ftpWatts
                )
            )
            workoutRepository.assignToUser(workoutId, profile.localUserId)
            // Riding as a guest and then keeping the ride is a strong signal
            // that this is who the rider now is.
            settingsRepository.setLastProfileId(profile.localUserId)
            WorkoutSyncWorker.enqueueIfAllowed(
                context.applicationContext,
                workoutId,
                profile.localUserId
            )
            _uiState.update { it.copy(saved = true) }
            onSaved()
        }
    }

    /**
     * Throws the ride away: the row and its whole metric series.
     *
     * Offered to guests since 8.4 and, since 11.1a.4, to riders with a profile
     * too — the session that was a warm-up, a mistake, or somebody else
     * pedalling for thirty seconds. It is local only: an already-uploaded ride
     * stays in the cloud until tombstones exist (12.3.5 / 15.3.4). A sync
     * enqueued for it drops itself when it finds the row gone.
     */
    fun discard(onDiscarded: () -> Unit) {
        val workoutId = _uiState.value.workout?.id
        viewModelScope.launch {
            if (workoutId != null) workoutRepository.discardWorkout(workoutId)
            onDiscarded()
        }
    }

    companion object {
        private const val DEFAULT_WEIGHT_KG = 70.0

        val Factory = viewModelFactory {
            PostRideViewModel(
                workoutRepository = ServiceLocator.workoutRepository,
                userRepository = ServiceLocator.userRepository,
                settingsRepository = ServiceLocator.settingsRepository
            )
        }
    }
}
