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
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/** What the heart-rate strap connection is currently doing. */
sealed interface HeartRateStatus {
    data object Unsupported : HeartRateStatus
    data object BluetoothOff : HeartRateStatus
    data object PermissionRequired : HeartRateStatus
    data object Idle : HeartRateStatus
    data object Scanning : HeartRateStatus
    data class Connecting(val deviceName: String?) : HeartRateStatus
    data class Connected(val deviceName: String?) : HeartRateStatus
}

/** A strap discovered during a scan, for the settings UI to choose from. */
data class HeartRateDevice(val address: String, val name: String?)

/**
 * Connects to a Bluetooth LE heart-rate strap and exposes live BPM.
 *
 * The previous implementation could not work on any device. It:
 *  - passed `null` as the Context to `connectGatt`, which throws;
 *  - called `setCharacteristicNotification` without writing the Client
 *    Characteristic Configuration descriptor, so the peripheral was never told
 *    to send notifications and no reading ever arrived;
 *  - discovered straps by matching `"Heart"` or `"HR"` in the bonded-device
 *    name, missing every strap actually on the market (a Polar H10 is named
 *    "Polar H10"), instead of filtering the scan by the standard Heart Rate
 *    service UUID;
 *  - scanned in an uncancellable `while (true)` loop;
 *  - read `value[1]` after only checking `isEmpty()`, so a one-byte packet
 *    crashed the BLE callback thread;
 *  - and called `startAutoReconnect()` from `disconnect()`, so an intentional
 *    disconnect immediately reconnected.
 */
class BleHeartRateManager(context: Context) {

    private val appContext = context.applicationContext

    private val bluetoothManager: BluetoothManager? =
        ContextCompat.getSystemService(appContext, BluetoothManager::class.java)

    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _heartRate = MutableStateFlow<Int?>(null)

    /** Latest BPM, or null when no strap is delivering data. */
    val heartRate: StateFlow<Int?> = _heartRate.asStateFlow()

    private val _status = MutableStateFlow<HeartRateStatus>(HeartRateStatus.Idle)
    val status: StateFlow<HeartRateStatus> = _status.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<HeartRateDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<HeartRateDevice>> = _discoveredDevices.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var scanning = false

    /** Address the user picked, so a reconnect targets the same strap. */
    private var preferredAddress: String? = null

    // ── Permissions and capability ──────────────────────────────────

    /**
     * Runtime permissions differ across the range this app supports: API 24–30
     * gate BLE scanning behind location, API 31+ behind the dedicated
     * BLUETOOTH_SCAN/CONNECT permissions.
     */
    val requiredPermissions: List<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun hasPermissions(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun currentBlocker(): HeartRateStatus? = when {
        adapter == null -> HeartRateStatus.Unsupported
        !adapter.isEnabled -> HeartRateStatus.BluetoothOff
        !hasPermissions() -> HeartRateStatus.PermissionRequired
        else -> null
    }

    // ── Scanning ────────────────────────────────────────────────────

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val name = safeDeviceName(device)

            _discoveredDevices.update { existing ->
                if (existing.any { it.address == device.address }) existing
                else existing + HeartRateDevice(device.address, name)
            }

            val wanted = preferredAddress
            if (wanted == null || wanted == device.address) {
                stopScan()
                connect(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "BLE scan failed with code $errorCode")
            scanning = false
            _status.value = HeartRateStatus.Idle
        }
    }

    /**
     * Scans for straps advertising the Heart Rate service. Connects to the
     * first match, or to [preferredAddress] when one has been chosen.
     */
    @SuppressLint("MissingPermission") // guarded by currentBlocker()
    fun startScan() {
        currentBlocker()?.let {
            _status.value = it
            return
        }
        if (scanning) return

        val bluetoothAdapter = adapter ?: run {
            _status.value = HeartRateStatus.Unsupported
            return
        }
        val scanner = bluetoothAdapter.bluetoothLeScanner ?: run {
            _status.value = HeartRateStatus.Unsupported
            return
        }

        // Bonded straps advertise intermittently to save battery, so try a
        // known one directly before falling back to a scan.
        val bonded = runCatching { bluetoothAdapter.bondedDevices.orEmpty() }
            .getOrDefault(emptySet())
        val bondedMatch = bonded.firstOrNull { it.address == preferredAddress }
        if (bondedMatch != null) {
            connect(bondedMatch)
            return
        }

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(HEART_RATE_SERVICE))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanning = true
        _status.value = HeartRateStatus.Scanning
        _discoveredDevices.value = emptyList()
        runCatching { scanner.startScan(listOf(filter), settings, scanCallback) }
            .onFailure {
                scanning = false
                _status.value = HeartRateStatus.Idle
                Log.w(TAG, "Could not start BLE scan", it)
            }
    }

    @SuppressLint("MissingPermission") // guarded by currentBlocker()
    fun stopScan() {
        if (!scanning) return
        scanning = false
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        if (_status.value is HeartRateStatus.Scanning) _status.value = HeartRateStatus.Idle
    }

    // ── Connection ──────────────────────────────────────────────────

    /** Targets a specific strap; pass null to accept the first one found. */
    fun selectDevice(address: String?) {
        preferredAddress = address
        disconnect()
        startScan()
    }

    @SuppressLint("MissingPermission") // guarded by currentBlocker()
    private fun connect(device: BluetoothDevice) {
        currentBlocker()?.let {
            _status.value = it
            return
        }
        closeGatt()
        _status.value = HeartRateStatus.Connecting(safeDeviceName(device))
        gatt = device.connectGatt(appContext, /* autoConnect = */ true, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _status.value = HeartRateStatus.Connected(safeDeviceName(gatt.device))
                    preferredAddress = gatt.device?.address ?: preferredAddress
                    gatt.discoverServices()
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    // connectGatt(autoConnect = true) means the stack retries
                    // on our behalf; we must not tear the GATT down here or
                    // that reconnect can never happen.
                    _heartRate.value = null
                    _status.value = HeartRateStatus.Idle
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "Service discovery failed: $status")
                return
            }

            val characteristic = gatt.getService(HEART_RATE_SERVICE)
                ?.getCharacteristic(HEART_RATE_MEASUREMENT)
                ?: run {
                    Log.w(TAG, "Strap does not expose the Heart Rate Measurement characteristic")
                    return
                }

            gatt.setCharacteristicNotification(characteristic, true)

            // Local notification routing alone does nothing: the peripheral
            // only starts sending once its CCCD is written.
            val cccd = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG) ?: run {
                Log.w(TAG, "Heart Rate characteristic is missing its CCCD")
                return
            }

            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(cccd)
            }
        }

        // API 33+ delivers the value as a parameter; older releases require
        // reading the deprecated `characteristic.value`. Both are implemented
        // because only one is called on any given release.
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == HEART_RATE_MEASUREMENT) {
                parseHeartRate(value)?.let { _heartRate.value = it }
            }
        }

        @Deprecated("Required for API < 33", ReplaceWith(""))
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            if (characteristic.uuid == HEART_RATE_MEASUREMENT) {
                characteristic.value?.let { bytes ->
                    parseHeartRate(bytes)?.let { _heartRate.value = it }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        gatt?.let {
            runCatching { it.disconnect() }
            runCatching { it.close() }
        }
        gatt = null
    }

    /** Drops the connection without scheduling a reconnect. */
    fun disconnect() {
        closeGatt()
        _heartRate.value = null
        _status.value = HeartRateStatus.Idle
    }

    fun destroy() {
        stopScan()
        closeGatt()
        _heartRate.value = null
        _status.value = HeartRateStatus.Idle
    }

    @SuppressLint("MissingPermission")
    private fun safeDeviceName(device: BluetoothDevice?): String? =
        runCatching { device?.name }.getOrNull()

    companion object {
        private const val TAG = "BleHeartRateManager"

        val HEART_RATE_SERVICE: UUID = UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")
        val HEART_RATE_MEASUREMENT: UUID = UUID.fromString("00002A37-0000-1000-8000-00805F9B34FB")
        val CLIENT_CHARACTERISTIC_CONFIG: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

        /**
         * Decodes a Heart Rate Measurement packet per the Bluetooth SIG
         * specification. Bit 0 of the flags byte selects the value width:
         * clear means a uint8 at offset 1, set means a little-endian uint16 at
         * offsets 1–2.
         *
         * Returns null for a truncated packet rather than throwing, since this
         * runs on the Binder callback thread where an exception is fatal.
         *
         * Internal so it can be unit-tested without a Bluetooth stack.
         */
        internal fun parseHeartRate(value: ByteArray): Int? {
            if (value.size < 2) return null
            val flags = value[0].toInt()
            val isUint16 = (flags and 0x01) != 0

            return if (isUint16) {
                if (value.size < 3) return null
                ((value[2].toInt() and 0xFF) shl 8) or (value[1].toInt() and 0xFF)
            } else {
                value[1].toInt() and 0xFF
            }.takeIf { it in 1..300 }
        }
    }
}
