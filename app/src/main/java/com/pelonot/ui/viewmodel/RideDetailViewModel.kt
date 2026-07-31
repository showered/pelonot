package com.pelonot.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.data.repository.WorkoutRepository
import com.pelonot.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RideDetailUiState(
    val workout: WorkoutEntity? = null,
    val classTitle: String? = null,
    val isLoading: Boolean = true,
    val isDeleted: Boolean = false
) {
    val displayTitle: String get() = classTitle ?: "Just Ride"
}

/**
 * One ride from history.
 *
 * Deliberately not `PostRideViewModel` with a flag: that one runs the FTP
 * analyser over the whole metric series on load and offers to rewrite the
 * rider's FTP, which is the right thing ninety seconds after a ride and a
 * bizarre thing to do when someone opens a ride from last March.
 *
 * RPE stays editable (12.2.4). Riders rate a ride in the ninety seconds after
 * finishing it, which is exactly the worst moment to ask for a considered
 * number, and there has been no way to correct it afterwards.
 */
class RideDetailViewModel(
    private val workoutRepository: WorkoutRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RideDetailUiState())
    val uiState: StateFlow<RideDetailUiState> = _uiState.asStateFlow()

    fun load(workoutId: String) {
        if (workoutId.isBlank()) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }

        viewModelScope.launch {
            val workout = workoutRepository.getWorkout(workoutId)
            _uiState.update {
                it.copy(
                    workout = workout,
                    classTitle = workout?.classId?.let { id ->
                        workoutRepository.getClassTitle(id)
                    },
                    isLoading = false
                )
            }
        }
    }

    fun setRpe(rpe: Int) {
        val workout = _uiState.value.workout ?: return
        _uiState.update { it.copy(workout = workout.copy(rpeRating = rpe)) }
        viewModelScope.launch { workoutRepository.setRpe(workout.id, rpe) }
    }

    /**
     * Deletes for real, with no undo window.
     *
     * The history list can hold a delete back because the row is still on
     * screen to put back. Here the rider has confirmed a named ride on a screen
     * that is about to close behind it, and pretending otherwise would leave a
     * ride visible in history that the app has already agreed to destroy.
     */
    fun delete(onDeleted: () -> Unit) {
        val id = _uiState.value.workout?.id ?: return onDeleted()
        viewModelScope.launch {
            workoutRepository.discardWorkout(id)
            _uiState.update { it.copy(isDeleted = true) }
            onDeleted()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            RideDetailViewModel(workoutRepository = ServiceLocator.workoutRepository)
        }
    }
}
