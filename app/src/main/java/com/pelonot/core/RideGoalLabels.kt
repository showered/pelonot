package com.pelonot.core

import com.pelonot.domain.model.RideGoal
import com.pelonot.domain.model.UnitSystem

fun RideGoal.progressLabel(elapsedSec: Int, distanceKm: Double, units: UnitSystem): String = when (this) {
    is RideGoal.Time -> "${Formatters.duration((seconds - elapsedSec).coerceAtLeast(0))} left · ${Formatters.minutes(seconds)} goal"
    is RideGoal.Distance -> "${Formatters.distanceValue(distanceKm, units)} / ${Formatters.distance(km, units)}"
}
