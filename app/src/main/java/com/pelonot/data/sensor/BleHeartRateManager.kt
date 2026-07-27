package com.pelonot.data.sensor

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

/**
 * Manages BLE heart rate monitor connections.
 * Scans for bonded devices, connects via GATT, and subscribes to heart rate notifications.
 */
class BleHeartRateManager(context: Context) {
    private val TAG = "BleHeartRateManager"
    private val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private val heartRateStateFlow = MutableStateFlow<Int>(0)
    private val _heartRate: StateFlow<Int> = heartRateStateFlow.asStateFlow()
    val heartRate: StateFlow<Int> = _heartRate

    private var device: BluetoothDevice? = null
    private var gatt: BluetoothGatt? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val heartRateServiceUuid = UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")
    private val heartRateCharacteristicUuid = UUID.fromString("00002A37-0000-1000-8000-00805F9B34FB")

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected to BLE device")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w(TAG, "BLE disconnected")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val heartRateChar = gatt.getService(heartRateServiceUuid)
                    ?.getCharacteristic(heartRateCharacteristicUuid)
                heartRateChar?.let {
                    gatt.setCharacteristicNotification(it, true)
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == heartRateCharacteristicUuid) {
                val heartRate = parseHeartRate(characteristic.value)
                heartRateStateFlow.value = heartRate
            }
        }
    }

    /**
     * Start scanning for bonded heart rate devices.
     */
    fun startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.w(TAG, "Bluetooth not available or disabled")
            return
        }

        scope.launch {
            while (true) {
                val devices = bluetoothAdapter.bondedDevices
                for (d in devices) {
                    if (d.name?.contains("Heart", ignoreCase = true) == true ||
                        d.name?.contains("HR", ignoreCase = true) == true) {
                        connectToDevice(d)
                        break
                    }
                }
                kotlinx.coroutines.delay(5000) // Scan every 5 seconds
            }
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        this.device = device
        gatt = device.connectGatt(null, false, gattCallback)
    }

    fun stopScan() {
        // Scanning is continuous; stop is handled by disconnect
    }

    fun disconnect() {
        gatt?.close()
        gatt = null
        device = null
    }

    fun destroy() {
        disconnect()
        scope.cancel()
    }

    private fun parseHeartRate(value: ByteArray): Int {
        if (value.isEmpty()) return 0
        // Heart rate is typically a single byte (uint8) or two bytes (uint16)
        return if (value[0].toInt() and 0x01 == 0) {
            // 8-bit heart rate
            value[1].toInt() and 0xFF
        } else {
            // 16-bit heart rate
            ((value[1].toInt() and 0xFF) shl 8) or (value[2].toInt() and 0xFF)
        }
    }
}