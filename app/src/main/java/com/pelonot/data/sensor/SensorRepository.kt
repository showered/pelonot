package com.pelonot.data.sensor

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Singleton repository that merges serial port sensor data and BLE heart rate
 * into a single unified StateFlow<SensorReading>.
 *
 * This is the single source of truth for real-time telemetry during a workout.
 * The WorkoutService collects from this flow to record metrics every second.
 *
 * Auto-reconnect logic:
 * - Serial port: retries every 5 seconds with exponential backoff (max 30s)
 * - BLE: retries every 2 seconds with exponential backoff (max 30s)
 */
class SensorRepository private constructor(
    private val context: Context
) {

    companion object {
        private const val TAG = "SensorRepository"
        private const val SERIAL_RECONNECT_DELAY_MS = 5000L
        private const val MAX_SERIAL_RECONNECT_DELAY_MS = 30000L

        @Volatile
        private var INSTANCE: SensorRepository? = null

        fun getInstance(context: Context): SensorRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SensorRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // ── Public StateFlow ────────────────────────────────────────────
    private val _sensorReading = MutableStateFlow(
        SensorReading(
            cadenceRpm = 0.0,
            resistancePercent = 0.0,
            powerWatts = 0.0,
            heartRateBpm = null
        )
    )
    val sensorReading: StateFlow<SensorReading> = _sensorReading.asStateFlow()

    // ── Internal components ─────────────────────────────────────────
    private val serialPortReader = SerialPortReader()
    private val bleHeartRateManager = BleHeartRateManager(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Reconnect state
    private var serialReconnectAttempt = 0
    private var serialReconnectScheduled = false

    init {
        // Collect serial port readings
        scope.launch {
            serialPortReader.reading.collect { reading ->
                // Merge with current heart rate
                val currentHr = _sensorReading.value.heartRateBpm
                _sensorReading.value = reading.copy(heartRateBpm = currentHr)
            }
        }

        // Collect BLE heart rate
        scope.launch {
            bleHeartRateManager.heartRate.collect { hr ->
                _sensorReading.update { current ->
                    current.copy(heartRateBpm = hr)
                }
            }
        }
    }

    // ── Public API ──────────────────────────────────────────────────

    /**
     * Start all sensor data collection.
     * Opens the serial port and starts BLE scanning.
     */
    fun start() {
        Log.d(TAG, "Starting sensor repository")

        // Open serial port
        if (!serialPortReader.open()) {
            Log.w(TAG, "Serial port not available, scheduling reconnect")
            scheduleSerialReconnect()
        }

        // Start BLE scanning for heart rate monitors
        bleHeartRateManager.startScan()
    }

    /**
     * Stop all sensor data collection.
     */
    fun stop() {
        Log.d(TAG, "Stopping sensor repository")
        serialPortReader.close()
        bleHeartRateManager.stopScan()
        bleHeartRateManager.disconnect()
    }

    /**
     * Manually connect to a specific BLE heart rate device.
     */
    fun connectHeartRate(deviceAddress: String?) {
        // BLE connection is managed internally; this is a placeholder for future device selection
    }

    /**
     * Disconnect from the current BLE heart rate device.
     */
    fun disconnectHeartRate() {
        bleHeartRateManager.disconnect()
    }

    /**
     * Get the BLE heart rate manager for UI integration (device list, etc.).
     */
    fun getBleHeartRateManager(): BleHeartRateManager = bleHeartRateManager

    // ── Serial port reconnect logic ─────────────────────────────────

    private fun scheduleSerialReconnect() {
        if (serialReconnectScheduled) return

        serialReconnectScheduled = true
        serialReconnectAttempt++

        val delayMs = (SERIAL_RECONNECT_DELAY_MS * (1L shl (serialReconnectAttempt - 1)))
            .coerceAtMost(MAX_SERIAL_RECONNECT_DELAY_MS)

        Log.d(TAG, "Scheduling serial reconnect attempt $serialReconnectAttempt in ${delayMs}ms")

        scope.launch {
            kotlinx.coroutines.delay(delayMs)
            serialReconnectScheduled = false

            if (serialPortReader.reconnect()) {
                Log.d(TAG, "Serial port reconnected successfully")
                serialReconnectAttempt = 0
            } else {
                Log.w(TAG, "Serial reconnect failed, will retry")
                scheduleSerialReconnect()
            }
        }
    }

    /**
     * Clean up all resources.
     */
    fun destroy() {
        stop()
        bleHeartRateManager.destroy()
    }
}
