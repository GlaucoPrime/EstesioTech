package com.code.EstesioTech

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.*

@SuppressLint("MissingPermission")
object BleManager {
    private const val TAG = "BleManager"

    // UUIDs padrão UART (Nordic) - O padrão do ESP32 BLE
    private val SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    private val CHARACTERISTIC_UUID_TX = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E") // Read/Notify
    private val CCCD_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null

    // Estado da conexão
    private var isDeviceConnected = false
    private var connectedDeviceAddress: String? = null

    interface ConnectionListener {
        fun onConnected()
        fun onDisconnected()
        fun onDataReceived(data: String)
        fun onError(message: String)
    }

    private var listener: ConnectionListener? = null

    fun setListener(l: ConnectionListener?) {
        listener = l
    }

    fun isConnected(): Boolean = isDeviceConnected

    fun initialize(context: Context): Boolean {
        if (bluetoothAdapter == null) {
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothAdapter = btManager.adapter
        }
        return bluetoothAdapter != null
    }

    fun connectToDevice(address: String, context: Context) {
        // Se já estiver conectado no mesmo dispositivo, não faz nada (Evita reconexão desnecessária)
        if (isDeviceConnected && connectedDeviceAddress == address && bluetoothGatt != null) {
            Log.d(TAG, "Já conectado a $address. Mantendo conexão.")
            listener?.onConnected()
            return
        }

        // Se estiver conectado em OUTRO, desconecta antes
        if (isDeviceConnected) {
            disconnect()
        }

        if (!initialize(context)) return

        try {
            val device = bluetoothAdapter?.getRemoteDevice(address)
            Log.d(TAG, "Iniciando conexão GATT com $address...")

            // AutoConnect = false para conexão direta e rápida
            bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device?.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device?.connectGatt(context, false, gattCallback)
            }

            connectedDeviceAddress = address
        } catch (e: IllegalArgumentException) {
            listener?.onError("Endereço MAC inválido.")
        }
    }

    fun disconnect() {
        if (bluetoothGatt == null) return

        Log.d(TAG, "Desconectando...")
        try {
            bluetoothGatt?.disconnect()
            // O close() é crucial para o Android liberar a antena pro próximo scan
            bluetoothGatt?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao fechar: ${e.message}")
        } finally {
            bluetoothGatt = null
            isDeviceConnected = false
            connectedDeviceAddress = null
            listener?.onDisconnected()
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Conectado ao GATT. Descobrindo serviços...")
                isDeviceConnected = true
                // Pequeno delay para estabilizar antes de descobrir serviços
                Handler(Looper.getMainLooper()).postDelayed({
                    gatt.discoverServices()
                }, 500)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Desconectado do GATT.")
                isDeviceConnected = false
                listener?.onDisconnected()
                // Tenta limpar o recurso se caiu sozinho
                try { gatt.close() } catch (e: Exception) {}
                bluetoothGatt = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    val characteristic = service.getCharacteristic(CHARACTERISTIC_UUID_TX)
                    if (characteristic != null) {
                        gatt.setCharacteristicNotification(characteristic, true)

                        val descriptor = characteristic.getDescriptor(CCCD_DESCRIPTOR_UUID)
                        if (descriptor != null) {
                            val enableValue = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                gatt.writeDescriptor(descriptor, enableValue)
                            } else {
                                @Suppress("DEPRECATION")
                                descriptor.value = enableValue
                                @Suppress("DEPRECATION")
                                gatt.writeDescriptor(descriptor)
                            }
                            Log.i(TAG, "Notificações habilitadas!")
                            listener?.onConnected()
                        }
                    }
                }
            } else {
                Log.w(TAG, "Falha ao descobrir serviços: $status")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            val text = value.toString(Charsets.UTF_8)
            listener?.onDataReceived(text)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val text = characteristic.value?.toString(Charsets.UTF_8) ?: ""
            listener?.onDataReceived(text)
        }
    }
}