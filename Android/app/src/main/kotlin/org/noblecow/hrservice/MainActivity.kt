package org.noblecow.hrservice

import android.Manifest
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
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.gson.gson
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.util.Arrays
import java.util.UUID
import org.noblecow.hrservice.databinding.ActivityServerBinding

private const val TAG = "HRService"
private const val PORT_LISTEN = 12345

data class Request(val bpm: Int)

data class Response(val status: String)

class MainActivity : FragmentActivity() {

    /* Local UI */
    private lateinit var localHRView: TextView

    /* Bluetooth API */
    private lateinit var bluetoothManager: BluetoothManager
    private var bluetoothGattServer: BluetoothGattServer? = null

    /* Collection of notification subscribers */
    private val registeredDevices = mutableSetOf<BluetoothDevice>()

    /* Heart Rate Service UUID */
    private val HR_SERVICE: UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb")

    /* Heart Rate Measurement Characteristic */
    private val HRM_CHARACTERISTIC: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")

    /* Mandatory Client Characteristic Config Descriptor */
    private val CLIENT_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    private val heartRateData = byteArrayOf(0, 99)
    private var heartBeat = false
    private val permissionsRequired = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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

    private val requestMultiplePermissions = registerForActivityResult(
        RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.containsValue(false)) {
            Toast.makeText(this, R.string.permissions_denied, Toast.LENGTH_LONG).show()
        } else {
            startBluetooth()
        }
    }

    private val enableBluetoothLauncher =
        registerForActivityResult(StartActivityForResult()) { _ ->
            // BroadcastReceiver handles the next step
        }

    /**
     * Listens for Bluetooth adapter events to enable/disable
     * advertising and server functionality.
     */
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF)

            when (state) {
                BluetoothAdapter.STATE_ON -> {
                    startAdvertising()
                    startServer()
                }
                BluetoothAdapter.STATE_OFF -> {
                    stopServer()
                    stopAdvertising()
                }
            }
        }
    }

    /**
     * Callback to receive information about the advertisement process.
     */
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.i(TAG, "LE Advertise Started.")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.w(TAG, "LE Advertise Failed: $errorCode")
            FatalErrorDialogFragment(getString(R.string.error_advertise))
                .show(supportFragmentManager, "error")
        }
    }

    /**
     * Callback to handle incoming requests to the GATT server.
     * All read/write requests for characteristics and descriptors are handled here.
     */
    private val gattServerCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "BluetoothDevice CONNECTED: $device")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "BluetoothDevice DISCONNECTED: $device")
                // Remove device from any active subscriptions
                registeredDevices.remove(device)
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (HRM_CHARACTERISTIC == characteristic.uuid) {
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

        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor
        ) {
            if (CLIENT_CONFIG == descriptor.uuid) {
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

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (CLIENT_CONFIG == descriptor.uuid) {
                if (Arrays.equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, value)) {
                    Log.d(TAG, "Subscribe device to notifications: $device")
                    registeredDevices.add(device)
                } else if (BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE.contentEquals(value)
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

    /**
     * Return a configured [BluetoothGattService] instance for the
     * Heart Rate Service.
     */
    private fun createHRService(): BluetoothGattService {
        val service = BluetoothGattService(
            HR_SERVICE,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        // Current Time characteristic
        val characteristic = BluetoothGattCharacteristic(
            HRM_CHARACTERISTIC,
            // Read-only characteristic, supports notifications
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        val descriptor = BluetoothGattDescriptor(
            CLIENT_CONFIG,
            // Read/write descriptor
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        characteristic.addDescriptor(descriptor)

        service.addCharacteristic(characteristic)

        return service
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityServerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        localHRView = binding.textBpm

        // Devices with a display should not go to sleep
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        confirmPermissions()
    }

    private fun confirmPermissions() {
        val permissionsNeeded = mutableListOf<String>()

        permissionsRequired.forEach { permission ->
            when (ContextCompat.checkSelfPermission(this, permission)) {
                PackageManager.PERMISSION_GRANTED -> {
                    Log.d(TAG, "Permission already granted: $permission")
                }

                PackageManager.PERMISSION_DENIED -> {
                    permissionsNeeded.add(permission)
                }
            }
        }

        if (permissionsNeeded.isNotEmpty()) {
            requestMultiplePermissions.launch(permissionsNeeded.toTypedArray())
        } else {
            startBluetooth()
        }
    }

    private fun startBluetooth() {
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        // We can't continue without proper Bluetooth support
        if (!checkBluetoothSupport(bluetoothAdapter)) {
            FatalErrorDialogFragment(
                getString(R.string.error_hardware)
            ).show(supportFragmentManager, "error")
        } else {
            // Register for system Bluetooth events
            val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            registerReceiver(bluetoothReceiver, filter)

            if (!bluetoothAdapter.isEnabled) {
                Log.d(TAG, "Bluetooth is currently disabled...enabling")
                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                enableBluetoothLauncher.launch(intent)
            } else {
                Log.d(TAG, "Bluetooth enabled...starting services")
                startAdvertising()
                startServer()
            }

            embeddedServer(Netty, PORT_LISTEN) {
                install(StatusPages) {
                    exception<Throwable> { call, e ->
                        call.respondText(
                            e.localizedMessage ?: e::javaClass.name,
                            ContentType.Text.Plain,
                            HttpStatusCode.InternalServerError
                        )
                    }
                }
                install(ContentNegotiation) {
                    gson {}
                }
                routing {
                    get("/") {
                        call.respond(Response(status = "OK"))
                    }

                    post("/") {
                        val request = call.receive<Request>()
                        Log.d(TAG, "Received POST request: $request")
                        heartRateData[1] = request.bpm.toByte()
                        updateLocalUi(request.bpm)
                        notifyRegisteredDevices()
                        call.respond(request)
                    }
                }
            }.start(wait = false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothManager.adapter?.let {
            if (it.isEnabled) {
                stopServer()
                stopAdvertising()
            }

            unregisterReceiver(bluetoothReceiver)
        }
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

            !packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) -> {
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
    private fun startAdvertising() {
        val bluetoothLeAdvertiser: BluetoothLeAdvertiser? =
            bluetoothManager.adapter.bluetoothLeAdvertiser

        bluetoothLeAdvertiser?.let {
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build()

            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(ParcelUuid(HR_SERVICE))
                .build()

            it.startAdvertising(settings, data, advertiseCallback)
        } ?: {
            Log.w(TAG, "Failed to create advertiser")
            FatalErrorDialogFragment(getString(R.string.error_advertise))
                .show(supportFragmentManager, "error")
        }
    }

    /**
     * Stop Bluetooth advertisements.
     */
    private fun stopAdvertising() {
        val bluetoothLeAdvertiser: BluetoothLeAdvertiser? =
            bluetoothManager.adapter.bluetoothLeAdvertiser
        bluetoothLeAdvertiser?.let {
            it.stopAdvertising(advertiseCallback)
        } ?: Log.w(TAG, "Failed to create advertiser")
    }

    /**
     * Initialize the GATT server instance with the services/characteristics
     * from the Time Profile.
     */
    private fun startServer() {
        bluetoothGattServer = bluetoothManager.openGattServer(this, gattServerCallback)

        bluetoothGattServer?.addService(createHRService())
            ?: Log.w(TAG, "Unable to create GATT server")

        // Initialize the local UI
        updateLocalUi(0)
    }

    /**
     * Shut down the GATT server.
     */
    private fun stopServer() {
        bluetoothGattServer?.close()
    }

    /**
     * Send a heart rate measurement notification to any devices that are subscribed
     * to the characteristic.
     */
    private fun notifyRegisteredDevices() {
        if (registeredDevices.isEmpty()) {
            Log.d(TAG, "No subscribers registered")
            return
        }

        Log.d(TAG, "Sending update to ${registeredDevices.size} subscribers")
        for (device in registeredDevices) {
            val characteristic = bluetoothGattServer
                ?.getService(HR_SERVICE)
                ?.getCharacteristic(HRM_CHARACTERISTIC)
            characteristic?.value = heartRateData
            bluetoothGattServer?.notifyCharacteristicChanged(device, characteristic, false)
        }
    }

    /**
     * Update graphical UI on devices that support it with the current bpm.
     */
    private fun updateLocalUi(bpm: Int) {
        var text = "$bpm\n"
        if (heartBeat) {
            text += "♡"
        }
        heartBeat = !heartBeat
        runOnUiThread {
            localHRView.setText(text)
        }
    }
}
