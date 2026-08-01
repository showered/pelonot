package com.pelonot.data.sensor

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.retryWhen
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
    val sensorReading: StateFlow<SensorReading> = _sensorReading.asStateFlow()

    private val _status = MutableStateFlow<SensorStatus>(SensorStatus.Stopped)
    val status: StateFlow<SensorStatus> = _status.asStateFlow()

    val heartRateStatus: StateFlow<HeartRateStatus> get() = bleHeartRateManager.status
    val discoveredHeartRateDevices: StateFlow<List<HeartRateDevice>>
        get() = bleHeartRateManager.discoveredDevices

    private var telemetryJob: Job? = null
    private var heartRateJob: Job? = null

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
        Log.i(TAG, "Starting telemetry from ${source.id} (mode=$mode)")

        telemetryJob = scope.launch {
            source.readings()
                // 2.7.4. A source that stops delivering never errors, so
                // without this the retry policy below it never runs. On the
                // bike that meant the board went quiet 86 seconds in and
                // nothing rebound it for the rest of the ride.
                .failOnSilence(SILENCE_TIMEOUT_MS)
                .retryWhen { cause, attempt ->
                    // Deliberately does not fall back to simulated data on a
                    // hardware failure: silently substituting fake telemetry
                    // mid-ride would write fabricated numbers into the rider's
                    // permanent workout record.
                    val attemptNumber = (attempt + 1).toInt()
                    val delayMs = backoffDelayMs(attempt)
                    _status.value = SensorStatus.Reconnecting(
                        sourceId = source.id,
                        attempt = attemptNumber,
                        cause = cause.message ?: cause::class.java.simpleName
                    )
                    Log.w(TAG, "Telemetry failed (attempt $attemptNumber), retrying in ${delayMs}ms", cause)
                    delay(delayMs)
                    true // retry indefinitely; the rider may reconnect the board
                }
                .collect { reading ->
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

    /** Exponential backoff, capped, so a missing board does not spin the CPU. */
    private fun backoffDelayMs(attempt: Long): Long {
        val exponent = attempt.coerceAtMost(MAX_BACKOFF_SHIFT).toInt()
        return (BASE_RETRY_DELAY_MS shl exponent).coerceAtMost(MAX_RETRY_DELAY_MS)
    }

    private companion object {
        const val TAG = "SensorRepository"
        const val BASE_RETRY_DELAY_MS = 1_000L
        const val MAX_RETRY_DELAY_MS = 30_000L
        const val MAX_BACKOFF_SHIFT = 5L

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
