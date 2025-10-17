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
import android.util.Log
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.noblecow.hrservice.data.source.local.blessed.HeartRateService
import org.noblecow.hrservice.data.source.local.blessed.Service
import org.noblecow.hrservice.di.DefaultDispatcher

private const val TAG = "BluetoothLocalDataSourceImpl"

@SuppressLint("MissingPermission")
@ContributesBinding(AppScope::class)
@Inject
@SingleIn(AppScope::class)
internal class BluetoothLocalDataSourceImpl(
    private val context: Context,
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) : BluetoothLocalDataSource {

    private val _advertisingState = MutableStateFlow<AdvertisingState>(AdvertisingState.Stopped)
    override val advertisingState = _advertisingState.asStateFlow()
    private val _clientConnectedState = MutableStateFlow(false)
    override val clientConnectedState = _clientConnectedState.asStateFlow()

    private var isInitialized = false
    private val bluetoothManager: BluetoothManager? = context.getSystemService(
        Context.BLUETOOTH_SERVICE
    ) as? BluetoothManager
    private val localScope: CoroutineScope = CoroutineScope(Job() + dispatcher)
    private val serviceImplementations = HashMap<BluetoothGattService, Service>()
    private var heartRateService: HeartRateService? = null

    @Suppress("TooManyFunctions")
    private val peripheralManagerCallback: BluetoothPeripheralManagerCallback =
        object : BluetoothPeripheralManagerCallback() {
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
                        _clientConnectedState.value = true
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
                peripheralManager?.connectedCentrals?.isEmpty().run {
                    _clientConnectedState.value = false
                }
            }

            override fun onAdvertisingStarted(settingsInEffect: AdvertiseSettings) {
                localScope.launch {
                    _advertisingState.emit(AdvertisingState.Started)
                }
            }

            override fun onAdvertisingStopped() {
                localScope.launch {
                    _advertisingState.emit(AdvertisingState.Stopped)
                }
            }

            override fun onAdvertiseFailure(advertiseError: AdvertiseError) {
                localScope.launch {
                    Log.e(TAG, "${advertiseError.name} ${advertiseError.value}")
                    _advertisingState.emit(AdvertisingState.Failure)
                }
            }
        }
    private var peripheralManager: BluetoothPeripheralManager?

    init {
        peripheralManager = bluetoothManager?.let {
            BluetoothPeripheralManager(
                context,
                it,
                peripheralManagerCallback
            )
        }
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

    private fun initialize() {
        bluetoothManager?.let {
            it.adapter.name = Build.MODEL
        }
        if (peripheralManager == null) { // Nulled on stopping
            peripheralManager = bluetoothManager?.let {
                BluetoothPeripheralManager(
                    context,
                    it,
                    peripheralManagerCallback
                )
            }
        }
        peripheralManager?.apply {
            openGattServer()
            removeAllServices()

            heartRateService = HeartRateService(this).also {
                serviceImplementations[it.service] = it // More services in the example code
            }
            for (service in serviceImplementations.keys) {
                add(service)
            }
            isInitialized = true
        }
    }

    override fun stop() {
        if (isInitialized) {
            peripheralManager?.close()
        }
        peripheralManager = null
    }

    override fun permissionsGranted() = peripheralManager?.permissionsGranted() == true

    override fun startAdvertising() {
        initialize()
        peripheralManager?.let {
            if (!it.isAdvertising) {
                val advertiseSettings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                    .setConnectable(true)
                    .setTimeout(0)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                    .build()
                val advertiseData = AdvertiseData.Builder()
                    .setIncludeTxPowerLevel(true)
                    .addServiceUuid(ParcelUuid(HeartRateService.Companion.HRS_SERVICE_UUID))
                    .build()
                val scanResponse = AdvertiseData.Builder()
                    .setIncludeDeviceName(true)
                    .build()
                peripheralManager?.startAdvertising(advertiseSettings, advertiseData, scanResponse)
            } else {
                Log.d(TAG, "Already advertising")
            }
        }
    }

    @Suppress("UseOrEmpty")
    override fun getMissingPermissions(): Array<out String> = peripheralManager?.getMissingPermissions().orEmpty()

    override fun notifyHeartRate(bpm: Int) = heartRateService?.notifyHeartRate(bpm)
}
