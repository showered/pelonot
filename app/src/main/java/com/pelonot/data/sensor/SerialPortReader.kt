package com.pelonot.data.sensor

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow

/**
 * Reads real-time telemetry from the Peloton sensor board over serial.
 *
 * Device: /dev/ttyS1
 * Baud: 19200
 * Protocol: Custom binary protocol based on Grupetto reverse-engineering.
 */
class SerialPortReader {

    companion object {
        private const val TAG = "SerialPortReader"
        private const val DEVICE_PATH = "/dev/ttyS1"
        
        // Power curve constants (Grupetto model)
        // P = c1 * rpm^3 + c2 * rpm^2 + c3 * rpm + c4
        // where c_i are functions of resistance (R)
        private const val P1 = 0.000185
        private const val P2 = -0.0125
        private const val P3 = 0.85
        private const val P4 = -15.0
    }

    private val _reading = MutableSharedFlow<SensorReading>(extraBufferCapacity = 1)
    val reading: SharedFlow<SensorReading> = _reading.asSharedFlow()

    private var inputStream: FileInputStream? = null
    private var readJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var lastCadenceTickMs: Long = 0
    private var currentResistance: Double = 0.0
    private var currentCadence: Double = 0.0
    private var reconnectJob: Job? = null
    private var reconnectAttempts: Long = 0
    private val maxReconnectAttempts = 10L
    private val baseDelayMs = 1000L
    
    /**
     * Open the serial port for reading.
     * @return true if successful, false otherwise.
     */
    fun open(): Boolean {
        return try {
            val file = File(DEVICE_PATH)
            if (!file.exists() || !file.canRead()) {
                Log.e(TAG, "Serial device $DEVICE_PATH not accessible")
                return false
            }
            inputStream = FileInputStream(file)
            startReading()
            reconnectAttempts = 0
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open serial port: ${e.message}")
            false
        }
    }

    /**
     * Start the reading loop.
     */
    private fun startReading() {
        readJob?.cancel()
        readJob = scope.launch {
            val buffer = ByteArray(64)
            while (isActive) {
                try {
                    val bytesRead = inputStream?.read(buffer) ?: -1
                    if (bytesRead > 0) {
                        processRawData(buffer, bytesRead)
                    } else if (bytesRead == -1) {
                        Log.w(TAG, "EOF reached on serial port")
                        break
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Read error: ${e.message}")
                    break
                }
                delay(10) // Small delay to prevent tight loop if no data
            }
        }
    }

    /**
     * Process raw bytes from the sensor board.
     * The Peloton protocol sends single-byte commands:
     * - 'C': Cadence tick (one flywheel revolution)
     * - 'R' followed by value: Resistance change
     */
    private fun processRawData(data: ByteArray, length: Int) {
        var i = 0
        while (i < length) {
            when (data[i].toInt().toChar()) {
                'C' -> {
                    handleCadenceTick()
                    i++
                }
                'R' -> {
                    if (i + 1 < length) {
                        val rawResistance = data[i + 1].toInt() and 0xFF
                        handleResistanceUpdate(rawResistance)
                        i += 2
                    } else {
                        // Resistance value might be in next read, but protocol 
                        // usually keeps them together.
                        i++ 
                    }
                }
                else -> i++
            }
        }
    }

    private fun handleCadenceTick() {
        val now = System.currentTimeMillis()
        if (lastCadenceTickMs > 0) {
            val deltaMs = now - lastCadenceTickMs
            if (deltaMs > 0) {
                // RPM = (1 tick / deltaMs) * (60,000 ms / 1 min)
                currentCadence = 60000.0 / deltaMs
            }
        }
        lastCadenceTickMs = now
        emitReading()
    }

    private fun handleResistanceUpdate(raw: Int) {
        // Map 0-255 or 0-100 raw to 0-100% resistance
        // Gen 1/2 Peloton usually reports 0-100 directly
        currentResistance = raw.toDouble().coerceIn(0.0, 100.0)
        emitReading()
    }

    private fun emitReading() {
        val power = calculatePower(currentCadence, currentResistance)
        scope.launch {
            _reading.emit(
                SensorReading(
                    cadenceRpm = currentCadence,
                    resistancePercent = currentResistance,
                    powerWatts = power,
                    timestampMs = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * Calculate power based on cadence and resistance using the Peloton power curve.
     * P = (c1 * R + c2) * cadence^2 + (c3 * R + c4) * cadence
     * Simplified Grupetto model for now.
     */
    private fun calculatePower(rpm: Double, resistance: Double): Double {
        if (rpm < 10.0) return 0.0
        
        // This is a placeholder for the actual non-linear model
        // In a real implementation, we'd use a lookup table or the full polynomial
        val basePower = P1 * rpm.pow(3) + P2 * rpm.pow(2) + P3 * rpm + P4
        val resistanceFactor = 1.0 + (resistance / 50.0)
        
        return (basePower * resistanceFactor).coerceAtLeast(0.0)
    }

    fun close() {
        readJob?.cancel()
        try {
            inputStream?.close()
        } catch (e: IOException) {
            // Ignore
        }
        inputStream = null
        startAutoReconnect()
    }

    fun reconnect(): Boolean {
        close()
        return open()
    }
    
    /**
     * Start auto-reconnect with exponential backoff.
     */
    private fun startAutoReconnect() {
        if (reconnectJob?.isActive == true) return
        
        reconnectJob = scope.launch {
            while (isActive && reconnectAttempts < maxReconnectAttempts) {
                reconnectAttempts++
                val delayMs = baseDelayMs * (1L shl (reconnectAttempts - 1).coerceAtMost(5))
                Log.i(TAG, "Attempting serial port reconnect (attempt $reconnectAttempts, delay ${delayMs}ms)")
                
                delay(delayMs)
                
                if (reconnect()) {
                    Log.i(TAG, "Serial port reconnected successfully")
                    return@launch
                }
            }
            Log.e(TAG, "Failed to reconnect after $maxReconnectAttempts attempts")
        }
    }
    
    /**
     * Stop any ongoing reconnect attempts.
     */
    fun stopReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempts = 0
    }
}
