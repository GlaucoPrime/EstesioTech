package com.code.EstesioTech.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.UUID

@SuppressLint("MissingPermission")
object BleManager {

    private const val TAG = "BleManager"
    private val SERVICE_UUID        = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    private val CHARACTERISTIC_TX   = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
    private val DESCRIPTOR_CCCD     = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    private const val SERVICE_DISCOVERY_DELAY_MS = 500L

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var isDeviceConnected = false
    private var connectedDeviceAddress: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    interface ConnectionListener {
        fun onConnected()
        fun onDisconnected()
        fun onDataReceived(data: String)
        fun onError(message: String)
    }

    private var listener: ConnectionListener? = null

    fun setListener(l: ConnectionListener?) { listener = l }
    fun isConnected(): Boolean = isDeviceConnected
    fun getConnectedAddress(): String? = connectedDeviceAddress

    fun initialize(context: Context): Boolean {
        if (bluetoothAdapter == null) {
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bluetoothAdapter = btManager?.adapter
        }
        return bluetoothAdapter != null
    }

    fun connectToDevice(address: String, context: Context) {
        if (isDeviceConnected && connectedDeviceAddress == address && bluetoothGatt != null) {
            mainHandler.post { listener?.onConnected() }
            return
        }
        if (isDeviceConnected) disconnect()

        if (!initialize(context)) {
            mainHandler.post { listener?.onError("Bluetooth não disponível neste dispositivo.") }
            return
        }

        try {
            bluetoothGatt?.close()
            bluetoothGatt = null
            val device = bluetoothAdapter!!.getRemoteDevice(address)
            bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context.applicationContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context.applicationContext, false, gattCallback)
            }
            connectedDeviceAddress = address
        } catch (e: IllegalArgumentException) {
            mainHandler.post { listener?.onError("Endereço do dispositivo é inválido.") }
        } catch (e: Exception) {
            mainHandler.post { listener?.onError("Erro ao iniciar conexão: ${e.message}") }
        }
    }

    fun disconnect() {
        if (bluetoothGatt == null) return
        try {
            bluetoothGatt?.disconnect()
        } catch (e: Exception) {
            forceClose()
        }
    }

    private fun forceClose() {
        try { bluetoothGatt?.close() } catch (e: Exception) { }
        finally {
            bluetoothGatt = null
            isDeviceConnected = false
            connectedDeviceAddress = null
            mainHandler.post { listener?.onDisconnected() }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when {
                newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS -> {
                    isDeviceConnected = true
                    mainHandler.postDelayed({ gatt.discoverServices() }, SERVICE_DISCOVERY_DELAY_MS)
                }
                newState == BluetoothProfile.STATE_DISCONNECTED -> forceClose()
                else -> forceClose()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                mainHandler.post { listener?.onError("Falha ao descobrir serviços BLE.") }
                return
            }
            val service = gatt.getService(SERVICE_UUID)
            if (service == null) {
                mainHandler.post { listener?.onError("Serviço não encontrado. Verifique o firmware do ESP32.") }
                return
            }
            val characteristic = service.getCharacteristic(CHARACTERISTIC_TX)
            if (characteristic == null) {
                mainHandler.post { listener?.onError("Característica TX não encontrada.") }
                return
            }
            gatt.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(DESCRIPTOR_CCCD)
            if (descriptor == null) {
                mainHandler.post { listener?.onError("Descriptor CCCD não encontrado.") }
                return
            }
            val enableValue = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, enableValue)
            } else {
                @Suppress("DEPRECATION") descriptor.value = enableValue
                @Suppress("DEPRECATION") gatt.writeDescriptor(descriptor)
            }
            mainHandler.post { listener?.onConnected() }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            val data = value.toString(Charsets.UTF_8)
            mainHandler.post { listener?.onDataReceived(data) }
        }

        @Deprecated("Deprecated in API 33")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val data = characteristic.value?.toString(Charsets.UTF_8) ?: return
            mainHandler.post { listener?.onDataReceived(data) }
        }
    }
}