package com.pelonot.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pelonot.data.sensor.SimulatedSensorSource

/**
 * Makes the simulated rider stop pedalling, from `adb`.
 *
 * The simulated source rides a smooth effort wave and **never stops**, so
 * everything about a rider standing still — auto-pause (19.1.2), the gap a stop
 * leaves in `workout_metrics`, what the averages do across one — could only be
 * seen on a real bike with a real person on it, which `CLAUDE.md` rightly calls
 * a perishable resource.
 *
 * ```bash
 * adb shell am broadcast -a com.pelonot.debug.COAST \
 *   -n com.pelonot/com.pelonot.debug.DebugTelemetryReceiver --ei seconds 40
 * ```
 *
 * Debug source set only: this class does not exist in a release build, so the
 * exported receiver it needs cannot be reached in one.
 */
class DebugTelemetryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val seconds = intent.getIntExtra(EXTRA_SECONDS, DEFAULT_SECONDS)
        SimulatedSensorSource.coastFor(seconds)
        Log.i(TAG, "Simulated rider stops pedalling for ${seconds}s")
    }

    private companion object {
        const val TAG = "DebugTelemetry"
        const val EXTRA_SECONDS = "seconds"
        const val DEFAULT_SECONDS = 30
    }
}
