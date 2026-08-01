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
import java.util.concurrent.atomic.AtomicInteger

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

        // 2.7.1. The three streams arrive on separate messages and are only
        // one observation when they are close enough together in time to be
        // one. Nothing here keeps a `var` per metric any more; the assembler
        // holds each field with the instant it arrived and refuses to hand
        // back a triple that mixes moments or is missing a leg.
        val assembler = TelemetryAssembler()
        var consecutiveTimeouts = 0

        // 2.7.2. The corrupted ride carried a value near 602 that is not
        // cadence, resistance or power. If it arrives on a message type this
        // source does not handle, this is what will say so.
        val unknownEvents = HashMap<Int, Int>()

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

                val field = fieldFor(msg.what) ?: run {
                    recordUnknownEvent(unknownEvents, msg)
                    return
                }

                val value = data.getFloat(KEY_DATA).toDouble()
                when (val intake = assembler.onValue(field, value, System.currentTimeMillis())) {
                    is Intake.Emit -> trySend(intake.reading)
                    is Intake.Held -> Unit
                    is Intake.Rejected ->
                        // Logged rather than swallowed: this is the shape the
                        // corruption took on the bike, and if it happens again
                        // the log says which stream carried it.
                        Log.w(TAG, "Rejected impossible ${intake.value} (event ${msg.what})")
                }
            }
        }
        val replyMessenger = Messenger(replyHandler)

        val connection = object : ServiceConnection {
            /**
             * Guards against registering twice on one binding.
             *
             * `onServiceConnected` runs again if the service dies and the
             * system rebinds us to its replacement, and every extra
             * registration is another repeating poll answering into the same
             * reply Messenger — the leading suspect for 2.7.1's rotation.
             */
            private var registered = false

            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (registered) {
                    Log.w(TAG, "Sensor service reconnected while already registered; not registering again")
                    return
                }
                val outgoing = Messenger(binder)
                runCatching {
                    // Each of these registers a *repeating* request, so the
                    // board is polled and events arrive until we unbind.
                    for (command in REGISTER_COMMANDS) {
                        outgoing.send(
                            Message.obtain(null, command).apply { replyTo = replyMessenger }
                        )
                    }
                }.onSuccess {
                    registered = true
                    val live = liveRegistrations.incrementAndGet()
                    // The count is the whole diagnostic for 2.7.1: one live
                    // registration cannot rotate values between fields, and
                    // more than one is expected to.
                    if (live > 1) {
                        Log.e(TAG, "$live live sensor registrations — telemetry will interleave")
                    } else {
                        Log.i(TAG, "Registered for sensor events (1 live registration)")
                    }
                }.onFailure { close(IOException("Could not register for sensor events", it)) }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                close(IOException("Peloton sensor service disconnected"))
            }

            /** Called once, from [awaitClose], so the count cannot drift. */
            fun released() {
                if (registered) {
                    registered = false
                    Log.i(TAG, "Released registration (${liveRegistrations.decrementAndGet()} live)")
                }
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
            connection.released()
            runCatching { context.unbindService(connection) }
            replyThread.quitSafely()
            if (unknownEvents.isNotEmpty()) {
                Log.w(TAG, "Unhandled sensor events this session: $unknownEvents")
            }
            Log.d(
                TAG,
                "Peloton sensor service source closed " +
                    "(${assembler.rejectedCount} impossible values rejected)"
            )
        }
    }

    private fun fieldFor(what: Int): TelemetryField? = when (what) {
        EVENT_RPM -> TelemetryField.Cadence
        EVENT_WATT -> TelemetryField.Power
        EVENT_RESISTANCE -> TelemetryField.Resistance
        else -> null
    }

    /**
     * Logs the first few of each unhandled message type with its payload, then
     * only counts them (2.7.2).
     *
     * Unbounded logging on a message arriving several times a second would
     * push the evidence out of logcat's buffer before anyone read it.
     */
    // Bundle.get() is deprecated in favour of the typed getters, and typed is
    // exactly what this cannot be: the point is to find out what an unhandled
    // event carries, which means not assuming it is a float.
    @Suppress("DEPRECATION")
    private fun recordUnknownEvent(counts: HashMap<Int, Int>, msg: Message) {
        val seen = (counts[msg.what] ?: 0) + 1
        counts[msg.what] = seen
        if (seen <= UNKNOWN_EVENT_LOG_LIMIT) {
            val data = msg.data
            val payload = data?.keySet()?.joinToString { key -> "$key=${data.get(key)}" }
            Log.w(TAG, "Unhandled sensor event what=${msg.what} arg1=${msg.arg1} [$payload]")
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

        /** How many of each unhandled event type get their payload logged. */
        private const val UNKNOWN_EVENT_LOG_LIMIT = 3

        /**
         * Live registrations across the whole process (2.7.1).
         *
         * There should never be more than one. It is a companion counter
         * rather than an instance field precisely because the failure being
         * hunted is a *second* source outliving the first.
         */
        private val liveRegistrations = AtomicInteger(0)
    }
}
