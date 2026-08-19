package com.pelonot.data.sensor

/**
 * The mean of the last few seconds of watts, for the two screens to draw
 * (PLAN 11.6.20).
 *
 * The owner, out of the saddle on a heavy gear: *"All the down pedals will be
 * super high wattage, and then inbetween it's lower, this causes the output to
 * jump up and down quite dramatically. Would be good to smooth it out
 * slightly."* That is a real physical effect and not a sensor fault — a
 * standing rider delivers torque in two pulses a revolution, which at 60 rpm is
 * a two-hertz square wave in the watts, reported faithfully. Nothing here is
 * impossible, so `TelemetryBounds` is the wrong instrument: the fence turns
 * impossible values into gaps, and these values are true.
 *
 * **It smooths one metric and it smooths nothing that is recorded.**
 * `SensorRepository` publishes the raw stream for the recorder and this for the
 * screens, so `workout_metrics` keeps every sample the board sent: the chart,
 * the averages, the twenty-minute peak that moves a rider's FTP and everything
 * in `calibration/` are untouched by construction.
 *
 * Cadence, resistance and heart rate come through unaltered, deliberately.
 * Cadence is already a rate over a whole revolution and does not pulse;
 * resistance moves when a hand moves it, and a knob whose number lags the hand
 * feels broken; a heart rate is nullable, and a mean would have to decide what
 * an absent sample contributes when the honest answer is nothing.
 *
 * This does cost `DisplayRate`'s promise that *"nothing is averaged and nothing
 * is invented"* — one number on the screen is now a mean of several. That is
 * the owner's own request and the right trade for the one metric whose raw form
 * is unreadable, and it is said out loud there rather than quietly broken.
 */
class PowerSmoother(private val windowMs: Long = WINDOW_MS) {

    private val samples = ArrayDeque<Sample>()

    private data class Sample(val timestampMs: Long, val watts: Double)

    /**
     * [reading] with its watts replaced by the mean of the window ending at its
     * own timestamp.
     *
     * **A gap resets it rather than being averaged across.** A reading that
     * arrives after a silence longer than the window is the first of a new
     * effort, not the continuation of an old one, and carrying a pre-dropout
     * mean into it would draw the watts of a rider who had stopped. Same
     * argument as `WorkoutMetricsCalculator`'s gap clamp and as
     * [SensorReading.isStaleAt]: an absence is not a measurement. A timestamp
     * that goes backwards — a new ride, a clock change — resets it too.
     */
    fun smooth(reading: SensorReading): SensorReading {
        val newest = samples.lastOrNull()
        if (newest != null &&
            (reading.timestampMs < newest.timestampMs ||
                reading.timestampMs - newest.timestampMs > windowMs)
        ) {
            samples.clear()
        }

        samples.addLast(Sample(reading.timestampMs, reading.powerWatts))
        val cutoff = reading.timestampMs - windowMs
        while (samples.size > 1 && samples.first().timestampMs < cutoff) {
            samples.removeFirst()
        }

        return reading.copy(powerWatts = samples.sumOf { it.watts } / samples.size)
    }

    /** Forgets the window. For a source restart, where continuity is a lie. */
    fun reset() = samples.clear()

    companion object {
        /**
         * Three seconds, which is Garmin's *3s power* and the industry's answer
         * to exactly this (11.6.20a).
         *
         * At 60 rpm it spans three full pedal strokes, so both pulses and both
         * gaps are inside every window. One second is barely one stroke and the
         * ripple survives it; ten seconds stops answering *what am I doing
         * now*, which is the question the tile exists for, and would leave the
         * amber lying for several seconds after a rider had fixed their effort.
         */
        const val WINDOW_MS = 3_000L
    }
}
