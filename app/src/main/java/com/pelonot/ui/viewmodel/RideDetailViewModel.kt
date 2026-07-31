package com.pelonot.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.data.repository.UserRepository
import com.pelonot.data.repository.WorkoutRepository
import com.pelonot.di.ServiceLocator
import com.pelonot.domain.chart.ChartSample
import com.pelonot.domain.chart.RideChartBuilder
import com.pelonot.domain.chart.RideCharts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RideDetailUiState(
    val workout: WorkoutEntity? = null,
    val classTitle: String? = null,
    val isLoading: Boolean = true,
    val isDeleted: Boolean = false,
    /**
     * The ride's own time series, reduced to what can be drawn (16.1).
     *
     * Null while it is still being built, which is a different thing from a
     * ride with nothing in it — a screen that cannot tell those apart flashes
     * "no data" at every rider on the way in.
     */
    val charts: RideCharts? = null
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
    private val workoutRepository: WorkoutRepository,
    private val userRepository: UserRepository
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

            // Built after the summary is on screen, not before it. A ride's
            // series is a few thousand rows and the totals are the thing the
            // rider opened this screen for; making them wait on the charts
            // would be the wrong way round.
            if (workout != null) buildCharts(workout)
        }
    }

    /**
     * Off the main thread and computed once (16.2.3).
     *
     * The FTP used for the zone bands is the rider's **current** one, which is
     * a deliberate simplification worth knowing about: a ride from before an
     * FTP change is banded against today's zones, not the ones it was ridden
     * under. Storing the FTP on the workout row would fix it and needs a
     * migration (12.5).
     */
    private suspend fun buildCharts(workout: WorkoutEntity) {
        val charts = withContext(Dispatchers.Default) {
            val metrics = workoutRepository.getMetrics(workout.id)
            val ftp = workout.userId
                ?.let { userRepository.getUser(it)?.ftpWatts }
                ?: UserEntity.DEFAULT_FTP

            RideChartBuilder.build(
                samples = metrics.map { metric ->
                    ChartSample(
                        timestampSec = metric.timestampSec,
                        powerWatts = metric.power,
                        cadenceRpm = metric.cadence,
                        // Preserved as null: 16.1.2 depends on it.
                        heartRateBpm = metric.heartRate
                    )
                },
                ftpWatts = ftp
            )
        }
        _uiState.update { it.copy(charts = charts) }
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
            RideDetailViewModel(
                workoutRepository = ServiceLocator.workoutRepository,
                userRepository = ServiceLocator.userRepository
            )
        }
    }
}
