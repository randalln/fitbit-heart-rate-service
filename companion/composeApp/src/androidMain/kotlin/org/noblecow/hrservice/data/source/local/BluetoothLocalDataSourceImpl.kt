package org.noblecow.hrservice.data.source.local

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import co.touchlab.kermit.Logger
import com.welie.blessed.AdvertiseError
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.BluetoothPeripheralManager
import com.welie.blessed.BluetoothPeripheralManagerCallback
import com.welie.blessed.GattStatus
import com.welie.blessed.ReadResponse
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.noblecow.hrservice.data.source.local.blessed.HeartRateService
import org.noblecow.hrservice.data.source.local.blessed.Service
import org.noblecow.hrservice.di.DefaultDispatcher

private const val TAG = "BluetoothLocalDataSourceImpl"

/**
 * Bluetooth Low Energy peripheral implementation for advertising heart rate data.
 *
 * This class manages BLE advertising and GATT server operations to broadcast heart rate
 * measurements to connected centrals using the standard Heart Rate Service profile (0x180D).
 *
 * **Thread Safety:** All state updates are dispatched through [localScope] using the injected
 * [DefaultDispatcher] to ensure thread-safe state management from BLE callbacks.
 *
 * **Permissions:** Requires `BLUETOOTH_ADVERTISE` and `BLUETOOTH_CONNECT` permissions on Android 12+.
 * The [SuppressLint] annotation is safe because permission checks are performed upstream by
 * `StartServicesUseCase` before this class is instantiated and used.
 *
 * **Lifecycle:** This is a singleton scoped to [AppScope].
 *
 * @param context Android application context for accessing system services
 * @param dispatcher Coroutine dispatcher for background operations (injected via [DefaultDispatcher])
 */
@SuppressLint("MissingPermission")
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
internal class BluetoothLocalDataSourceImpl(
    private val context: Context,
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) : BluetoothLocalDataSource {

    @Volatile
    private var isGattInitialized = false

    private val bluetoothManager: BluetoothManager? = context.getSystemService(
        Context.BLUETOOTH_SERVICE
    ) as? BluetoothManager
    private val localScope: CoroutineScope = CoroutineScope(Job() + dispatcher)
    private val logger = Logger.withTag(TAG)
    private val serviceImplementations = HashMap<BluetoothGattService, Service>()
    private var heartRateService: HeartRateService? = null

    private val _clientConnectedState = MutableStateFlow(false)
    override val clientConnectedState = _clientConnectedState.asStateFlow()

    private val _advertisingState = MutableStateFlow<AdvertisingState>(AdvertisingState.Stopped)
    override val advertisingState: StateFlow<AdvertisingState> = _advertisingState.asStateFlow()

    /**
     * Peripheral manager instance created once and reused.
     */
    @Volatile
    private var peripheralManager: BluetoothPeripheralManager? = null

    /**
     * Callback for BLE peripheral manager events.
     * Updates advertising state in response to system callbacks.
     */
    @Suppress("TooManyFunctions")
    private val peripheralCallback = object : BluetoothPeripheralManagerCallback() {
        override fun onCharacteristicRead(
            bluetoothCentral: BluetoothCentral,
            characteristic: BluetoothGattCharacteristic
        ): ReadResponse {
            val serviceImplementation = serviceImplementations[characteristic.service]
            return serviceImplementation?.onCharacteristicRead(
                bluetoothCentral,
                characteristic
            ) ?: super.onCharacteristicRead(bluetoothCentral, characteristic)
        }

        override fun onCharacteristicWrite(
            bluetoothCentral: BluetoothCentral,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ): GattStatus {
            val serviceImplementation = serviceImplementations[characteristic.service]
            return serviceImplementation?.onCharacteristicWrite(
                bluetoothCentral,
                characteristic,
                value
            ) ?: GattStatus.REQUEST_NOT_SUPPORTED
        }

        override fun onCharacteristicWriteCompleted(
            bluetoothCentral: BluetoothCentral,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            val serviceImplementation = serviceImplementations[characteristic.service]
            serviceImplementation?.onCharacteristicWriteCompleted(
                bluetoothCentral,
                characteristic,
                value
            )
        }

        override fun onDescriptorRead(
            bluetoothCentral: BluetoothCentral,
            descriptor: BluetoothGattDescriptor
        ): ReadResponse {
            val characteristic =
                requireNotNull(descriptor.characteristic) { "Descriptor has no Characteristic" }
            val service =
                requireNotNull(characteristic.service) { "Characteristic has no Service" }
            val serviceImplementation = serviceImplementations[service]
            return serviceImplementation?.onDescriptorRead(
                bluetoothCentral,
                descriptor
            ) ?: super.onDescriptorRead(bluetoothCentral, descriptor)
        }

        override fun onDescriptorWrite(
            bluetoothCentral: BluetoothCentral,
            descriptor: BluetoothGattDescriptor,
            value: ByteArray
        ): GattStatus {
            val characteristic =
                requireNotNull(descriptor.characteristic) { "Descriptor has no Characteristic" }
            val service =
                requireNotNull(characteristic.service) { "Characteristic has no Service" }
            val serviceImplementation = serviceImplementations[service]
            return serviceImplementation?.onDescriptorWrite(
                bluetoothCentral,
                descriptor,
                value
            ) ?: GattStatus.REQUEST_NOT_SUPPORTED
        }

        override fun onNotifyingEnabled(
            bluetoothCentral: BluetoothCentral,
            characteristic: BluetoothGattCharacteristic
        ) {
            val serviceImplementation = serviceImplementations[characteristic.service]
            serviceImplementation?.onNotifyingEnabled(bluetoothCentral, characteristic)?.let {
                if (it) {
                    localScope.launch {
                        _clientConnectedState.emit(true)
                    }
                }
            }
        }

        override fun onNotifyingDisabled(
            bluetoothCentral: BluetoothCentral,
            characteristic: BluetoothGattCharacteristic
        ) {
            val serviceImplementation = serviceImplementations[characteristic.service]
            serviceImplementation?.onNotifyingDisabled(bluetoothCentral, characteristic)
        }

        override fun onNotificationSent(
            bluetoothCentral: BluetoothCentral,
            value: ByteArray,
            characteristic: BluetoothGattCharacteristic,
            status: GattStatus
        ) {
            val serviceImplementation = serviceImplementations[characteristic.service]
            serviceImplementation?.onNotificationSent(
                bluetoothCentral,
                value,
                characteristic,
                status
            )
        }

        override fun onCentralConnected(bluetoothCentral: BluetoothCentral) {
            for (serviceImplementation in serviceImplementations.values) {
                serviceImplementation.onCentralConnected(bluetoothCentral)
            }
        }

        override fun onCentralDisconnected(bluetoothCentral: BluetoothCentral) {
            for (serviceImplementation in serviceImplementations.values) {
                serviceImplementation.onCentralDisconnected(bluetoothCentral)
            }
            if (peripheralManager?.connectedCentrals?.isEmpty() == true) {
                localScope.launch {
                    _clientConnectedState.emit(false)
                }
            }
        }

        override fun onAdvertisingStarted(settingsInEffect: AdvertiseSettings) {
            logger.d("Advertising started")
            localScope.launch {
                _advertisingState.emit(AdvertisingState.Started)
            }
        }

        override fun onAdvertisingStopped() {
            logger.d("Advertising stopped")
            localScope.launch {
                _advertisingState.emit(AdvertisingState.Stopped)
            }
        }

        override fun onAdvertiseFailure(advertiseError: AdvertiseError) {
            logger.d("${advertiseError.name} ${advertiseError.value}")
            localScope.launch {
                _advertisingState.emit(AdvertisingState.Failure)
            }
        }
    }

    init {
        // Create peripheral manager on initialization
        peripheralManager = bluetoothManager?.let { manager ->
            try {
                manager.adapter.name = Build.MODEL
            } catch (e: SecurityException) {
                logger.w("Unable to set Bluetooth adapter name: ${e.message}")
            }
            BluetoothPeripheralManager(
                context,
                manager,
                peripheralCallback
            )
        }
        logger.d("Peripheral manager created")
    }

    override fun getHardwareState(): HardwareState = bluetoothManager?.let { manager ->
        val bluetoothAdapter = manager.adapter
        // We can't continue without proper Bluetooth support
        if (!checkBluetoothSupport(bluetoothAdapter)) {
            null
        } else {
            if (!bluetoothAdapter.isEnabled) {
                HardwareState.DISABLED
            } else {
                HardwareState.READY
            }
        }
    } ?: HardwareState.HARDWARE_UNSUITABLE

    /**
     * Verify the level of Bluetooth support provided by the hardware.
     * @param bluetoothAdapter System [android.bluetooth.BluetoothAdapter].
     * @return true if Bluetooth is properly supported, false otherwise.
     */
    private fun checkBluetoothSupport(bluetoothAdapter: BluetoothAdapter?): Boolean = when {
        bluetoothAdapter == null -> {
            logger.w("Bluetooth is not supported")
            false
        }

        !context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) -> {
            logger.w("Bluetooth LE is not supported")
            false
        }

        else -> true
    }

    /**
     * Initialize the GATT server with services.
     * This only needs to be called once - the GATT server persists across advertising start/stop cycles.
     */
    private fun initializeGattServer() {
        if (!isGattInitialized) {
            peripheralManager?.apply {
                openGattServer()
                removeAllServices()

                heartRateService = HeartRateService(this).also {
                    serviceImplementations[it.service] = it
                }
                for (service in serviceImplementations.keys) {
                    add(service)
                }
                isGattInitialized = true
                logger.d("GATT server initialized with Heart Rate Service")
            } ?: logger.e("Cannot initialize GATT server: peripheralManager is null")
        }
    }

    override fun permissionsGranted() = peripheralManager?.permissionsGranted() == true

    /**
     * Stop advertising.
     * Emits Stopping state immediately, then delegates to peripheral manager.
     * The GATT server and peripheral manager remain active for future advertising cycles.
     */
    override fun stopAdvertising() {
        peripheralManager?.let { manager ->
            if (manager.isAdvertising) {
                // Emit Stopping state immediately for responsive UI (synchronous)
                _advertisingState.value = AdvertisingState.Stopping
                manager.stopAdvertising()
                logger.d("Stopping advertising")
            } else {
                logger.d("Already stopped")
            }
        } ?: logger.e("Cannot stop advertising: peripheralManager is null")
    }

    /**
     * Cleanup method to properly dispose of resources.
     * Should be called when the data source is no longer needed.
     */
    fun cleanup() {
        logger.d("Cleaning up BluetoothLocalDataSource")
        peripheralManager?.close()
        peripheralManager = null
        isGattInitialized = false
        localScope.cancel()
    }

    /**
     * Start BLE advertising.
     * Initializes the GATT server on first call, then can be called repeatedly to toggle advertising.
     *
     * @throws BluetoothError.PermissionDenied if Bluetooth permissions are not granted
     * @throws BluetoothError.InvalidState if peripheral manager is not initialized
     */
    override suspend fun startAdvertising() {
        try {
            initializeGattServer()
            peripheralManager?.let { manager ->
                if (!manager.isAdvertising && advertisingState.value != AdvertisingState.Started) {
                    val advertiseSettings = AdvertiseSettings.Builder()
                        .setAdvertiseMode(ADVERTISE_MODE)
                        .setConnectable(ADVERTISE_CONNECTABLE)
                        .setTimeout(ADVERTISE_TIMEOUT)
                        .setTxPowerLevel(TX_POWER_LEVEL)
                        .build()
                    val advertiseData = AdvertiseData.Builder()
                        .setIncludeTxPowerLevel(true)
                        .addServiceUuid(ParcelUuid(HeartRateService.HRS_SERVICE_UUID))
                        .build()
                    val scanResponse = AdvertiseData.Builder()
                        .setIncludeDeviceName(true)
                        .build()
                    manager.startAdvertising(advertiseSettings, advertiseData, scanResponse)
                    logger.d("Starting advertising")
                } else {
                    logger.d("Already advertising")
                }
            } ?: run {
                logger.e("Cannot start advertising: peripheralManager is null")
                throw BluetoothError.InvalidState("Bluetooth peripheral manager not initialized")
            }
        } catch (e: SecurityException) {
            logger.e("Permission denied while starting advertising", e)
            throw BluetoothError.PermissionDenied(e)
        }
    }

    @Suppress("UseOrEmpty")
    override fun getMissingPermissions(): Array<out String> = peripheralManager?.getMissingPermissions().orEmpty()

    override fun notifyHeartRate(bpm: Int): Boolean {
        heartRateService?.notifyHeartRate(bpm)
        return heartRateService != null
    }

    companion object {
        private const val ADVERTISE_MODE = AdvertiseSettings.ADVERTISE_MODE_BALANCED
        private const val TX_POWER_LEVEL = AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM
        private const val ADVERTISE_TIMEOUT = 0
        private const val ADVERTISE_CONNECTABLE = true
    }
}
