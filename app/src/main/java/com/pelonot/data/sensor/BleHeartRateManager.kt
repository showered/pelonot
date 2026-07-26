package com.pelonot.data.sensor

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Manages BLE Heart Rate Monitor connections.
 *
 * Scans for BLE devices advertising the Heart Rate Service (UUID 0x180D),
 * connects to them, and subscribes to the Heart Rate Measurement characteristic
 * (UUID 0x2A37) to receive real-time BPM updates.
 *
 * Exposes heart rate as a StateFlow<Int?> that SensorRepository merges
 * with serial port data.
 */
class BleHeartRateManager(private val context: Context) {

    companion object {
        private const val TAG = "BleHeartRateManager"

        // Heart Rate Service UUID (0x180D)
        private val HEART_RATE_SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")

        // Heart Rate Measurement Characteristic UUID (0x2A37)
        private val HEART_RATE_MEASUREMENT_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")

        // Client Characteristic Configuration Descriptor UUID (0x2902)
        private val CLIENT_CHAR_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        // Reconnect delay in milliseconds (exponential backoff)
        private const val INITIAL_RECONNECT_DELAY_MS = 2000L
        private const val MAX_RECONNECT_DELAY_MS = 30000L
    }

    // ── Public StateFlow ────────────────────────────────────────────
    private val _heartRate = MutableStateFlow<Int?>(null)
    val heartRate: StateFlow<Int?> = _heartRate.asStateFlow()

    // ── Internal state ──────────────────────────────────────────────
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var connectedDevice: BluetoothDevice? = null

    // Reconnect state
    private var reconnectAttempt = 0
    private var reconnectScheduled = false

    // ── Scanning ────────────────────────────────────────────────────

    /**
     * Start scanning for BLE heart rate devices.
     * Requires BLUETOOTH_SCAN permission on Android 12+.
     */
    @SuppressLint("MissingPermission")
    fun startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Log.w(TAG, "Bluetooth is not enabled")
            return
        }

        bluetoothLeScanner = bluetoothAdapter!!.bluetoothLeScanner ?: return

        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(HEART_RATE_SERVICE_UUID))
            .build()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bluetoothLeScanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
        Log.d(TAG, "BLE scan started for Heart Rate Service")
    }

    /**
     * Stop scanning for BLE devices.
     */
    @SuppressLint("MissingPermission")
    fun stopScan() {
        bluetoothLeScanner?.stopScan(scanCallback)
        Log.d(TAG, "BLE scan stopped")
    }

    // ── Connection ──────────────────────────────────────────────────

    /**
     * Connect to a specific BLE device.
     */
    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        stopScan()
        connectedDevice = device
        reconnectAttempt = 0

        Log.d(TAG, "Connecting to ${device.address}")
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    /**
     * Disconnect from the current BLE device.
     */
    fun disconnect() {
        reconnectScheduled = false
        reconnectAttempt = 0
        bluetoothGatt?.close()
        bluetoothGatt = null
        connectedDevice = null
        _heartRate.value = null
        Log.d(TAG, "BLE disconnected")
    }

    // ── Reconnect logic ─────────────────────────────────────────────

    /**
     * Attempt to reconnect with exponential backoff.
     */
    @SuppressLint("MissingPermission")
    private fun scheduleReconnect() {
        if (reconnectScheduled || connectedDevice == null) return

        reconnectScheduled = true
        reconnectAttempt++

        val delayMs = (INITIAL_RECONNECT_DELAY_MS * (1L shl (reconnectAttempt - 1)))
            .coerceAtMost(MAX_RECONNECT_DELAY_MS)

        Log.d(TAG, "Scheduling reconnect attempt $reconnectAttempt in ${delayMs}ms")

        // Use a simple thread for the delay (avoids Handler/Looper complexity)
        Thread {
            try {
                Thread.sleep(delayMs)
                reconnectScheduled = false
                Log.d(TAG, "Reconnecting...")
                bluetoothGatt = connectedDevice?.connectGatt(context, false, gattCallback)
            } catch (e: InterruptedException) {
                Log.w(TAG, "Reconnect thread interrupted")
            }
        }.start()
    }

    // ── GATT Callback ───────────────────────────────────────────────

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "GATT connected: ${gatt.device.address}")
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.w(TAG, "GATT disconnected from ${gatt.device.address}")
                    if (connectedDevice != null) {
                        scheduleReconnect()
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed with status $status")
                return
            }

            val hrChar = gatt.getService(HEART_RATE_SERVICE_UUID)
                ?.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)

            if (hrChar == null) {
                Log.e(TAG, "Heart Rate Measurement characteristic not found")
                return
            }

            // Enable notifications
            val enabled = gatt.setCharacteristicNotification(hrChar, true)
            if (!enabled) {
                Log.e(TAG, "Failed to enable notifications")
                return
            }

            // Write the CCC descriptor to actually enable notifications on the peripheral
            val descriptor = hrChar.getDescriptor(CLIENT_CHAR_CONFIG_UUID)
            if (descriptor != null) {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            }

            Log.d(TAG, "Heart Rate notifications enabled")
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == HEART_RATE_MEASUREMENT_UUID) {
                val hr = parseHeartRate(characteristic.value)
                if (hr != null) {
                    Log.d(TAG, "Heart rate: ${hr}bpm")
                    _heartRate.value = hr
                }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "CCC descriptor written successfully")
            } else {
                Log.w(TAG, "CCC descriptor write failed with status $status")
            }
        }
    }

    /**
     * Parse heart rate from the Heart Rate Measurement characteristic value.
     *
     * The HRMS characteristic format:
     * - Byte 0: Flags (bit 0 = 8-bit HR value, bit 1 = sensor contact, etc.)
     * - Byte 1+: HR value (8-bit if flag bit 0 = 0, 16-bit if bit 0 = 1)
     */
    private fun parseHeartRate(value: ByteArray): Int? {
        if (value.isEmpty()) return null

        val flags = value[0].toInt()
        val isHeartRate16Bit = (flags and 0x01) != 0

        return if (isHeartRate16Bit && value.size >= 3) {
            // 16-bit HR value (little-endian)
            val hr = (value[1].toInt() and 0xFF) or (value[2].toInt() and 0xFF shl 8)
            hr
        } else if (value.size >= 2) {
            // 8-bit HR value
            value[1].toInt() and 0xFF
        } else {
            null
        }
    }

    // ── Scan Callback ───────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
            val device = result.device
            val rssi = result.rssi
            Log.d(TAG, "Found HR device: ${device.address} (RSSI: $rssi)")

            // Auto-connect to the first device found
            connect(device)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed with error code $errorCode")
        }
    }

    /**
     * Clean up resources.
     */
    fun destroy() {
        stopScan()
        disconnect()
    }
}
