package com.pelonot.data.sensor

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.IOException

/**
 * Reads telemetry from the bike's own sensor board by binding Peloton's
 * `SensorService`, which is the only route available on a stock tablet.
 *
 * [SerialSensorSource] cannot work here. The sensor board's UART is
 * `/dev/ttyO0` (`crw-rw---- system:system`) — not `/dev/ttyS1` or `/dev/ttyS2`,
 * which is the Bluetooth HCI port — and an ordinary app uid cannot open it. A
 * stock bike has no `su`, so there is no way to widen those permissions.
 *
 * Peloton's own `com.peloton.service.SensorData` already owns the port and
 * hands out decoded values over a [Messenger]. It declares
 * `onepeloton.permission.ACCESS_SENSOR_SERVICE` at `signature` level, but the
 * `<service>` tag carries **no `android:permission` attribute**, so the
 * permission is never enforced and any app may bind. That is the whole reason
 * third-party bike apps work on an unmodified tablet.
 *
 * The protocol below was established by decompiling that service. Nothing here
 * is copied from another client — this project is Apache-2.0 and the obvious
 * reference implementation is GPL-3.0.
 */
class PelotonSensorServiceSource(
    private val context: Context
) : SensorSource {

    override val id: String = "peloton-service"
    override val displayName: String = "Peloton sensor board"

    override fun isAvailable(): Boolean = runCatching {
        context.packageManager.resolveService(bindIntent(), 0) != null
    }.getOrDefault(false)

    override fun readings(): Flow<SensorReading> = callbackFlow {
        if (!isAvailable()) {
            throw IOException("Peloton sensor service is not installed on this device")
        }

        // Replies arrive on this thread rather than the main looper: telemetry
        // must keep flowing while the UI thread is busy compositing the HUD.
        val replyThread = HandlerThread("peloton-sensor").apply { start() }

        var powerWatts = 0.0
        var cadenceRpm = 0.0
        var resistance = 0.0
        var consecutiveTimeouts = 0

        val replyHandler = object : Handler(replyThread.looper) {
            override fun handleMessage(msg: Message) {
                val data = msg.data ?: return

                // The board answering with nothing is reported as a TIME_OUT
                // carrying a 0.0 payload. That zero is an absence of data, not
                // a measurement, and recording it would write a fake sample
                // into the rider's permanent record — the same mistake the
                // nullable heartRateBpm exists to prevent.
                if (data.getString(KEY_RESPONSE_HEX) == RESPONSE_TIMEOUT) {
                    if (++consecutiveTimeouts >= MAX_CONSECUTIVE_TIMEOUTS) {
                        close(IOException("Sensor board stopped responding"))
                    }
                    return
                }
                consecutiveTimeouts = 0

                val value = data.getFloat(KEY_DATA).toDouble()
                when (msg.what) {
                    EVENT_RPM -> cadenceRpm = value
                    EVENT_WATT -> powerWatts = value
                    EVENT_RESISTANCE -> resistance = value
                    else -> return
                }

                trySend(
                    SensorReading(
                        powerWatts = powerWatts,
                        cadenceRpm = cadenceRpm,
                        resistancePercent = resistance,
                        powerIsMeasured = true,
                        timestampMs = System.currentTimeMillis()
                    )
                )
            }
        }
        val replyMessenger = Messenger(replyHandler)

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val outgoing = Messenger(binder)
                runCatching {
                    // Each of these registers a *repeating* request, so the
                    // board is polled and events arrive until we unbind.
                    for (command in REGISTER_COMMANDS) {
                        outgoing.send(
                            Message.obtain(null, command).apply { replyTo = replyMessenger }
                        )
                    }
                }.onFailure { close(IOException("Could not register for sensor events", it)) }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                close(IOException("Peloton sensor service disconnected"))
            }
        }

        val bound = runCatching {
            context.bindService(bindIntent(), connection, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)

        if (!bound) {
            replyThread.quitSafely()
            throw IOException("Could not bind the Peloton sensor service")
        }

        awaitClose {
            runCatching { context.unbindService(connection) }
            replyThread.quitSafely()
            Log.d(TAG, "Peloton sensor service source closed")
        }
    }

    private fun bindIntent() = Intent(SERVICE_ACTION).apply {
        setPackage(SERVICE_PACKAGE)
        addCategory(BIKE_CATEGORY)
    }

    companion object {
        private const val TAG = "PelotonSensorSource"

        const val SERVICE_ACTION = "android.intent.action.peloton.SensorData"
        const val SERVICE_PACKAGE = "com.peloton.service.SensorData"
        const val BIKE_CATEGORY = "com.peloton.sensor.category.BIKE"

        // Command ids the service's own client library uses. Registering is a
        // one-shot; the matching EVENT_* arrives repeatedly thereafter.
        private const val REGISTER_RPM = 1
        private const val REGISTER_WATT = 2
        private const val REGISTER_RESISTANCE = 3
        private val REGISTER_COMMANDS = intArrayOf(REGISTER_RPM, REGISTER_WATT, REGISTER_RESISTANCE)

        private const val EVENT_RPM = 7
        private const val EVENT_WATT = 8
        private const val EVENT_RESISTANCE = 9

        private const val KEY_DATA = "data"
        private const val KEY_RESPONSE_HEX = "responseHexString"
        private const val RESPONSE_TIMEOUT = "TIME_OUT"

        /** Roughly a second of silence before we let the repository back off. */
        private const val MAX_CONSECUTIVE_TIMEOUTS = 5
    }
}
