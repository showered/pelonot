package com.pelonot.core

import com.pelonot.domain.model.UnitSystem
import java.util.Locale

/**
 * Duration and metric formatting shared across the HUD, notification, summary
 * and class screens. Each of those previously carried its own private copy of
 * `formatDuration`, and they had drifted — some wrapped past an hour, some did
 * not.
 *
 * This is also the **only** place a stored SI value becomes miles or pounds.
 * See [UnitSystem]: nothing between the sensor and the database ever sees an
 * imperial number.
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

    /** Rounded the way a rider reads a file size, not the way a disk reports one. */
    fun fileSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
        bytes >= 1024 -> String.format(Locale.US, "%.0f kB", bytes / 1024.0)
        else -> "$bytes bytes"
    }

    /**
     * Whole kilojoules — **11.6.12, the owner's call**: nothing in the watt
     * family shows a rider a decimal place.
     *
     * A tenth of a kilojoule is 0.04% of a class and no rider has ever acted
     * on one; what it cost was real, because the tenth is what pushed the ride
     * screen's OUTPUT tile wide enough to clip its own unit label once the
     * total reached three digits. Display only — the stored value keeps its
     * fraction, the same way the board's fractional watts do (14.4.6).
     */
    fun kilojoules(value: Double): String = "${kilojoulesValue(value)} kJ"

    /** Kilojoules without the unit, for a tile that labels itself. */
    fun kilojoulesValue(value: Double): String = String.format(Locale.US, "%.0f", value)

    fun bpm(value: Int?): String = value?.let { "$it BPM" } ?: "--"

    // ── Unit-dependent ──────────────────────────────────────────────
    // Each takes the stored SI value and the rider's preference. There is no
    // no-argument overload on purpose: a caller that forgets to pass the
    // preference would silently show kilometres to a rider who asked for miles,
    // which is precisely the bug this replaces.

    /** Distance, from a stored value in kilometres. */
    fun distance(km: Double, units: UnitSystem): String =
        String.format(Locale.US, "%.2f %s", units.distanceFromKm(km), units.distanceLabel)

    /** Distance without its unit, for a metric tile that labels itself. */
    fun distanceValue(km: Double, units: UnitSystem): String =
        String.format(Locale.US, "%.2f", units.distanceFromKm(km))

    /** Speed, from a stored value in km/h. */
    fun speed(kmh: Double, units: UnitSystem): String =
        String.format(Locale.US, "%.1f %s", units.speedFromKmh(kmh), units.speedLabel)

    /** Body weight, from a stored value in kilograms. */
    fun bodyWeight(kg: Double, units: UnitSystem): String =
        String.format(Locale.US, "%.1f %s", units.weightFromKg(kg), units.weightLabel)
}
