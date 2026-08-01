package com.pelonot.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pelonot.data.sensor.SimulatedSensorSource

/**
 * Drives the simulated rider's misbehaviour from `adb`.
 *
 * The simulated source rides a smooth effort wave, never stops and never lies,
 * so three separate things could once only be seen on a real bike with a real
 * person on it — which `CLAUDE.md` rightly calls a perishable resource:
 *
 * ```bash
 * # The rider stops pedalling: auto-pause (19.1.2), the gap a stop leaves.
 * adb shell am broadcast -a com.pelonot.debug.COAST \
 *   -n com.pelonot/com.pelonot.debug.DebugTelemetryReceiver --ei seconds 40
 *
 * # The board reports nonsense: the overlay corruption of 2.7.
 * adb shell am broadcast -a com.pelonot.debug.CORRUPT \
 *   -n com.pelonot/com.pelonot.debug.DebugTelemetryReceiver --ei seconds 40
 *
 * # The board goes quiet without failing: the dead source of 2.7.4.
 * adb shell am broadcast -a com.pelonot.debug.SILENCE \
 *   -n com.pelonot/com.pelonot.debug.DebugTelemetryReceiver --ei seconds 20
 * ```
 *
 * Debug source set only: this class does not exist in a release build, so the
 * exported receiver it needs cannot be reached in one.
 */
class DebugTelemetryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val seconds = intent.getIntExtra(EXTRA_SECONDS, DEFAULT_SECONDS)
        when (intent.action) {
            ACTION_CORRUPT -> {
                SimulatedSensorSource.corruptFor(seconds)
                Log.i(TAG, "Simulated board reports corrupt telemetry for ${seconds}s")
            }

            ACTION_SILENCE -> {
                SimulatedSensorSource.silenceFor(seconds)
                Log.i(TAG, "Simulated board goes silent — without failing — for ${seconds}s")
            }

            else -> {
                SimulatedSensorSource.coastFor(seconds)
                Log.i(TAG, "Simulated rider stops pedalling for ${seconds}s")
            }
        }
    }

    private companion object {
        const val TAG = "DebugTelemetry"
        const val EXTRA_SECONDS = "seconds"
        const val DEFAULT_SECONDS = 30
        const val ACTION_CORRUPT = "com.pelonot.debug.CORRUPT"
        const val ACTION_SILENCE = "com.pelonot.debug.SILENCE"
    }
}
