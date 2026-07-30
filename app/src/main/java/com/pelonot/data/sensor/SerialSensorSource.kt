package com.pelonot.data.sensor

import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * Reads telemetry from the Peloton sensor board over the tablet's UART.
 *
 * On a jailbroken Gen 1/Gen 2 the board is exposed as a character device. The
 * path differs between hardware revisions, hence [devicePath] being injectable
 * rather than a hardcoded constant — the previous code documented `/dev/ttyS1`
 * and then opened `/dev/ttyS2`.
 *
 * Note this reads the device as a plain file. Line discipline and baud rate
 * (19200 8N1) are whatever the kernel already has configured; setting them
 * would need `termios` via JNI or an external `stty`. In practice the board's
 * port is already configured at boot.
 */
class SerialSensorSource(
    private val devicePath: String = DEFAULT_DEVICE_PATH
) : SensorSource {

    override val id: String = "serial"
    override val displayName: String = "Peloton sensor board"

    override fun isAvailable(): Boolean = runCatching {
        val file = File(devicePath)
        file.exists() && file.canRead()
    }.getOrDefault(false)

    override fun readings(): Flow<SensorReading> = callbackFlow {
        val file = File(devicePath)
        if (!file.exists() || !file.canRead()) {
            throw IOException("Serial device $devicePath is not accessible")
        }

        val parser = SerialProtocolParser()
        val cadenceTracker = CadenceTracker()
        var resistancePercent = 0.0
        var cadenceRpm = 0.0

        fun emitReading() {
            val power = PowerModel.estimateWatts(cadenceRpm, resistancePercent)
            trySend(
                SensorReading(
                    powerWatts = power,
                    cadenceRpm = cadenceRpm,
                    resistancePercent = resistancePercent,
                    timestampMs = System.currentTimeMillis()
                )
            )
        }

        // A rider who stops pedalling produces no further ticks, so cadence has
        // to be re-evaluated on a timer to decay to zero rather than freezing
        // at its last value.
        val decayJob = launch {
            while (isActive) {
                kotlinx.coroutines.delay(DECAY_TICK_MS)
                val decayed = cadenceTracker.cadenceAt(System.currentTimeMillis())
                if (decayed != cadenceRpm) {
                    cadenceRpm = decayed
                    emitReading()
                }
            }
        }

        val stream = FileInputStream(file)
        val readJob = launch {
            val buffer = ByteArray(READ_BUFFER_BYTES)
            try {
                while (isActive) {
                    val bytesRead = stream.read(buffer)
                    if (bytesRead < 0) throw IOException("EOF on $devicePath")
                    if (bytesRead == 0) continue

                    var changed = false
                    for (event in parser.parse(buffer, bytesRead)) {
                        when (event) {
                            SerialEvent.CadenceTick -> {
                                cadenceRpm = cadenceTracker.onTick(System.currentTimeMillis())
                                changed = true
                            }

                            is SerialEvent.ResistanceUpdate -> {
                                resistancePercent = event.percent
                                changed = true
                            }
                        }
                    }
                    if (changed) emitReading()
                }
            } catch (e: IOException) {
                // Surface to the collector so SensorRepository can back off and
                // retry, rather than completing the flow as if all were well.
                close(e)
            }
        }

        awaitClose {
            decayJob.cancel()
            readJob.cancel()
            runCatching { stream.close() }
            Log.d(TAG, "Serial source closed")
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        private const val TAG = "SerialSensorSource"

        /** Gen 1/Gen 2 sensor board UART. */
        const val DEFAULT_DEVICE_PATH = "/dev/ttyS2"

        private const val READ_BUFFER_BYTES = 64
        private const val DECAY_TICK_MS = 250L
    }
}
