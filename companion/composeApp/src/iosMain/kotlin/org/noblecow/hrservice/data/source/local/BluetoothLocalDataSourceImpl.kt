package org.noblecow.hrservice.data.source.local

import co.touchlab.kermit.Logger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.noblecow.hrservice.data.util.HRS_SERVICE_UUID_VAL
import org.noblecow.hrservice.data.util.HR_MEASUREMENT_CHAR_UUID_VAL
import org.noblecow.hrservice.data.util.MAX_BPM
import org.noblecow.hrservice.di.DefaultDispatcher
import platform.CoreBluetooth.CBAdvertisementDataLocalNameKey
import platform.CoreBluetooth.CBAdvertisementDataServiceUUIDsKey
import platform.CoreBluetooth.CBAttributePermissionsReadable
import platform.CoreBluetooth.CBCentral
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBCharacteristicPropertyNotify
import platform.CoreBluetooth.CBManager
import platform.CoreBluetooth.CBManagerAuthorizationAllowedAlways
import platform.CoreBluetooth.CBManagerStatePoweredOff
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.CoreBluetooth.CBManagerStateResetting
import platform.CoreBluetooth.CBManagerStateUnauthorized
import platform.CoreBluetooth.CBManagerStateUnknown
import platform.CoreBluetooth.CBManagerStateUnsupported
import platform.CoreBluetooth.CBMutableCharacteristic
import platform.CoreBluetooth.CBMutableService
import platform.CoreBluetooth.CBPeripheralManager
import platform.CoreBluetooth.CBPeripheralManagerDelegateProtocol
import platform.CoreBluetooth.CBService
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.create
import platform.UIKit.UIDevice
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue

private const val TAG = "BluetoothLocalDataSourceImpl"

/**
 * iOS implementation of BluetoothLocalDataSource using CoreBluetooth.
 *
 * This class manages BLE advertising and GATT server operations to broadcast heart rate
 * measurements to connected centrals using the standard Heart Rate Service profile (0x180D).
 *
 * **Thread Safety:** All state updates are dispatched through [scope] using the injected
 * [DefaultDispatcher] to ensure thread-safe state management from CoreBluetooth delegate callbacks.
 *
 * **Permissions:** Requires `NSBluetoothAlwaysUsageDescription` in Info.plist. The system
 * automatically prompts for permission when CBPeripheralManager is initialized.
 *
 * **Background Mode:** Supports background peripheral mode if `bluetooth-peripheral` is included
 * in UIBackgroundModes. Advertising continues in background with reduced frequency.
 *
 * **Lifecycle:** This is a singleton scoped to [AppScope].
 *
 * @param logger Logger for debugging and monitoring
 * @param dispatcher Coroutine dispatcher for background operations (injected via [DefaultDispatcher])
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
internal class BluetoothLocalDataSourceImpl(
    @DefaultDispatcher dispatcher: CoroutineDispatcher,
    defaultLogger: Logger
) : BluetoothLocalDataSource {
    private val logger = defaultLogger.withTag(TAG)
    private val scope: CoroutineScope = CoroutineScope(Job() + dispatcher)

    // Client connection state
    private val _clientConnectedState = MutableStateFlow(false)
    override val clientConnectedState: StateFlow<Boolean> = _clientConnectedState.asStateFlow()

    // Peripheral manager - created once and reused
    private var peripheralManager: CBPeripheralManager? = null

    // Heart Rate Service GATT components
    private var heartRateMeasurementChar: CBMutableCharacteristic? = null
    private var heartRateService: CBMutableService? = null
    private var isServiceAdded = false
    private var shouldStartAdvertisingAfterServiceAdded = false

    /**
     * Set of centrals that have subscribed to heart rate notifications.
     * A client is considered "connected" when they subscribe to notifications.
     */
    private val subscribedCentrals = mutableSetOf<CBCentral>()

    /**
     * Flow of advertising state changes from the CoreBluetooth peripheral manager.
     * Uses callbackFlow to convert callback-based API to Flow.
     * The peripheral manager is created once and reused for repeated start/stop cycles.
     */
    @OptIn(ExperimentalForeignApi::class)
    override val advertisingState: StateFlow<AdvertisingState> = callbackFlow {
        /**
         * CBPeripheralManagerDelegate implementation.
         * Handles all CoreBluetooth peripheral events and updates state accordingly.
         */
        val delegate = object : NSObject(), CBPeripheralManagerDelegateProtocol {

            /**
             * Called when the peripheral manager's state changes.
             * Monitors Bluetooth power state and authorization changes.
             */
            override fun peripheralManagerDidUpdateState(peripheral: CBPeripheralManager) {
                val state = peripheral.state
                logger.d("Peripheral manager state changed: $state")

                when (state) {
                    CBManagerStatePoweredOn -> {
                        logger.d("Bluetooth powered on and ready")
                        // Don't change advertising state - let startAdvertising() handle it
                    }

                    CBManagerStatePoweredOff -> {
                        logger.d("Bluetooth powered off")
                        // Force cleanup if currently advertising or if service addition pending
                        if (peripheralManager?.isAdvertising == true ||
                            shouldStartAdvertisingAfterServiceAdded
                        ) {
                            shouldStartAdvertisingAfterServiceAdded = false
                            subscribedCentrals.clear()
                            trySend(AdvertisingState.Stopped)
                            scope.launch {
                                _clientConnectedState.emit(false)
                            }
                        }
                    }

                    CBManagerStateUnauthorized -> {
                        logger.e("Bluetooth unauthorized - user denied permission")
                        shouldStartAdvertisingAfterServiceAdded = false
                        subscribedCentrals.clear()
                        trySend(AdvertisingState.Failure)
                        scope.launch {
                            _clientConnectedState.emit(false)
                        }
                    }

                    CBManagerStateUnsupported -> {
                        logger.e("Bluetooth unsupported on this device")
                        shouldStartAdvertisingAfterServiceAdded = false
                        subscribedCentrals.clear()
                        trySend(AdvertisingState.Failure)
                        scope.launch {
                            _clientConnectedState.emit(false)
                        }
                    }

                    CBManagerStateResetting -> {
                        logger.w("Bluetooth resetting")
                    }

                    CBManagerStateUnknown -> {
                        logger.w("Bluetooth state unknown")
                    }

                    else -> {
                        logger.w("Unknown Bluetooth state: $state")
                    }
                }
            }

            /**
             * Called when advertising starts successfully or fails.
             *
             * @param peripheral The peripheral manager
             * @param error Error if advertising failed, null if successful
             */
            override fun peripheralManagerDidStartAdvertising(
                peripheral: CBPeripheralManager,
                error: NSError?
            ) {
                if (error == null) {
                    logger.d("Advertising started successfully")
                    trySend(AdvertisingState.Started)
                } else {
                    logger.e("Advertising failed: ${error.localizedDescription}")

                    // Clear any pending requests since advertising failed
                    shouldStartAdvertisingAfterServiceAdded = false

                    trySend(AdvertisingState.Failure)
                }
            }

            /**
             * Called when a central subscribes to a characteristic's notifications.
             * This indicates a client is "connected" and ready to receive heart rate data.
             *
             * @param peripheral The peripheral manager
             * @param central The central that subscribed
             * @param didSubscribeToCharacteristic The characteristic being subscribed to
             */
            @ObjCSignatureOverride
            override fun peripheralManager(
                peripheral: CBPeripheralManager,
                central: CBCentral,
                didSubscribeToCharacteristic: CBCharacteristic
            ) {
                logger.d(
                    "Central ${central.identifier} subscribed to characteristic ${didSubscribeToCharacteristic.UUID}"
                )
                subscribedCentrals.add(central)
                scope.launch {
                    _clientConnectedState.emit(subscribedCentrals.isNotEmpty())
                }
            }

            /**
             * Called when a central unsubscribes from a characteristic's notifications.
             *
             * @param peripheral The peripheral manager
             * @param central The central that unsubscribed
             * @param didUnsubscribeFromCharacteristic The characteristic being unsubscribed from
             */
            @ObjCSignatureOverride
            override fun peripheralManager(
                peripheral: CBPeripheralManager,
                central: CBCentral,
                didUnsubscribeFromCharacteristic: CBCharacteristic
            ) {
                logger.d(
                    "Central ${central.identifier} unsubscribed from characteristic " +
                        "${didUnsubscribeFromCharacteristic.UUID}"
                )
                subscribedCentrals.remove(central)
                scope.launch {
                    _clientConnectedState.emit(subscribedCentrals.isNotEmpty())
                }
            }

            /**
             * Called when a service is added to the peripheral manager.
             *
             * @param peripheral The peripheral manager
             * @param didAddService The service that was added
             * @param error Error if service addition failed, null if successful
             */
            override fun peripheralManager(
                peripheral: CBPeripheralManager,
                didAddService: CBService,
                error: NSError?
            ) {
                if (error == null) {
                    logger.d("Heart Rate Service added successfully")
                    isServiceAdded = true
                    // Start advertising if requested
                    if (shouldStartAdvertisingAfterServiceAdded) {
                        shouldStartAdvertisingAfterServiceAdded = false
                        startAdvertisingInternal()
                    }
                } else {
                    logger.e("Failed to add Heart Rate Service: ${error.localizedDescription}")
                    shouldStartAdvertisingAfterServiceAdded = false
                    trySend(AdvertisingState.Failure)
                }
            }
        }

        // Create the peripheral manager once if not already created
        if (peripheralManager == null) {
            peripheralManager = CBPeripheralManager(
                delegate = delegate,
                queue = dispatch_get_main_queue()
            )
            logger.d("Peripheral manager created")
        }

        // Send initial state
        val initialState = if (peripheralManager?.isAdvertising == true) {
            AdvertisingState.Started
        } else {
            AdvertisingState.Stopped
        }
        trySend(initialState)

        awaitClose {
            // Flow cancelled - peripheral manager persists and is NOT closed here
            // It will be cleaned up when the entire class instance is destroyed
            logger.d("advertisingState flow closed (peripheral manager persists)")
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = AdvertisingState.Stopped
    )

    /**
     * Creates the Heart Rate Service with its characteristic.
     * This should be called once before starting advertising.
     */
    private fun createHeartRateService() {
        if (heartRateService != null) {
            logger.d("Heart Rate Service already created")
            return
        }

        // Create Heart Rate Measurement characteristic with NOTIFY property
        heartRateMeasurementChar = CBMutableCharacteristic(
            type = CBUUID.UUIDWithString(HR_MEASUREMENT_CHAR_UUID_VAL),
            properties = CBCharacteristicPropertyNotify,
            value = null, // Value is dynamic, set during notifications
            permissions = CBAttributePermissionsReadable
        )

        // Create Heart Rate Service
        heartRateService = CBMutableService(
            type = CBUUID.UUIDWithString(HRS_SERVICE_UUID_VAL),
            primary = true
        )
        heartRateService?.setCharacteristics(listOf(heartRateMeasurementChar!!))

        logger.d("Heart Rate Service created with characteristic ${heartRateMeasurementChar?.UUID}")
    }

    /**
     * Adds the Heart Rate Service to the peripheral manager.
     * Should only be called when Bluetooth is powered on.
     */
    private fun addHeartRateService() {
        if (isServiceAdded) {
            logger.d("Heart Rate Service already added")
            return
        }

        if (peripheralManager?.state != CBManagerStatePoweredOn) {
            logger.e("Cannot add service: Bluetooth not powered on")
            return
        }

        createHeartRateService()
        peripheralManager?.addService(heartRateService!!)
        logger.d("Adding Heart Rate Service to peripheral manager")
    }

    /**
     * Gets the current hardware state of the Bluetooth adapter.
     *
     * @return Current hardware state (READY, DISABLED, or HARDWARE_UNSUITABLE)
     */
    override fun getHardwareState(): HardwareState = when (peripheralManager?.state) {
        CBManagerStatePoweredOn -> {
            logger.d("Hardware state: READY")
            HardwareState.READY
        }

        CBManagerStatePoweredOff -> {
            logger.d("Hardware state: DISABLED (Bluetooth off)")
            HardwareState.DISABLED
        }

        CBManagerStateResetting -> {
            logger.d("Hardware state: DISABLED (Bluetooth resetting)")
            HardwareState.DISABLED
        }

        CBManagerStateUnknown -> {
            logger.d("Hardware state: DISABLED (Bluetooth state unknown)")
            HardwareState.DISABLED
        }

        CBManagerStateUnsupported, CBManagerStateUnauthorized -> {
            logger.d("Hardware state: HARDWARE_UNSUITABLE")
            HardwareState.HARDWARE_UNSUITABLE
        }

        else -> {
            logger.w("Hardware state: DISABLED (unexpected state)")
            HardwareState.DISABLED
        }
    }

    /**
     * Checks if Bluetooth permissions have been granted.
     *
     * On iOS 13.1+, checks CBManager.authorization.
     * On iOS 13.0, checks if state is not unauthorized.
     *
     * @return true if permissions granted, false otherwise
     */
    override fun permissionsGranted(): Boolean {
        // CBManager.authorization is available on iOS 13.1+
        // For simplicity, we just check authorization on all iOS 13+ devices
        val granted = CBManager.authorization == CBManagerAuthorizationAllowedAlways ||
            peripheralManager?.state == CBManagerStatePoweredOn

        logger.d("Permissions granted: $granted")
        return granted
    }

    /**
     * Gets missing permissions required for Bluetooth operation.
     *
     * iOS doesn't have granular permission strings like Android.
     * Returns a generic "Bluetooth" permission if not granted.
     *
     * @return Array of missing permission descriptions
     */
    override fun getMissingPermissions(): Array<out String> = if (!permissionsGranted()) {
        logger.d("Missing permissions: [Bluetooth]")
        arrayOf("Bluetooth")
    } else {
        logger.d("No missing permissions")
        emptyArray()
    }

    /**
     * Starts BLE advertising with Heart Rate Service.
     *
     * This method checks if Bluetooth is powered on, adds the Heart Rate Service
     * if not already added, and starts advertising with the service UUID.
     */
    override fun startAdvertising() {
        logger.d("startAdvertising() called")

        // Guard: Don't start if already started or starting
        val currentState = advertisingState.value
        if (currentState == AdvertisingState.Started) {
            logger.w("startAdvertising() called but already advertising")
            return
        }

        // Guard: Check peripheral manager exists
        if (peripheralManager == null) {
            logger.e("Cannot start advertising: Peripheral manager not initialized")
            return
        }

        when (peripheralManager?.state) {
            CBManagerStatePoweredOn -> {
                if (!isServiceAdded) {
                    logger.d("Service not added yet, so adding service first")
                    shouldStartAdvertisingAfterServiceAdded = true
                    addHeartRateService()
                } else {
                    logger.d("Service already added, starting advertising")
                    startAdvertisingInternal()
                }
            }

            else -> {
                logger.e("Cannot start advertising: Bluetooth not ready (state=${peripheralManager?.state})")
            }
        }
    }

    /**
     * Internal method to actually start advertising after service is added.
     * This is called either directly from startAdvertising() if service is already added,
     * or from the didAddService callback.
     */
    private fun startAdvertisingInternal() {
        // Double-check state before actually starting
        if (peripheralManager?.state != CBManagerStatePoweredOn) {
            logger.e("startAdvertisingInternal: Bluetooth not powered on, aborting")
            return
        }

        val serviceUUID = CBUUID.UUIDWithString(HRS_SERVICE_UUID_VAL)
        val deviceName = UIDevice.currentDevice.model

        val advertisingData: Map<Any?, *> = mapOf(
            CBAdvertisementDataServiceUUIDsKey to listOf(serviceUUID),
            CBAdvertisementDataLocalNameKey to deviceName
        )

        peripheralManager?.startAdvertising(advertisingData)
        logger.d("Starting advertising with service UUID: $serviceUUID, device name: $deviceName")
    }

    /**
     * Stops BLE advertising.
     *
     * This method stops advertising, clears all subscribed centrals, and updates
     * the advertising and client connected states.
     */
    override fun stopAdvertising() {
        logger.d("stopAdvertising() called")

        // Guard: Don't stop if already stopped
        val currentState = advertisingState.value
        if (currentState == AdvertisingState.Stopped) {
            logger.d("stopAdvertising() called but already stopped")
            return
        }

        // Stop advertising if peripheral manager exists
        peripheralManager?.stopAdvertising()

        // Clear any pending service addition requests
        if (shouldStartAdvertisingAfterServiceAdded) {
            logger.d("Clearing pending advertising request")
            shouldStartAdvertisingAfterServiceAdded = false
        }

        // Clear subscribed centrals
        subscribedCentrals.clear()

        // Update client connected state
        scope.launch {
            _clientConnectedState.emit(false)
        }

        logger.d("Advertising stop initiated")
    }

    /**
     * Notifies subscribed centrals of a new heart rate value.
     *
     * Encodes the heart rate value according to the Bluetooth Heart Rate Measurement
     * characteristic specification and sends it to all subscribed centrals.
     *
     * @param bpm Heart rate in beats per minute (0-255)
     * @return true if notification sent successfully, false otherwise
     */
    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override fun notifyHeartRate(bpm: Int): Boolean {
        val success = when {
            // Validate BPM range (UINT8 format supports 0-255)
            bpm !in 0..MAX_BPM -> {
                logger.e("notifyHeartRate($bpm): Invalid BPM value, must be 0-255")
                false
            }

            // Check if any centrals are subscribed
            subscribedCentrals.isEmpty() -> {
                logger.d("notifyHeartRate($bpm): No centrals subscribed, skipping notification")
                false
            }

            // Check if Bluetooth is powered on
            peripheralManager?.state != CBManagerStatePoweredOn -> {
                logger.w("notifyHeartRate($bpm): Bluetooth not powered on, skipping notification")
                false
            }

            // Check if characteristic exists
            heartRateMeasurementChar == null -> {
                logger.e("notifyHeartRate($bpm): Heart rate characteristic not initialized")
                false
            }

            // Check if we're actually advertising (additional safety check)
            advertisingState.value != AdvertisingState.Started -> {
                logger.w("notifyHeartRate($bpm): Not advertising, skipping notification")
                false
            }

            else -> true
        }

        if (!success) {
            return false
        }

        // Encode heart rate value using standard Bluetooth Heart Rate Measurement format
        // Byte 0: Flags field
        //   Bit 0: 0 = Heart Rate Value Format is UINT8 (0-255 bpm)
        //   Bit 1-2: 00 = Sensor Contact Status not supported
        //   Bit 3: 0 = Energy Expended Status not present
        //   Bit 4: 0 = RR-Interval not present
        // Byte 1: Heart Rate Measurement Value (0-255)
        val flags: Byte = 0x00
        val heartRateValue: Byte = bpm.toByte()
        val data = byteArrayOf(flags, heartRateValue)

        // Convert ByteArray to NSData
        val nsData = data.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = data.size.toULong())
        }

        // Send notification to all subscribed centrals
        val updateSuccess = peripheralManager?.updateValue(
            value = nsData,
            forCharacteristic = heartRateMeasurementChar!!,
            onSubscribedCentrals = null // null = all subscribed centrals
        ) ?: false

        if (updateSuccess) {
            logger.d("notifyHeartRate($bpm): Notified ${subscribedCentrals.size} central(s)")
        } else {
            logger.w("notifyHeartRate($bpm): Failed to notify (queue full or other error)")
        }

        return updateSuccess
    }
}
