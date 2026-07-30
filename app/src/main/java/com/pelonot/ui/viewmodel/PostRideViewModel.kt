package com.pelonot.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.data.repository.SettingsRepository
import com.pelonot.data.repository.UserRepository
import com.pelonot.data.repository.WorkoutRepository
import com.pelonot.data.service.PostWorkoutAnalyzer
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
    val saved: Boolean = false
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

    /** Guest rides are opt-in: discarding removes the row and its metrics. */
    fun discard(onDiscarded: () -> Unit) {
        val workoutId = _uiState.value.workout?.id
        viewModelScope.launch {
            if (workoutId != null) workoutRepository.discardWorkout(workoutId)
            onDiscarded()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            PostRideViewModel(
                workoutRepository = ServiceLocator.workoutRepository,
                userRepository = ServiceLocator.userRepository,
                settingsRepository = ServiceLocator.settingsRepository
            )
        }
    }
}
