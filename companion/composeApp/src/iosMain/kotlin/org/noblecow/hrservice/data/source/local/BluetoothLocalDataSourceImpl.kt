package org.noblecow.hrservice.data.source.local

import co.touchlab.kermit.Logger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.concurrent.Volatile
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
import platform.CoreBluetooth.CBManagerAuthorizationDenied
import platform.CoreBluetooth.CBManagerAuthorizationRestricted
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
import platform.Foundation.NSNotificationCenter
import platform.Foundation.create
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIDevice
import platform.darwin.NSObject
import platform.darwin.NSObjectProtocol
import platform.darwin.dispatch_get_main_queue

private const val TAG = "BluetoothLocalDataSourceImpl"
private const val ADVERTISING_STOP_INITIAL_DELAY_MS = 50L
private const val ADVERTISING_STOP_MAX_DELAY_MS = 500L
private const val ADVERTISING_STOP_TIMEOUT_MS = 5000L // 5 seconds max
private const val SERVICE_ADD_MAX_RETRIES = 3
private const val SERVICE_ADD_RETRY_DELAY_MS = 500L

/**
 * iOS implementation of BluetoothLocalDataSource using CoreBluetooth.
 *
 * This class manages BLE advertising and GATT server operations to broadcast heart rate
 * measurements to connected centrals using the standard Heart Rate Service profile (0x180D).
 *
 * **Thread Safety:** All state updates are dispatched through [scope] using the injected
 * [DefaultDispatcher] to ensure thread-safe state management from CoreBluetooth delegate callbacks.
 * All shared mutable state is protected with [Volatile] annotations for visibility across threads.
 *
 * **Permissions:** Requires `NSBluetoothAlwaysUsageDescription` in Info.plist. The system
 * automatically prompts for permission when CBPeripheralManager is initialized.
 *
 * **Background Mode:** Supports background peripheral mode if `bluetooth-peripheral` is included
 * in UIBackgroundModes. Advertising continues in background with reduced frequency.
 *
 * **Lifecycle:** This is a singleton scoped to [AppScope]. Lifecycle observers are managed
 * automatically and cleaned up when the advertising state flow is cancelled. For explicit cleanup
 * (e.g., during testing or app termination), call [cleanup] to release all resources.
 *
 * **Resource Management:** Pending async operations (service retries, stop polling) are tracked
 * and automatically cancelled when [stopAdvertising] or [cleanup] is called.
 *
 * @param logger Logger for debugging and monitoring
 * @param dispatcher Coroutine dispatcher for background operations (injected via [DefaultDispatcher])
 */
@Suppress("TooManyFunctions") // All functions are necessary for BLE peripheral operations
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
internal class BluetoothLocalDataSourceImpl(
    @DefaultDispatcher dispatcher: CoroutineDispatcher,
    defaultLogger: Logger
) : BluetoothLocalDataSource {
    private val logger = defaultLogger.withTag(TAG)
    private val scope: CoroutineScope = CoroutineScope(Job() + dispatcher)

    /**
     * Device name advertised in BLE advertisements.
     * Defaults to device model (e.g., "iPhone", "iPad").
     * Can be changed to UIDevice.currentDevice.name for user's device name (e.g., "John's iPhone").
     */
    private val advertisedDeviceName: String
        get() = UIDevice.currentDevice.name

    // CBPeripheralManagerDelegateProtocol doesn't give us a callback for when advertising has actually stopped
    private val manualState = MutableStateFlow<AdvertisingState?>(null)

    // Client connection state
    private val _clientConnectedState = MutableStateFlow(false)
    override val clientConnectedState: StateFlow<Boolean> = _clientConnectedState.asStateFlow()

    // Peripheral manager - created once and reused
    private var peripheralManager: CBPeripheralManager? = null

    // Heart Rate Service GATT components
    private var heartRateMeasurementChar: CBMutableCharacteristic? = null
    private var heartRateService: CBMutableService? = null

    @Volatile private var isServiceAdded = false

    @Volatile private var shouldStartAdvertisingAfterServiceAdded = false

    @Volatile private var serviceAddRetryCount = 0

    /**
     * Job for service addition retry operations.
     * Tracked so it can be cancelled when needed (e.g., during stopAdvertising or cleanup).
     */
    private var serviceRetryJob: Job? = null

    /**
     * Job for advertising stop polling operations.
     * Tracked so it can be cancelled when needed (e.g., during cleanup).
     */
    private var stopPollingJob: Job? = null

    /**
     * Set of centrals that have subscribed to heart rate notifications.
     * A client is considered "connected" when they subscribe to notifications.
     * Thread-safe via MutableStateFlow.
     */
    private val subscribedCentrals = MutableStateFlow<Set<CBCentral>>(emptySet())

    /**
     * Notification observers for app lifecycle events.
     * These are stored so they can be removed when the advertising state flow is cancelled.
     * Setup is done lazily in callbackFlow to prevent memory leaks.
     */
    private var backgroundObserver: NSObjectProtocol? = null
    private var foregroundObserver: NSObjectProtocol? = null

    /**
     * Sets up observers for app lifecycle events (background/foreground transitions).
     * This enables proper handling of BLE advertising when the app state changes.
     */
    private fun setupLifecycleObservers() {
        val notificationCenter = NSNotificationCenter.defaultCenter

        // Observe app entering background
        backgroundObserver = notificationCenter.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = null
        ) { _ ->
            logger.d("App entered background")
            // iOS automatically reduces advertising frequency in background
            // Local name will not be advertised (iOS limitation)
            if (peripheralManager?.isAdvertising == true) {
                logger.d("Advertising continues in background (reduced frequency, no local name)")
            }
        }

        // Observe app becoming active (foreground)
        foregroundObserver = notificationCenter.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = null
        ) { _ ->
            logger.d("App became active (foreground)")
            // Check if we need to restart advertising with full parameters
            if (peripheralManager?.isAdvertising == true) {
                logger.d("Advertising already active, continuing with full parameters")
            }
        }

        logger.d("Lifecycle observers registered")
    }

    /**
     * Removes lifecycle observers.
     * Called from awaitClose when the advertising state flow is cancelled.
     */
    private fun removeLifecycleObservers() {
        val notificationCenter = NSNotificationCenter.defaultCenter

        backgroundObserver?.let {
            notificationCenter.removeObserver(it)
            logger.d("Background observer removed")
        }
        foregroundObserver?.let {
            notificationCenter.removeObserver(it)
            logger.d("Foreground observer removed")
        }

        backgroundObserver = null
        foregroundObserver = null
    }

    /**
     * Cleans up services and resets state.
     * Called when Bluetooth resets, goes to background, or encounters errors.
     */
    private fun cleanupServices() {
        logger.d(
            "cleanupServices() - Before: " +
                "isServiceAdded=$isServiceAdded, " +
                "shouldStartAdvertising=$shouldStartAdvertisingAfterServiceAdded, " +
                "subscribedCentrals=${subscribedCentrals.value.size}"
        )
        peripheralManager?.removeAllServices()
        heartRateService = null
        heartRateMeasurementChar = null
        isServiceAdded = false
        shouldStartAdvertisingAfterServiceAdded = false
        subscribedCentrals.update { emptySet() }
        logger.d("cleanupServices() - Complete: All services and state cleared")
    }

    /**
     * Flow of advertising state changes from the CoreBluetooth peripheral manager.
     * Uses callbackFlow to convert callback-based API to Flow.
     * The peripheral manager is created once and reused for repeated start/stop cycles.
     */
    @OptIn(ExperimentalForeignApi::class)
    private val delegateAdvertisingState: Flow<AdvertisingState> = callbackFlow {
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
                    }

                    CBManagerStatePoweredOff -> {
                        logger.d("Bluetooth powered off")
                        // Force cleanup if currently advertising or if service addition pending
                        if (peripheralManager?.isAdvertising == true ||
                            shouldStartAdvertisingAfterServiceAdded
                        ) {
                            shouldStartAdvertisingAfterServiceAdded = false
                            subscribedCentrals.update { emptySet() }
                            trySend(AdvertisingState.Stopped)
                            scope.launch {
                                _clientConnectedState.emit(false)
                            }
                        }
                    }

                    CBManagerStateUnauthorized -> {
                        logger.e("Bluetooth unauthorized - user denied permission")
                        shouldStartAdvertisingAfterServiceAdded = false
                        subscribedCentrals.update { emptySet() }
                        trySend(AdvertisingState.Failure)
                        scope.launch {
                            _clientConnectedState.emit(false)
                        }
                    }

                    CBManagerStateUnsupported -> {
                        logger.e("Bluetooth unsupported on this device")
                        shouldStartAdvertisingAfterServiceAdded = false
                        subscribedCentrals.update { emptySet() }
                        trySend(AdvertisingState.Failure)
                        scope.launch {
                            _clientConnectedState.emit(false)
                        }
                    }

                    CBManagerStateResetting -> {
                        logger.w("Bluetooth resetting - cleaning up services")
                        cleanupServices()
                        scope.launch {
                            _clientConnectedState.emit(false)
                        }
                        trySend(AdvertisingState.Stopped)
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
                subscribedCentrals.update { it + central }
                logger.d("Subscribed centrals count: ${subscribedCentrals.value.size}")
                scope.launch {
                    val connected = subscribedCentrals.value.isNotEmpty()
                    logger.d("Emitting clientConnectedState: $connected")
                    _clientConnectedState.emit(connected)
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
                subscribedCentrals.update { it - central }
                logger.d("Subscribed centrals count: ${subscribedCentrals.value.size}")
                scope.launch {
                    val connected = subscribedCentrals.value.isNotEmpty()
                    logger.d("Emitting clientConnectedState: $connected")
                    _clientConnectedState.emit(connected)
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
                    serviceAddRetryCount = 0 // Reset retry counter on success
                    // Start advertising if requested
                    if (shouldStartAdvertisingAfterServiceAdded) {
                        shouldStartAdvertisingAfterServiceAdded = false
                        startAdvertisingInternal()
                    }
                } else {
                    logger.e(
                        "Failed to add Heart Rate Service " +
                            "(attempt ${serviceAddRetryCount + 1}/$SERVICE_ADD_MAX_RETRIES): " +
                            "${error.localizedDescription}"
                    )

                    // Retry logic
                    if (serviceAddRetryCount < SERVICE_ADD_MAX_RETRIES) {
                        serviceAddRetryCount++
                        logger.d("Retrying service addition after ${SERVICE_ADD_RETRY_DELAY_MS}ms delay")
                        serviceRetryJob = scope.launch {
                            delay(SERVICE_ADD_RETRY_DELAY_MS)
                            // Clean up before retry
                            peripheralManager?.removeAllServices()
                            heartRateService = null
                            heartRateMeasurementChar = null
                            // Retry adding the service
                            addHeartRateService()
                        }
                    } else {
                        logger.e("Exhausted all service addition retries, giving up")
                        serviceAddRetryCount = 0
                        shouldStartAdvertisingAfterServiceAdded = false
                        trySend(AdvertisingState.Failure)
                    }
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

        // Setup lifecycle observers for background/foreground transitions
        // This is done here instead of init() to prevent memory leaks
        setupLifecycleObservers()

        // Send initial state
        val initialState = if (peripheralManager?.isAdvertising == true) {
            AdvertisingState.Started
        } else {
            AdvertisingState.Stopped
        }
        trySend(initialState)

        awaitClose {
            // Flow cancelled - clean up lifecycle observers
            removeLifecycleObservers()
            // Peripheral manager persists and is NOT closed here
            // It will be cleaned up when the entire class instance is destroyed
            logger.d("advertisingState flow closed, lifecycle observers removed (peripheral manager persists)")
        }
    }

    override val advertisingState: StateFlow<AdvertisingState> = combine(
        delegateAdvertisingState,
        manualState
    ) { delegateState, manual ->
        val resultState = when (manual) {
            AdvertisingState.Stopping -> AdvertisingState.Stopping
            AdvertisingState.Stopped -> AdvertisingState.Stopped
            AdvertisingState.Failure -> AdvertisingState.Failure
            else -> delegateState
        }
        logger.d(
            "State combine: delegateState=$delegateState, manualState=$manual -> result=$resultState"
        )
        resultState
    }
        .stateIn(
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
            logger.d("Heart Rate Service already added (flag is true)")
            return
        }

        if (peripheralManager?.state != CBManagerStatePoweredOn) {
            logger.e("Cannot add service: Bluetooth not powered on")
            return
        }

        // If we have a service object but isServiceAdded is false, it means we might be
        // trying to add the service again after the peripheral manager was recreated.
        // Remove all services first to prevent the crash: "Services cannot be added more than once"
        if (heartRateService != null) {
            logger.d("Removing existing services to prevent duplicate add")
            peripheralManager?.removeAllServices()
            // Reset service objects so they get recreated
            heartRateService = null
            heartRateMeasurementChar = null
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
     * On iOS 13.1+, checks CBManager.authorization. Permissions are granted if authorization
     * is not explicitly denied or restricted. Also checks if peripheral manager is powered on
     * as a fallback for older iOS versions.
     *
     * @return true if permissions granted, false otherwise
     */
    override fun permissionsGranted(): Boolean {
        val authorization = CBManager.authorization
        val granted = (
            authorization != CBManagerAuthorizationDenied &&
                authorization != CBManagerAuthorizationRestricted
            ) ||
            peripheralManager?.state == CBManagerStatePoweredOn

        logger.d("Permissions granted: $granted (authorization: $authorization)")
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
     *
     * This is a suspend function that waits for advertising to actually start before returning.
     * This ensures that iOS delegate callbacks (peripheralManagerDidAddService and
     * peripheralManagerDidStartAdvertising) have completed before this function returns.
     *
     * @throws BluetoothError.InvalidState if Bluetooth is not ready or peripheral manager is not initialized
     * @throws BluetoothError.AdvertisingFailed if advertising fails to start
     */
    override suspend fun startAdvertising() {
        logger.d("startAdvertising() called")

        // Guard: Don't start if already started or starting
        val currentState = advertisingState.value
        if (currentState == AdvertisingState.Started || peripheralManager?.isAdvertising == true) {
            logger.w("startAdvertising() called but already advertising")
            return
        }

        // Guard: Check peripheral manager exists
        if (peripheralManager == null) {
            logger.e("Cannot start advertising: Peripheral manager not initialized")
            throw BluetoothError.InvalidState("Bluetooth peripheral manager not initialized")
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

                // Wait for advertising to actually start or fail
                // This ensures iOS delegate callbacks have completed before returning
                logger.d("Waiting for advertising state to become Started or Failure")
                advertisingState.first {
                    it == AdvertisingState.Started || it == AdvertisingState.Failure
                }

                val finalState = advertisingState.value
                logger.d("Advertising state is now: $finalState")

                // If advertising failed, throw an error
                if (finalState == AdvertisingState.Failure) {
                    throw BluetoothError.AdvertisingFailed("BLE advertising failed to start on iOS")
                }
            }

            else -> {
                val state = peripheralManager?.state
                logger.e("Cannot start advertising: Bluetooth not ready (state=$state)")
                throw BluetoothError.InvalidState("Bluetooth not powered on (state=$state)")
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

        // Clear manual state override so delegate state can propagate
        logger.d("startAdvertisingInternal: Clearing manualState (setting to null)")
        manualState.value = null

        val serviceUUID = CBUUID.UUIDWithString(HRS_SERVICE_UUID_VAL)

        val advertisingData: Map<Any?, *> = mapOf(
            CBAdvertisementDataServiceUUIDsKey to listOf(serviceUUID),
            CBAdvertisementDataLocalNameKey to advertisedDeviceName
        )

        peripheralManager?.startAdvertising(advertisingData)
        logger.d("Starting advertising with service UUID: $serviceUUID, device name: $advertisedDeviceName")
    }

    /**
     * Stops BLE advertising.
     *
     * This method stops advertising, clears all subscribed centrals, and updates
     * the advertising and client connected states.
     */
    override fun stopAdvertising() {
        logger.d("stopAdvertising() called")

        // Guard: Don't stop if already stopped or stopping
        if (advertisingState.value == AdvertisingState.Stopped) {
            logger.d("stopAdvertising() called but already stopped")
        } else if (manualState.value == AdvertisingState.Stopping) {
            logger.d("stopAdvertising() called but already stopping")
        } else {
            // Stop advertising if peripheral manager exists
            peripheralManager?.stopAdvertising()

            // Cancel any pending service addition retry
            serviceRetryJob?.cancel()
            serviceRetryJob = null

            // Clear any pending service addition requests
            if (shouldStartAdvertisingAfterServiceAdded) {
                logger.d("Clearing pending advertising request")
                shouldStartAdvertisingAfterServiceAdded = false
            }

            // Clear subscribed centrals
            subscribedCentrals.update { emptySet() }

            // Update client connected state and track the stop polling job
            stopPollingJob?.cancel() // Cancel any existing stop polling
            stopPollingJob = scope.launch {
                logger.d("stopAdvertising: Emitting clientConnectedState=false")
                _clientConnectedState.emit(false)
                logger.d("stopAdvertising: Setting manualState=Stopping")
                manualState.value = AdvertisingState.Stopping
                peripheralManager?.let { manager ->
                    // Use exponential backoff to poll for advertising stop
                    // Start with short delay, double each iteration, cap at max delay
                    var currentDelay = ADVERTISING_STOP_INITIAL_DELAY_MS
                    var totalElapsed = 0L
                    var attempt = 0

                    while (manager.isAdvertising() && totalElapsed < ADVERTISING_STOP_TIMEOUT_MS) {
                        attempt++
                        logger.d(
                            "Waiting for advertising to stop " +
                                "(attempt $attempt, delay: ${currentDelay}ms, elapsed: ${totalElapsed}ms)"
                        )
                        delay(currentDelay)
                        totalElapsed += currentDelay

                        // Exponential backoff: double delay each time, capped at max
                        currentDelay = minOf(currentDelay * 2, ADVERTISING_STOP_MAX_DELAY_MS)
                    }

                    if (manager.isAdvertising()) {
                        logger.e(
                            "Timed out waiting for advertising to stop after ${totalElapsed}ms " +
                                "($attempt attempts)"
                        )
                        logger.e("stopAdvertising: Setting manualState=Failure")
                        manualState.value = AdvertisingState.Failure
                    } else {
                        logger.d(
                            "Advertising stopped after ${totalElapsed}ms ($attempt attempts)"
                        )
                        logger.d("stopAdvertising: Setting manualState=Stopped")
                        manualState.value = AdvertisingState.Stopped
                    }
                }
            }
        }
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
            subscribedCentrals.value.isEmpty() -> {
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
            logger.d("notifyHeartRate($bpm): Notified ${subscribedCentrals.value.size} central(s)")
        } else {
            logger.w("notifyHeartRate($bpm): Failed to notify (queue full or other error)")
        }

        return updateSuccess
    }

    /**
     * Cleans up all resources and cancels pending operations.
     *
     * This method should be called when the data source is no longer needed (e.g., app termination).
     * It stops advertising, cancels all pending async jobs, removes services, and releases the
     * peripheral manager.
     *
     * **Note:** This class is a singleton scoped to [AppScope], so this method is typically
     * not needed during normal app operation. It's provided for completeness and testing purposes.
     *
     * **Thread Safety:** This method is safe to call from any thread.
     */
    fun cleanup() {
        logger.d("cleanup() called - releasing all resources")

        // Stop advertising if currently active
        if (peripheralManager?.isAdvertising == true) {
            peripheralManager?.stopAdvertising()
            logger.d("Stopped advertising during cleanup")
        }

        // Cancel all pending async operations
        serviceRetryJob?.cancel()
        serviceRetryJob = null
        stopPollingJob?.cancel()
        stopPollingJob = null
        logger.d("Cancelled all pending async jobs")

        // Clean up services
        cleanupServices()

        // Release peripheral manager
        peripheralManager = null
        logger.d("Released peripheral manager")

        // Cancel the coroutine scope to stop all ongoing coroutines
        scope.coroutineContext[Job]?.cancel()
        logger.d("Cancelled coroutine scope")

        logger.d("cleanup() complete - all resources released")
    }
}
