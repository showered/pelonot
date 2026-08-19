package com.pelonot.data.sensor

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow

/**
 * How often a live number on screen is allowed to change (11.6.7).
 *
 * The board reports several times a second and every emission used to be
 * rendered, so a rider glancing down at their cadence got a blur where a number
 * should be. Two updates a second is the compromise the first real ride asked
 * for: fast enough that the display still feels attached to the pedals, slow
 * enough that the eye can land on a figure and read it.
 */
const val DISPLAY_INTERVAL_MS = 500L

/**
 * The same values, paced so a human can read them.
 *
 * **This is a display concern and touches nothing that is recorded.** The
 * recorder reads `SensorRepository.sensorReading.value` once a second and gets
 * the raw reading; the rider's permanent record is unchanged by anything here.
 *
 * Semantics: the first value goes out immediately — a rider who has just
 * started pedalling should not watch `--` for half a second — and after that at
 * most one value per [intervalMs], always the **latest**. Intermediate values
 * are dropped rather than queued, so a burst cannot build a backlog that plays
 * out in slow motion after the rider has stopped.
 *
 * **This paces; it does not average.** It used to be able to say the stronger
 * thing — that every number on the screen was one the board actually reported —
 * and since 11.6.20 that is no longer true of the **watts**, which are a
 * three-second mean by the time they reach here ([PowerSmoother]). The owner
 * asked for it and it is the right trade for the one metric whose raw form is
 * unreadable out of the saddle; the sentence is changed rather than left
 * standing, because a promise nobody has revisited is how a comment starts
 * lying. Cadence, resistance and heart rate still keep it.
 */
fun <T> Flow<T>.atDisplayRate(intervalMs: Long = DISPLAY_INTERVAL_MS): Flow<T> = flow {
    // conflate() keeps the producer running at full speed while this collector
    // sleeps, and hands over only the newest value when it wakes.
    conflate().collect { value ->
        emit(value)
        delay(intervalMs)
    }
}
