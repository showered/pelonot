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
import android.os.SystemClock
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
            throw SensorServiceMissing("Peloton sensor service is not installed on this device")
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

        /** When the current unbroken run of `TIME_OUT` replies began (2.7.8). */
        var quietSince = 0L

        /** How often the service's own label disagreed with the frame (2.7.1b). */
        var mislabelled = 0

        // 2.7.2. The corrupted ride carried a value near 602 that is not
        // cadence, resistance or power. If it arrives on a message type this
        // source does not handle, this is what will say so.
        val unknownEvents = HashMap<Int, Int>()

        val replyHandler = object : Handler(replyThread.looper) {
            override fun handleMessage(msg: Message) {
                val data = msg.data ?: return

                // 2.7.1b. Every message exactly as it arrives, before anything
                // interprets it. This is the capture that decides which side of
                // the binder mislabels the stream: if the raw resistance value
                // turns up here already wearing `what=7`, nothing on this side
                // put it there.
                traceEvent(msg, data)

                // The board answering with nothing is reported as a TIME_OUT
                // carrying a 0.0 payload. That zero is an absence of data, not
                // a measurement, and recording it would write a fake sample
                // into the rider's permanent record — the same mistake the
                // nullable heartRateBpm exists to prevent.
                //
                // 2.7.8. Measured in *time*, not in messages. It used to be
                // five consecutive timeouts, which at three polls answering
                // several times a second is under a second of quiet — so the
                // source tore itself down and rebound long before the
                // repository's own six-second watchdog could run, and every one
                // of those rebinds reopened the serial port that 2.7d says
                // leaks. Two silence detectors, and the eager one always won.
                if (data.getString(KEY_RESPONSE_HEX) == RESPONSE_TIMEOUT) {
                    val now = SystemClock.elapsedRealtime()
                    if (quietSince == 0L) {
                        quietSince = now
                    } else if (now - quietSince >= BOARD_QUIET_TIMEOUT_MS) {
                        // The distinguishing failure (2.7.7): the service is
                        // alive and answering every poll, and what it is
                        // answering is "nothing". That is the board unreachable
                        // behind a service that bound fine, which is what a
                        // leaked `/dev/ttyO0` looks like from this side.
                        close(SensorBoardNotAnswering(now - quietSince))
                    }
                    return
                }
                quietSince = 0L

                // 2.7.1b, and the whole of the fix: **the frame is the truth,
                // `msg.what` is only a claim about it.** The service labels
                // replies by position in its own request cycle, so anything
                // that disturbs that cycle — a second app binding the sensor
                // service is enough — slides the labels along while the
                // payloads stay put. Measured on the bike: 55 of 204 messages
                // carried a payload disagreeing with their own label, and a
                // stationary rider was reported at 544 rpm.
                val frame = PelotonFrameParser.parse(data.getString(KEY_RESPONSE_HEX))
                if (frame == null) {
                    // No frame, or a frame that failed its checksum. Falling
                    // back to `msg.what` would be trusting the one thing this
                    // whole class exists because we cannot trust.
                    recordUnknownEvent(unknownEvents, msg)
                    return
                }

                val claimed = fieldFor(msg.what)
                if (claimed != frame.field) {
                    mislabelled++
                    if (mislabelled <= MISLABEL_LOG_LIMIT) {
                        Log.w(
                            TAG,
                            "Service labelled a 0x${frame.id.toString(16).uppercase()} frame " +
                                "as event ${msg.what} (${claimed?.label ?: "unknown"}); " +
                                "trusting the frame"
                        )
                    }
                }

                val field = frame.field ?: run {
                    // A real reading with no column of ours — the raw
                    // resistance report. Dropping it by identity rather than
                    // by plausibility is the difference between a fence and a
                    // fix.
                    if (!PelotonFrameParser.isKnownButUnused(frame.id)) {
                        recordUnknownEvent(unknownEvents, msg)
                    }
                    return
                }

                val value = frame.value
                when (val intake = assembler.onValue(field, value, System.currentTimeMillis())) {
                    is Intake.Emit -> trySend(intake.reading)
                    is Intake.Held, is Intake.Quarantined -> Unit
                    is Intake.Rejected ->
                        // Logged rather than swallowed: this is the shape the
                        // corruption took on the bike, and if it happens again
                        // the log says which stream carried it. The value is
                        // the evidence — on both recorded bursts it was the
                        // raw resistance reading, ~11.13 x resistance% + 229.
                        Log.w(
                            TAG,
                            "Desync: impossible ${intake.value} on event ${msg.what}; " +
                                "distrusting the stream for ${TelemetryAssembler.DEFAULT_RESYNC_QUIET_MS}ms"
                        )
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
                    for (command in registerCommands) {
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
                "Peloton sensor service source closed ($mislabelled mislabelled frames, " +
                    "${assembler.rejectedCount} impossible values rejected, " +
                    "${assembler.quarantinedCount} readings withheld as suspect)"
            )
        }
    }

    /**
     * Dumps one raw message to logcat while a trace window is open (2.7.1b).
     *
     * Off unless somebody asked for it: the board reports several times a
     * second and an always-on trace would push the evidence out of logcat's
     * buffer before anyone read it. `SystemClock.uptimeMillis` rather than wall
     * clock, because what matters is the *order and spacing* of arrivals.
     */
    @Suppress("DEPRECATION")
    private fun traceEvent(msg: Message, data: android.os.Bundle) {
        if (System.currentTimeMillis() >= traceUntilMs) return
        val payload = data.keySet().joinToString { key -> "$key=${data.get(key)}" }
        Log.i(
            TRACE_TAG,
            "${SystemClock.uptimeMillis()} what=${msg.what} arg1=${msg.arg1} " +
                "arg2=${msg.arg2} [$payload]"
        )
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

        /** Its own tag so the raw stream can be filtered out of everything else. */
        const val TRACE_TAG = "PelotonSensorTrace"

        /**
         * Wall-clock instant the raw event trace stops (2.7.1b).
         *
         * Opened from `adb` rather than left on, and read by the debug
         * receiver only — a release build has no way to reach it.
         */
        @Volatile
        private var traceUntilMs: Long = 0L

        /** Logs every raw sensor message for [seconds]. */
        fun traceFor(seconds: Int) {
            traceUntilMs = System.currentTimeMillis() + seconds * 1000L
        }


        const val SERVICE_ACTION = "android.intent.action.peloton.SensorData"
        const val SERVICE_PACKAGE = "com.peloton.service.SensorData"
        const val BIKE_CATEGORY = "com.peloton.sensor.category.BIKE"

        // Command ids the service's own client library uses. Registering is a
        // one-shot; the matching EVENT_* arrives repeatedly thereafter.
        private const val REGISTER_RPM = 1
        private const val REGISTER_WATT = 2
        private const val REGISTER_RESISTANCE = 3

        /**
         * Which repeating polls to register (2.7.1b).
         *
         * Normally all three. The A/B that identifies the mislabeller needs
         * one run with **resistance alone**: if the raw-resistance intruder
         * survives that, the board or the service emits it unsolicited; if it
         * disappears, three overlapping polls answering into one reply
         * Messenger are what desync the service, and the fix is one Messenger
         * per metric.
         *
         * A `var` only so the experiment can be run without a rebuild between
         * halves. Nothing in the app writes it; the debug receiver does.
         */
        @Volatile
        var registerCommands: IntArray =
            intArrayOf(REGISTER_RPM, REGISTER_WATT, REGISTER_RESISTANCE)
            private set

        /** Registers for resistance only on the next bind. Debug builds only. */
        fun registerResistanceOnly() {
            registerCommands = intArrayOf(REGISTER_RESISTANCE)
        }

        /** Back to all three. Debug builds only. */
        fun registerEverything() {
            registerCommands = intArrayOf(REGISTER_RPM, REGISTER_WATT, REGISTER_RESISTANCE)
        }

        private const val EVENT_RPM = 7
        private const val EVENT_WATT = 8
        private const val EVENT_RESISTANCE = 9

        private const val KEY_DATA = "data"
        private const val KEY_RESPONSE_HEX = "responseHexString"
        private const val RESPONSE_TIMEOUT = "TIME_OUT"

        /**
         * How long the board may answer nothing but `TIME_OUT` before the
         * source is torn down (2.7.8).
         *
         * Deliberately shorter than [SensorRepository]'s own silence watchdog,
         * because this failure is the *informative* one — a stream of timeouts
         * says the service is up and the board is not, and that is the only
         * evidence this app gets for the leaked-port condition. Four seconds
         * rather than the old sub-second count: a rebind is the one action that
         * can make a leaked port permanent, so it is worth being sure first.
         */
        private const val BOARD_QUIET_TIMEOUT_MS = 4_000L

        /** How many of each unhandled event type get their payload logged. */
        private const val UNKNOWN_EVENT_LOG_LIMIT = 3

        /** Mislabelling arrives in bursts; the first few are the evidence. */
        private const val MISLABEL_LOG_LIMIT = 5

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
