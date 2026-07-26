package com.pelonot.data.sensor

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Reads raw sensor ticks from the Peloton sensor board via serial port.
 *
 * Based on Grupetto's serial port logic. The sensor board sends:
 * - Cadence ticks ('C') — one per flywheel revolution
 * - Resistance ticks ('R') — sent when resistance changes, with raw value
 *
 * The serial device is typically /dev/ttyS1 or /dev/ttyUSB0 on the Peloton tablet.
 *
 * This class:
 * 1. Opens the serial port at 115200 baud
 * 2. Continuously reads bytes, parsing tick events
 * 3. Exposes a StateFlow<SensorReading> with computed cadence, resistance, and power
 */
class SerialPortReader {

    companion object {
        private const val TAG = "SerialPortReader"
        private const val BAUD_RATE = 115200
        private const val FLYWHEEL_MAGNETS = 2 // Peloton flywheel has 2 magnets
        private const val GEAR_RATIO = 2.0 // Internal gear ratio
        private const val WHEEL_CIRCUMFERENCE_M = 2.10 // Approximate wheel circumference in meters

        // Grupetto power curve constants
        // Power = (resistance_percent / 100) * (cadence_rpm / 60) * TORQUE_CONSTANT
        private const val TORQUE_CONSTANT = 0.42 // Empirical torque constant from Grupetto

        // Candidate serial device paths on Peloton hardware
        private val DEVICE_PATHS = listOf(
            "/dev/ttyS1",
            "/dev/ttyUSB0",
            "/dev/ttyACM0",
            "/dev/serial0"
        )
    }

    // ── Public StateFlow ────────────────────────────────────────────
    private val _reading = MutableStateFlow(
        SensorReading(
            cadenceRpm = 0.0,
            resistancePercent = 0.0,
            powerWatts = 0.0,
            heartRateBpm = null
        )
    )
    val reading: StateFlow<SensorReading> = _reading.asStateFlow()

    // ── Internal state ──────────────────────────────────────────────
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serialIn: FileInputStream? = null
    private var serialOut: FileOutputStream? = null
    private var fileDescriptor: FileDescriptor? = null
    private var isReading = false

    // Cadence calculation state
    private var lastCadenceTickMs: Long = 0
    private var cadenceTickCount: Int = 0
    private var currentCadenceRpm: Double = 0.0

    // Resistance state
    private var currentResistancePercent: Double = 0.0

    // Heart rate (injected from BLE)
    private var currentHeartRate: Int? = null

    /**
     * Open the serial port. Tries each candidate device path.
     * Returns true if a port was successfully opened.
     */
    fun open(): Boolean {
        for (path in DEVICE_PATHS) {
            try {
                val fd = openNative(path, BAUD_RATE)
                if (fd != null) {
                    fileDescriptor = fd
                    serialIn = FileInputStream(fd)
                    serialOut = FileOutputStream(fd)
                    Log.d(TAG, "Serial port opened on $path at ${BAUD_RATE} baud")
                    startReading()
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to open $path: ${e.message}")
            }
        }
        Log.e(TAG, "Could not open any serial port. Tried: $DEVICE_PATHS")
        return false
    }

    /**
     * Start the background read loop.
     */
    private fun startReading() {
        if (isReading) return
        isReading = true

        scope.launch {
            val buffer = ByteArray(1024)
            val lineBuffer = StringBuilder()

            while (isReading) {
                try {
                    val readBytes = serialIn?.read(buffer) ?: -1
                    if (readBytes > 0) {
                        // Parse incoming bytes as ASCII text lines
                        for (i in 0 until readBytes) {
                            val byte = buffer[i].toInt().toChar()
                            if (byte == '\n' || byte == '\r') {
                                if (lineBuffer.isNotEmpty()) {
                                    parseLine(lineBuffer.toString())
                                    lineBuffer.clear()
                                }
                            } else {
                                lineBuffer.append(byte)
                            }
                        }
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Read error: ${e.message}")
                    break
                }
            }
        }
    }

    /**
     * Parse a single line from the serial port.
     * Expected formats:
     * - "C" — cadence tick
     * - "R:XX" — resistance tick with value XX (0-100)
     */
    private fun parseLine(line: String) {
        if (line.isEmpty()) return

        val tick = when {
            line == "C" -> {
                SensorTick(System.currentTimeMillis(), 'C', 0)
            }
            line.startsWith("R:") -> {
                val value = line.substring(2).toIntOrNull() ?: 0
                SensorTick(System.currentTimeMillis(), 'R', value)
            }
            else -> null
        }

        tick?.let { processTick(it) }
    }

    /**
     * Process a single sensor tick and update the StateFlow.
     */
    private fun processTick(tick: SensorTick) {
        when (tick.tickType) {
            'C' -> {
                // Cadence tick — compute RPM from time between ticks
                val now = tick.timestampMs
                if (lastCadenceTickMs > 0) {
                    val intervalSec = (now - lastCadenceTickMs) / 1000.0
                    if (intervalSec > 0) {
                        // RPM = (1 / interval) * 60 / magnets
                        currentCadenceRpm = (60.0 / intervalSec) / FLYWHEEL_MAGNETS * GEAR_RATIO
                    }
                }
                lastCadenceTickMs = now
                cadenceTickCount++
            }
            'R' -> {
                currentResistancePercent = tick.rawValue.toDouble().coerceIn(0.0, 100.0)
            }
        }

        // Compute power from resistance and cadence
        val powerWatts = computePower(currentResistancePercent, currentCadenceRpm)

        // Update the StateFlow
        _reading.update { current ->
            current.copy(
                cadenceRpm = currentCadenceRpm,
                resistancePercent = currentResistancePercent,
                powerWatts = powerWatts,
                heartRateBpm = currentHeartRate,
                timestampMs = System.currentTimeMillis()
            )
        }
    }

    /**
     * Compute instantaneous power from resistance and cadence.
     * Uses the Grupetto power curve model.
     *
     * P = (resistance / 100) * (cadence / 60) * TORQUE_CONSTANT * GEAR_RATIO
     */
    private fun computePower(resistancePercent: Double, cadenceRpm: Double): Double {
        if (cadenceRpm <= 0.0) return 0.0
        val torque = (resistancePercent / 100.0) * TORQUE_CONSTANT
        val angularVelocity = (cadenceRpm / 60.0) * GEAR_RATIO
        return torque * angularVelocity
    }

    /**
     * Inject heart rate from BLE manager.
     */
    fun setHeartRate(hr: Int?) {
        currentHeartRate = hr
        _reading.update { current ->
            current.copy(heartRateBpm = hr)
        }
    }

    /**
     * Stop reading and close the serial port.
     */
    fun close() {
        isReading = false
        try {
            serialIn?.close()
            serialOut?.close()
        } catch (e: IOException) {
            Log.w(TAG, "Error closing serial port: ${e.message}")
        }
        Log.d(TAG, "Serial port closed")
    }

    /**
     * Attempt to reconnect to the serial port.
     */
    fun reconnect(): Boolean {
        close()
        return open()
    }

    // ── Native methods ──────────────────────────────────────────────
    // These are implemented in C/C++ via JNI for low-level serial port access.
    // On the Peloton tablet, these provide access to /dev/ttyS1 etc.
    private external fun openNative(path: String, baudRate: Int): FileDescriptor?

    private external fun closeNative(fd: FileDescriptor?)
}
