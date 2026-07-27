package com.pelonot.data.service

import com.pelonot.data.local.entity.WorkoutMetricEntity

/**
 * Represents an active or completed workout session.
 *
 * @property workoutId Unique identifier for this workout session (UUID)
 * @property classId Reference to the ClassTemplate being followed (0 for free ride)
 * @property startTime System.currentTimeMillis() when the workout started
 * @property elapsedSeconds Total elapsed time in seconds
 * @property metrics List of WorkoutMetricEntity recorded during the session
 * @property intentModifier The rider's intent ("Reach New Milestones" or "Just Stay Fit")
 */
data class WorkoutSession(
    val workoutId: String,
    val classId: Int,
    val startTime: Long,
    var elapsedSeconds: Int = 0,
    val metrics: MutableList<WorkoutMetricEntity> = mutableListOf(),
    val intentModifier: String = "Just Stay Fit"
)

/**
 * Lifecycle states for a workout.
 */
enum class WorkoutState {
    Idle,
    Active,
    Paused,
    Completed
}
