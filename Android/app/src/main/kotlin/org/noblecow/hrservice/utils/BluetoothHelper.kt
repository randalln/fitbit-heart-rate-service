package org.noblecow.hrservice.utils

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val TAG = "BluetoothHelper"
const val ERROR_ADVERTISING_GENERIC = -1

class BluetoothHelper @Inject constructor(
    @ApplicationContext val context: Context
) {
    /* Bluetooth API */
    private val bluetoothManager: BluetoothManager = context.getSystemService(
        Context.BLUETOOTH_SERVICE
    ) as BluetoothManager
    private var bluetoothGattServer: BluetoothGattServer? = null

    /* Collection of notification subscribers */
    val registeredDevices = mutableSetOf<BluetoothDevice>()

    /* Heart Rate Service UUID */
    private val hrServiceUUID: UUID = UUID.fromString(
        "0000180D-0000-1000-8000-00805f9b34fb"
    )

    /* Heart Rate Measurement Characteristic */
    private val hrmCharacteristicUUID: UUID = UUID.fromString(
        "00002a37-0000-1000-8000-00805f9b34fb"
    )

    /* Mandatory Client Characteristic Config Descriptor */
    private val clientConfigUUID: UUID = UUID.fromString(
        "00002902-0000-1000-8000-00805f9b34fb"
    )

    private val heartRateData = byteArrayOf(0, 99)
    val permissionsRequired = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    /**
     * Initialize the GATT server instance with the services/characteristics
     * from the Time Profile.
     */
    @SuppressLint("MissingPermission")
    internal fun getGattServerFlow(): Flow<BluetoothDevice?> {
        return callbackFlow {
            /**
             * Callback to handle incoming requests to the GATT server.
             * All read/write requests for characteristics and descriptors are handled here.
             */
            val gattServerCallback = object : BluetoothGattServerCallback() {
                override fun onConnectionStateChange(
                    device: BluetoothDevice,
                    status: Int,
                    newState: Int
                ) {
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            Log.i(TAG, "BluetoothDevice CONNECTED: $device $status $newState")
                            trySend(device)
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            Log.i(TAG, "BluetoothDevice DISCONNECTED: $device $status")
                            // Remove device from any active subscriptions
                            registeredDevices.remove(device)
                            trySend(null)
                        }
                        else -> {
                            Log.i(
                                TAG,
                                "BluetoothDevice onConnectionStateChange ${device.address} $status $newState"
                            )
                        }
                    }
                }

                @SuppressLint("MissingPermission")
                override fun onCharacteristicReadRequest(
                    device: BluetoothDevice,
                    requestId: Int,
                    offset: Int,
                    characteristic: BluetoothGattCharacteristic
                ) {
                    if (hrmCharacteristicUUID == characteristic.uuid) {
                        Log.i(TAG, "Read HRM Characteristic")
                        bluetoothGattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            heartRateData
                        )
                    } else {
                        // Invalid characteristic
                        Log.w(TAG, "Invalid Characteristic Read: " + characteristic.uuid)
                        bluetoothGattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_FAILURE,
                            0,
                            null
                        )
                    }
                }

                @SuppressLint("MissingPermission")
                override fun onDescriptorReadRequest(
                    device: BluetoothDevice,
                    requestId: Int,
                    offset: Int,
                    descriptor: BluetoothGattDescriptor
                ) {
                    if (clientConfigUUID == descriptor.uuid) {
                        Log.d(TAG, "Config descriptor read")
                        val returnValue = if (registeredDevices.contains(device)) {
                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        } else {
                            BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                        }
                        bluetoothGattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            returnValue
                        )
                    } else {
                        Log.w(TAG, "Unknown descriptor read request")
                        bluetoothGattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_FAILURE,
                            0,
                            null
                        )
                    }
                }

                @SuppressLint("MissingPermission")
                override fun onDescriptorWriteRequest(
                    device: BluetoothDevice,
                    requestId: Int,
                    descriptor: BluetoothGattDescriptor,
                    preparedWrite: Boolean,
                    responseNeeded: Boolean,
                    offset: Int,
                    value: ByteArray
                ) {
                    if (clientConfigUUID == descriptor.uuid) {
                        if (BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE.contentEquals(
                                value
                            )
                        ) {
                            Log.d(TAG, "Subscribe device to notifications: $device")
                            registeredDevices.add(device)
                            trySend(device)
                        } else if (BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                                .contentEquals(value)
                        ) {
                            Log.d(TAG, "Unsubscribe device from notifications: $device")
                            registeredDevices.remove(device)
                        }

                        if (responseNeeded) {
                            bluetoothGattServer?.sendResponse(
                                device,
                                requestId,
                                BluetoothGatt.GATT_SUCCESS,
                                0,
                                null
                            )
                        }
                    } else {
                        Log.w(TAG, "Unknown descriptor write request")
                        if (responseNeeded) {
                            bluetoothGattServer?.sendResponse(
                                device,
                                requestId,
                                BluetoothGatt.GATT_FAILURE,
                                0,
                                null
                            )
                        }
                    }
                }
            }

            bluetoothGattServer = bluetoothManager.openGattServer(context, gattServerCallback)
                .apply {
                    addService(createHRService())
                }

            awaitClose {
                bluetoothGattServer?.close()
            }
        }
    }

    /**
     * Return a configured [BluetoothGattService] instance for the
     * Heart Rate Service.
     */
    private fun createHRService(): BluetoothGattService {
        val service = BluetoothGattService(
            hrServiceUUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        // Current Time characteristic
        val characteristic = BluetoothGattCharacteristic(
            hrmCharacteristicUUID,
            // Read-only characteristic, supports notifications
            BluetoothGattCharacteristic.PROPERTY_READ or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        val descriptor = BluetoothGattDescriptor(
            clientConfigUUID,
            // Read/write descriptor
            BluetoothGattDescriptor.PERMISSION_READ or
                BluetoothGattDescriptor.PERMISSION_WRITE
        )
        characteristic.addDescriptor(descriptor)

        service.addCharacteristic(characteristic)

        return service
    }

    /**
     * Listens for Bluetooth adapter events to enable/disable
     * advertising and server functionality.
     */
    internal fun getBluetoothReceiverFlow(): Flow<Int> {
        return callbackFlow {
            val bluetoothReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val state = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.STATE_OFF
                    )
                    trySend(state)
                }
            }

            val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            context.registerReceiver(bluetoothReceiver, filter)

            awaitClose {
                context.unregisterReceiver(bluetoothReceiver)
            }
        }
    }

    internal fun getBLEState(): BLEState {
        return bluetoothManager.let { manager ->
            val bluetoothAdapter = manager.adapter
            // We can't continue without proper Bluetooth support
            if (!checkBluetoothSupport(bluetoothAdapter)) {
                null
            } else {
                if (!bluetoothAdapter.isEnabled) {
                    BLEState.DISABLED
                } else {
                    BLEState.READY
                }
            }
        } ?: BLEState.HARDWARE_UNSUITABLE
    }

    /**
     * Verify the level of Bluetooth support provided by the hardware.
     * @param bluetoothAdapter System [BluetoothAdapter].
     * @return true if Bluetooth is properly supported, false otherwise.
     */
    private fun checkBluetoothSupport(bluetoothAdapter: BluetoothAdapter?): Boolean {
        return when {
            bluetoothAdapter == null -> {
                Log.w(TAG, "Bluetooth is not supported")
                false
            }

            !context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) -> {
                Log.w(TAG, "Bluetooth LE is not supported")
                return false
            }

            else -> true
        }
    }

    /**
     * Begin advertising over Bluetooth that this device is connectable
     * and supports the Current Time Service.
     */
    @SuppressLint("MissingPermission")
    internal fun getAdvertisingFlow(): Flow<Int> {
        return callbackFlow {
            val advertiseCallback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                    Log.i(TAG, "LE Advertise Started.")
                }

                override fun onStartFailure(errorCode: Int) {
                    trySend(errorCode)
                }
            }

            bluetoothManager.adapter?.bluetoothLeAdvertiser?.let {
                val settings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                    .setConnectable(true)
                    .setTimeout(0)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                    .build()

                val data = AdvertiseData.Builder()
                    .setIncludeDeviceName(true)
                    .setIncludeTxPowerLevel(false)
                    .addServiceUuid(ParcelUuid(hrServiceUUID))
                    .build()

                it.startAdvertising(settings, data, advertiseCallback)
            } ?: trySend(ERROR_ADVERTISING_GENERIC)

            awaitClose {
                bluetoothManager.adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
            }
        }
    }

    /**
     * Send a heart rate measurement notification to any devices that are subscribed
     * to the characteristic.
     */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun notifyRegisteredDevices(bpm: Int) {
        for (device in registeredDevices) {
            Log.d(TAG, "Sending update to ${device.address}")
            val characteristic = bluetoothGattServer
                ?.getService(hrServiceUUID)
                ?.getCharacteristic(hrmCharacteristicUUID)
            heartRateData[1] = bpm.toByte()
            characteristic?.value = heartRateData
            bluetoothGattServer?.notifyCharacteristicChanged(device, characteristic, false)
        }
    }

    enum class BLEState {
        DISABLED,
        HARDWARE_UNSUITABLE,
        READY
    }
}
