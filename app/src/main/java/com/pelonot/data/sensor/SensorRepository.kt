package com.pelonot.data.sensor

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which telemetry source to ride with. */
enum class SensorMode {
    /** Use the sensor board when present, otherwise simulate. */
    Auto,

    /** Require the real sensor board; retry it rather than falling back. */
    Hardware,

    /** Always simulate, even on a real bike. For development. */
    Simulated
}

/** What the telemetry pipeline is currently doing. */
sealed interface SensorStatus {
    data object Stopped : SensorStatus
    data class Streaming(val sourceId: String, val simulated: Boolean) : SensorStatus
    data class Reconnecting(val sourceId: String, val attempt: Int, val cause: String) : SensorStatus

    /**
     * The pipeline has stopped trying (2.7.7).
     *
     * Distinct from [Stopped], which is nobody asking for telemetry, and from
     * [Reconnecting], which is a promise. This one is the app admitting that
     * rebinding is not going to help — and it exists because the alternative
     * was a retry counter climbing past 141 behind a screen saying
     * "reconnecting to the bike".
     */
    data class Unavailable(
        val sourceId: String,
        val reason: SensorUnavailableReason,
        val attempts: Int
    ) : SensorStatus
}

/**
 * Single source of truth for live telemetry, merging a bike [SensorSource]
 * with the BLE heart-rate strap.
 *
 * This owns **all** retry policy. Previously reconnection was implemented
 * three times over — in `SerialPortReader`, in `BleHeartRateManager`, and
 * again here — with `close()` triggering a reconnect, so the schedules fought
 * each other and stopping a workout started an endless retry loop.
 */
class SensorRepository(
    private val hardwareSource: SensorSource,
    private val simulatedSource: SensorSource,
    private val bleHeartRateManager: BleHeartRateManager,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

    private val _sensorReading = MutableStateFlow(SensorReading.EMPTY)

    /**
     * Every reading, at whatever rate the board produces them.
     *
     * What the **recorder** reads. Anything writing a number into a rider's
     * record wants this one and reads `.value` at its own tick.
     */
    val sensorReading: StateFlow<SensorReading> = _sensorReading.asStateFlow()

    /**
     * The same readings, paced to something a rider can read (11.6.7).
     *
     * What every **screen** reads — the ride screen and the overlay both, from
     * here, so the two surfaces cannot drift into different answers or solve
     * this twice. Nothing recorded goes through it.
     */
    val displayReading: StateFlow<SensorReading> = _sensorReading
        .atDisplayRate()
        .stateIn(scope, SharingStarted.Eagerly, SensorReading.EMPTY)

    private val _status = MutableStateFlow<SensorStatus>(SensorStatus.Stopped)
    val status: StateFlow<SensorStatus> = _status.asStateFlow()

    val heartRateStatus: StateFlow<HeartRateStatus> get() = bleHeartRateManager.status
    val discoveredHeartRateDevices: StateFlow<List<HeartRateDevice>>
        get() = bleHeartRateManager.discoveredDevices

    private var telemetryJob: Job? = null
    private var heartRateJob: Job? = null

    /**
     * The one retry schedule, and the decision to stop using it (2.7.7, 2.7.8).
     *
     * Held here rather than per-collection because the policy is the
     * repository's promise — a second one anywhere is the defect this class's
     * own header describes.
     */
    private val reconnects = ReconnectPolicy()

    /**
     * How many readings the plausibility fence has turned away since the last
     * [start] (2.7.3).
     *
     * Kept because the number is the evidence: on the corrupted ride it would
     * have been 41 of 53, and a fence nobody can count is a fence nobody can
     * tell is working.
     */
    @Volatile
    var rejectedReadings: Int = 0
        private set

    @Volatile
    private var mode: SensorMode = SensorMode.Auto

    /**
     * Picks the source for a ride. Changing this mid-ride restarts the
     * pipeline, so it is a settings-time decision rather than a live toggle.
     */
    fun setMode(newMode: SensorMode) {
        if (mode == newMode) return
        mode = newMode
        if (telemetryJob?.isActive == true) {
            stop()
            start()
        }
    }

    fun currentMode(): SensorMode = mode

    /** The source [mode] resolves to right now. */
    fun activeSource(): SensorSource = when (mode) {
        SensorMode.Simulated -> simulatedSource
        SensorMode.Hardware -> hardwareSource
        SensorMode.Auto -> if (hardwareSource.isAvailable()) hardwareSource else simulatedSource
    }

    fun start() {
        if (telemetryJob?.isActive == true) return

        val source = activeSource()
        val simulated = source === simulatedSource
        rejectedReadings = 0
        reconnects.reset()

        // 2.7.7. Whether *this* bind ever delivered anything is the one thing
        // that separates a board which dropped out from a port that was never
        // opened, and the two want opposite responses. A plain local: the
        // collector and the retry policy are the same coroutine, and its
        // suspensions are what publish the write.
        var producedReadings = false

        Log.i(TAG, "Starting telemetry from ${source.id} (mode=$mode)")

        telemetryJob = scope.launch {
            source.readings()
                // 2.7.4. A source that stops delivering never errors, so
                // without this the retry policy below it never runs. On the
                // bike that meant the board went quiet 86 seconds in and
                // nothing rebound it for the rest of the ride.
                .failOnSilence(SILENCE_TIMEOUT_MS)
                .retryWhen { cause, _ ->
                    // Deliberately does not fall back to simulated data on a
                    // hardware failure: silently substituting fake telemetry
                    // mid-ride would write fabricated numbers into the rider's
                    // permanent workout record.
                    val decision = reconnects.onFailure(cause, producedReadings)
                    producedReadings = false

                    when (decision) {
                        is Reconnect.GiveUp -> {
                            _status.value = SensorStatus.Unavailable(
                                sourceId = source.id,
                                reason = decision.reason,
                                attempts = decision.attempts
                            )
                            // Warn, not info: on this tablet `log.tag` is `W`
                            // device-wide and anything below it is discarded,
                            // and this is the line that explains a ride with no
                            // numbers in it.
                            Log.w(
                                TAG,
                                "Giving up on ${source.id} after ${decision.attempts} " +
                                    "attempts (${decision.reason}) — every rebind reopens " +
                                    "the board's serial port and none of them delivered",
                                cause
                            )
                            false
                        }

                        is Reconnect.After -> {
                            _status.value = SensorStatus.Reconnecting(
                                sourceId = source.id,
                                attempt = decision.attempt,
                                cause = cause.message ?: cause::class.java.simpleName
                            )
                            Log.w(
                                TAG,
                                "Telemetry failed (attempt ${decision.attempt}), " +
                                    "retrying in ${decision.delayMs}ms",
                                cause
                            )
                            delay(decision.delayMs)
                            true
                        }
                    }
                }
                // Not decoration. `retryWhen` returning false **rethrows**, and
                // an uncaught throw inside `scope.launch` takes the process
                // down — a `SupervisorJob` isolates siblings, it does not
                // handle. Measured the first time giving up actually happened:
                // `FATAL EXCEPTION: DefaultDispatcher-worker-4`, the whole app
                // gone, and the ride the rider was on left for the crash
                // recovery prompt to find. The status the retry policy set is
                // the report; the exception has nowhere left to go.
                .catch { cause -> Log.w(TAG, "Telemetry pipeline ended", cause) }
                .collect { reading ->
                    producedReadings = true
                    _status.value = SensorStatus.Streaming(source.id, simulated)

                    // 2.7.3. A value that cannot be true is not published at
                    // all — not clamped into range, which would put a
                    // plausible lie where a gap belongs. Holding the previous
                    // reading rather than replacing it means the flow ages
                    // out on its own timestamp and the recorder writes the
                    // gap that actually happened.
                    val impossible = reading.implausibleValues()
                    if (impossible.isNotEmpty()) {
                        rejectedReadings++
                        Log.w(TAG, "Rejected impossible reading: ${impossible.joinToString()}")
                        return@collect
                    }

                    _sensorReading.value = reading.copy(
                        // A live strap always wins over a simulated value.
                        heartRateBpm = bleHeartRateManager.heartRate.value ?: reading.heartRateBpm
                    )
                }
        }

        heartRateJob = scope.launch {
            bleHeartRateManager.heartRate.collect { bpm ->
                if (bpm != null) {
                    _sensorReading.update { it.copy(heartRateBpm = bpm) }
                }
            }
        }

        bleHeartRateManager.startScan()
    }

    fun stop() {
        Log.i(TAG, "Stopping telemetry")
        telemetryJob?.cancel()
        telemetryJob = null
        heartRateJob?.cancel()
        heartRateJob = null
        bleHeartRateManager.stopScan()
        bleHeartRateManager.disconnect()
        _sensorReading.value = SensorReading.EMPTY
        _status.value = SensorStatus.Stopped
    }

    /** Targets a specific strap by address, or null for the first found. */
    fun selectHeartRateDevice(address: String?) =
        bleHeartRateManager.selectDevice(address)

    fun scanForHeartRateDevices() = bleHeartRateManager.startScan()

    fun stopHeartRateScan() = bleHeartRateManager.stopScan()

    fun heartRatePermissions(): List<String> = bleHeartRateManager.requiredPermissions

    fun destroy() {
        stop()
        bleHeartRateManager.destroy()
    }

    private companion object {
        const val TAG = "SensorRepository"

        /**
         * How long a source may deliver nothing before it is torn down and
         * rebuilt (2.7.4).
         *
         * Comfortably longer than [SensorReading.MAX_AGE_MS], so the rider is
         * told the telemetry is stale — and the recorder starts leaving a gap
         * — a couple of seconds before the source is thrown away and rebound.
         * Short enough that a dropout costs one interval cue, not the rest of
         * the ride.
         */
        const val SILENCE_TIMEOUT_MS = 6_000L
    }
}
