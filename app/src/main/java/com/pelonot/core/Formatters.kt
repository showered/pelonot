package com.pelonot.core

import java.util.Locale

/**
 * Duration and metric formatting shared across the HUD, notification, summary
 * and class screens. Each of those previously carried its own private copy of
 * `formatDuration`, and they had drifted — some wrapped past an hour, some did
 * not.
 */
object Formatters {

    /** `mm:ss`, or `h:mm:ss` once the duration passes an hour. */
    fun duration(totalSeconds: Int): String {
        val seconds = totalSeconds.coerceAtLeast(0)
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, secs)
        }
    }

    /** Rounded whole minutes, for class durations. */
    fun minutes(totalSeconds: Int): String = "${totalSeconds / 60} min"

    fun watts(value: Double): String = String.format(Locale.US, "%.0f W", value)

    fun rpm(value: Double): String = String.format(Locale.US, "%.0f RPM", value)

    fun kilojoules(value: Double): String = String.format(Locale.US, "%.1f kJ", value)

    fun kilometres(value: Double): String = String.format(Locale.US, "%.2f km", value)

    fun bpm(value: Int?): String = value?.let { "$it BPM" } ?: "--"
}
