package com.pelonot.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.data.repository.ClassRepository
import com.pelonot.data.repository.UserRepository
import com.pelonot.data.repository.WorkoutRepository
import com.pelonot.di.ServiceLocator
import com.pelonot.domain.chart.ChartSample
import com.pelonot.domain.chart.RideChartBuilder
import com.pelonot.domain.chart.RideCharts
import com.pelonot.ui.components.RideGhost
import com.pelonot.ui.components.RideRival
import com.pelonot.domain.export.ExportFormat
import com.pelonot.domain.export.ExportRide
import com.pelonot.domain.export.ExportSample
import com.pelonot.domain.export.RideExport
import com.pelonot.domain.model.Interval
import com.pelonot.domain.model.MaxHeartRate
import com.pelonot.domain.model.PowerProvenance
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
    val charts: RideCharts? = null,
    /**
     * Housemates who have ridden this same class and can be drawn behind it
     * (24.3.1). Empty is the ordinary answer and draws nothing.
     */
    val rivals: List<RideRival> = emptyList(),
    /** The one currently drawn, or null. Only ever one: two ghosts is a graph. */
    val ghost: RideGhost? = null
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
    private val userRepository: UserRepository,
    private val classRepository: ClassRepository
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
            // The whole plan, not just its title: 16.1.5 draws the intervals it
            // prescribed under the trace of what was actually ridden.
            val plan = workout?.classId?.let { classRepository.getPlan(it) }
            _uiState.update {
                it.copy(
                    workout = workout,
                    classTitle = plan?.title,
                    isLoading = false
                )
            }

            // Built after the summary is on screen, not before it. A ride's
            // series is a few thousand rows and the totals are the thing the
            // rider opened this screen for; making them wait on the charts
            // would be the wrong way round.
            if (workout != null) buildCharts(workout, plan?.intervals.orEmpty())
        }
    }

    /**
     * Off the main thread and computed once (16.2.3).
     *
     * The reduction itself is `buildRideCharts`, shared with the post-ride
     * summary (12.6.1) — including the FTP rule that decides the zone bands,
     * which is the part that must not exist twice (7.8).
     */
    private suspend fun buildCharts(workout: WorkoutEntity, intervals: List<Interval>) {
        // Read once, outside the CPU-bound block: it is two fallbacks for a
        // ride that recorded neither denominator of its own (7.8.3, 21.4.2a).
        val rider = workout.userId?.let { userRepository.getUser(it) }
        val charts = withContext(Dispatchers.Default) {
            buildRideCharts(
                workout = workout,
                metrics = workoutRepository.getMetrics(workout.id),
                intervals = intervals,
                riderFtp = rider?.ftpWatts,
                riderMaxHr = rider?.let { MaxHeartRate.resolve(it.maxHrBpm, it.birthDate) }
            )
        }
        _uiState.update { it.copy(charts = charts) }
        loadRivals(workout, charts)
    }

    /**
     * What else there is to draw this ride against — the rider's own previous
     * best at this class (16.3.4), then whoever else on this bike has ridden it
     * (24.3.1).
     *
     * **Gated on *this* ride's provenance as well as theirs.** The query
     * already refuses a rival whose watts were not measured, for 24.4.2's
     * reason; the symmetric half has to be checked here, because a modelled
     * trace of *mine* drawn against a measured one of theirs is the same lie
     * facing the other way. `PowerModel` is 137 W out at RMSE, which on a
     * power chart is most of the height of a zone — the comparison would look
     * exact and be fiction.
     *
     * A guest ride has no rider, so it has no housemates and asks nothing.
     */
    private suspend fun loadRivals(workout: WorkoutEntity, charts: RideCharts) {
        val classId = workout.classId ?: return
        val userId = workout.userId ?: return
        if (!charts.powerProvenance.isTrustworthyAsMeasured) return

        // The rider's own previous best at this class, first in the list
        // (16.3.4). **Before this ride, not best-ever**: a ride is measured
        // against what the rider had already done when they rode it, so the
        // comparison on a ride from March says the same thing next year, and a
        // personal best is never quietly drawn against the ride that beat it.
        val yourBest = workoutRepository.previousBestOfClass(
            classId = classId,
            userId = userId,
            excludingWorkoutId = workout.id,
            beforeMs = workout.timestamp
        )?.let {
            RideRival(
                workoutId = it.workoutId,
                name = "Your best",
                outputKj = it.outputKj,
                you = true
            )
        }

        val rivals = workoutRepository
            .householdRivals(classId, excludingWorkoutId = workout.id, excludingUserId = userId)
            .map { RideRival(it.workoutId, it.name, it.outputKj) }
        _uiState.update { it.copy(rivals = listOfNotNull(yourBest) + rivals) }
    }

    /**
     * Draw a housemate's ride behind this one, or clear it.
     *
     * Fetched on demand rather than with the screen: most riders never tap it,
     * and it is a second few-thousand-row series through the same reduction.
     */
    fun showGhost(workoutId: String?) {
        if (workoutId == null || workoutId == _uiState.value.ghost?.workoutId) {
            _uiState.update { it.copy(ghost = null) }
            return
        }
        val rival = _uiState.value.rivals.firstOrNull { it.workoutId == workoutId } ?: return

        viewModelScope.launch {
            val trace = withContext(Dispatchers.Default) {
                RideChartBuilder.build(
                    samples = workoutRepository.getMetrics(workoutId).map { metric ->
                        ChartSample(
                            timestampSec = metric.timestampSec,
                            powerWatts = metric.power,
                            cadenceRpm = metric.cadence,
                            heartRateBpm = metric.heartRate,
                            resistancePercent = metric.resistance
                        )
                    },
                    // Their zones are not drawn and their plan is not drawn —
                    // only the line. This chart already carries one rider's FTP
                    // bands and a second set would make it unreadable, so the
                    // ghost is deliberately a shape rather than a full record.
                    ftpWatts = 0
                ).power
            }
            if (trace.isEmpty) return@launch
            _uiState.update {
                it.copy(
                    ghost = RideGhost(
                        workoutId = rival.workoutId,
                        name = rival.name,
                        outputKj = rival.outputKj,
                        trace = trace,
                        you = rival.you
                    )
                )
            }
        }
    }

    /**
     * Builds the file the rider asked for (12.4.3).
     *
     * Reads the **samples**, never the aggregates — an export that disagreed
     * with the database would be worse than none. Returns null when the ride
     * has gone, which the caller reports rather than swallowing: an export that
     * silently produces nothing is exactly the failure shape the *Corrections*
     * table exists to stop.
     */
    suspend fun buildExport(format: ExportFormat): Pair<String, String>? {
        val workout = _uiState.value.workout ?: return null
        val title = _uiState.value.displayTitle

        return withContext(Dispatchers.Default) {
            val ride = ExportRide(
                id = workout.id,
                title = title,
                startedAtMillis = workout.timestamp,
                durationSec = workout.durationSec,
                totalOutputKj = workout.totalOutputKj,
                totalDistanceKm = workout.totalDistanceKm,
                avgPower = workout.avgPower,
                avgCadence = workout.avgCadence,
                avgHeartRate = workout.avgHr,
                // 23.4.3: the file says what resolution it is, because it is
                // the copy that leaves the app and can never be asked again.
                detailSec = workout.metricsDetailSec ?: 1
            )
            val samples = workoutRepository.getMetrics(workout.id).map { metric ->
                ExportSample(
                    timestampSec = metric.timestampSec,
                    cadenceRpm = metric.cadence,
                    resistancePercent = metric.resistance,
                    powerWatts = metric.power,
                    // Preserved as null all the way to the file.
                    heartRateBpm = metric.heartRate
                )
            }

            val content = when (format) {
                ExportFormat.Csv -> RideExport.csv(ride, samples)
                ExportFormat.Tcx -> RideExport.tcx(ride, samples)
            }
            RideExport.filename(ride, format) to content
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
            RideDetailViewModel(
                workoutRepository = ServiceLocator.workoutRepository,
                userRepository = ServiceLocator.userRepository,
                classRepository = ServiceLocator.classRepository
            )
        }
    }
}
