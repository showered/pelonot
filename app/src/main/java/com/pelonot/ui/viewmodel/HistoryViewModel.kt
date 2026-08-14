package com.pelonot.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelonot.data.local.dao.WorkoutListItem
import com.pelonot.data.repository.SettingsRepository
import com.pelonot.data.repository.WorkoutRepository
import com.pelonot.di.ServiceLocator
import com.pelonot.domain.model.RideDayGrouping
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryUiState(
    val days: List<RideDayGrouping.Day<WorkoutListItem>> = emptyList(),
    val totalRides: Int = 0,
    val isLoading: Boolean = true,
    val isGuest: Boolean = false,
    /**
     * Rides nobody has claimed (12.4.1), newest first.
     *
     * Kept apart from [days] rather than merged into them, because these are
     * not this rider's rides — they are rides with an open question on them,
     * and the same list is shown to every profile on the bike.
     */
    val unclaimed: List<WorkoutListItem> = emptyList(),
    /** The ride the undo snackbar is currently offering to bring back. */
    val pendingDeletion: WorkoutListItem? = null
) {
    val isEmpty: Boolean get() = !isLoading && days.isEmpty() && unclaimed.isEmpty()

    /** True while there are older rides beyond the loaded window. */
    val hasMore: Boolean get() = days.sumOf { it.rides.size } < totalRides
}

/**
 * Ride history.
 *
 * Every ride since the foreign-key fix has been writing a full per-second
 * series to `workout_metrics`, and until now there was no screen in the app
 * that showed a rider a ride they finished yesterday. The data layer was
 * already there; nothing rendered it.
 *
 * ### Why deletion is deferred rather than undone
 *
 * `workout_metrics` cascades off `workouts`, so deleting a ride takes its
 * per-second record with it and nothing can bring that back — an "undo" that
 * re-inserted the aggregates would silently hand the rider a ride with its
 * time series missing. So the delete is *held*: the row is filtered out of the
 * list immediately, the snackbar offers to put it back, and the delete only
 * reaches the database when the snackbar goes away.
 *
 * If the process dies while a delete is pending, the ride survives. That is the
 * safe direction to fail in.
 *
 * ### Why one list here is not this rider's
 *
 * 12.4.1. Every query on `workouts` is filtered to a profile, and a guest ride
 * has none — so until now a ride nobody claimed was not merely hard to re-file,
 * it was **invisible from every screen in the app** the moment the post-ride
 * summary closed. [HistoryUiState.unclaimed] is the one owner-less list, drawn
 * above the rider's own and never mixed into it.
 */
@Suppress("OPT_IN_USAGE") // flatMapLatest
class HistoryViewModel(
    private val workoutRepository: WorkoutRepository,
    private val settingsRepository: SettingsRepository,
    /**
     * Outlives this ViewModel on purpose — see [onCleared]. `viewModelScope`
     * is already cancelled by the time `onCleared` runs, so a delete launched
     * there would never reach the database.
     */
    private val applicationScope: CoroutineScope
) : ViewModel() {

    private val windowSize = MutableStateFlow(INITIAL_WINDOW)
    private val heldBack = MutableStateFlow<WorkoutListItem?>(null)

    private val userId = settingsRepository.settings.map { it.lastProfileId }

    val uiState: StateFlow<HistoryUiState> = userId
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(HistoryUiState(isLoading = false, isGuest = true))
            } else {
                combine(
                    windowSize.flatMapLatest { limit ->
                        workoutRepository.observeHistory(id, limit)
                    },
                    workoutRepository.observeCompletedCount(id),
                    workoutRepository.observeUnclaimedRides(),
                    heldBack
                ) { rides, total, unclaimed, pending ->
                    val visible = rides.filterNot { it.id == pending?.id }
                    HistoryUiState(
                        days = RideDayGrouping.group(visible) { it.timestamp },
                        // The count is what the database holds, so a ride held
                        // back for undo must not make the list look truncated.
                        // Only the rider's own rides are in it — an unclaimed
                        // one held back would otherwise shorten a window it was
                        // never part of, and hide the *Show older rides*
                        // button on a rider whose list really does go on.
                        totalRides = total -
                            if (rides.any { it.id == pending?.id }) 1 else 0,
                        isLoading = false,
                        unclaimed = unclaimed.filterNot { it.id == pending?.id },
                        pendingDeletion = pending
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = HistoryUiState()
        )

    fun loadMore() {
        windowSize.update { it + WINDOW_STEP }
    }

    /**
     * Hides the ride and starts the undo window. Nothing is written yet.
     *
     * A second delete while one is pending commits the first: the snackbar for
     * it has been replaced, so the rider has no way left to undo it, and
     * leaving the row hidden-but-present forever would be worse.
     */
    fun requestDelete(ride: WorkoutListItem) {
        heldBack.value?.let { commitDelete(it) }
        heldBack.value = ride
    }

    fun undoDelete() {
        heldBack.value = null
    }

    /** Called when the undo snackbar goes away, whichever way it went. */
    fun confirmPendingDelete() {
        val ride = heldBack.value ?: return
        heldBack.value = null
        commitDelete(ride)
    }

    private fun commitDelete(ride: WorkoutListItem) {
        applicationScope.launch { workoutRepository.discardWorkout(ride.id) }
    }

    override fun onCleared() {
        // Leaving the screen ends the undo window. Committing rather than
        // dropping the deletion matches what the rider was told: they asked for
        // the ride to go and then navigated away without taking it back.
        heldBack.value?.let(::commitDelete)
        super.onCleared()
    }

    companion object {
        /** Enough to fill any screen; a rider with 400 rides pages the rest. */
        private const val INITIAL_WINDOW = 60
        private const val WINDOW_STEP = 60
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory = viewModelFactory {
            HistoryViewModel(
                workoutRepository = ServiceLocator.workoutRepository,
                settingsRepository = ServiceLocator.settingsRepository,
                applicationScope = ServiceLocator.applicationScope
            )
        }
    }
}
