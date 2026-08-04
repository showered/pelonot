package com.pelonot.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pelonot.data.sensor.PelotonSensorServiceSource
import com.pelonot.data.service.RaceDebug
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
 *
 * # The live leaderboard draws on a simulated ride (24.3.13a). This one is
 * # not a lie about the telemetry — see RaceDebug for why.
 * adb shell am broadcast -a com.pelonot.debug.RACE \
 *   -n com.pelonot/com.pelonot.debug.DebugTelemetryReceiver --ez on true
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

            // 24.3.13a. Not a lie about the telemetry — see [RaceDebug]. What
            // is recorded is untouched; only the live comparison stops
            // refusing to draw itself on a simulated ride.
            ACTION_RACE -> {
                val on = intent.getBooleanExtra(EXTRA_ON, true)
                RaceDebug.ignoreMeasuredGate = on
                Log.i(
                    TAG,
                    if (on) {
                        "The race will run on modelled watts (the record still says modelled)"
                    } else {
                        "The race is back behind the measured-power gate"
                    }
                )
            }

            ACTION_TRACE -> {
                PelotonSensorServiceSource.traceFor(seconds)
                Log.i(TAG, "Tracing raw sensor events for ${seconds}s")
            }

            // 2.7.1b. Takes effect on the next bind, so end the ride and start
            // another — the registration is sent once, when the service binds.
            ACTION_REGISTER -> {
                val only = intent.getStringExtra(EXTRA_ONLY)
                if (only == "resistance") {
                    PelotonSensorServiceSource.registerResistanceOnly()
                } else {
                    PelotonSensorServiceSource.registerEverything()
                }
                Log.i(
                    TAG,
                    "Next bind registers: " +
                        PelotonSensorServiceSource.registerCommands.joinToString()
                )
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
        const val ACTION_TRACE = "com.pelonot.debug.TRACE"
        const val ACTION_RACE = "com.pelonot.debug.RACE"
        const val EXTRA_ON = "on"
        const val ACTION_REGISTER = "com.pelonot.debug.REGISTER"
        const val EXTRA_ONLY = "only"
    }
}
