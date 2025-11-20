package org.noblecow.hrservice.data.repository

import co.touchlab.kermit.Logger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.noblecow.hrservice.data.source.local.BluetoothLocalDataSource
import org.noblecow.hrservice.data.source.local.BpmReading
import org.noblecow.hrservice.data.source.local.HardwareState
import org.noblecow.hrservice.data.source.local.WebServerLocalDataSource
import org.noblecow.hrservice.data.util.DEFAULT_BPM

data class AppState(
    val bpm: BpmReading = BpmReading(DEFAULT_BPM, 0),
    val isClientConnected: Boolean = false,
    val servicesState: ServicesState = ServicesState.Stopped
)

interface MainRepository {
    val appStateFlow: SharedFlow<AppState>
    fun getHardwareState(): HardwareState
    fun getMissingPermissions(): Array<out String>
    fun permissionsGranted(): Boolean
    suspend fun startServices(): ServiceResult<Unit>
    suspend fun stopServices(): ServiceResult<Unit>

    /**
     * Toggles fake BPM generation for testing purposes.
     *
     * Lifecycle:
     * - Start: User-initiated via this toggle method (only when services are started)
     * - Stop: Automatically cleaned up when services stop
     *
     * @return true if fake BPM is now enabled, false if disabled or services not started
     */
    fun toggleFakeBpm(): Boolean
}

private const val TAG = "MainRepositoryImpl"

@Suppress("TooManyFunctions", "TooGenericExceptionCaught")
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
internal class MainRepositoryImpl(
    private val bluetoothLocalDataSource: BluetoothLocalDataSource,
    private val webServerLocalDataSource: WebServerLocalDataSource,
    private val fakeBpmManager: FakeBpmManager,
    private val appScope: CoroutineScope,
    defaultLogger: Logger
) : MainRepository {
    private val logger = defaultLogger.withTag(TAG)

    private val stateMachine = ServicesStateMachine()

    private val servicesState: StateFlow<ServicesState> = combine(
        bluetoothLocalDataSource.advertisingState,
        webServerLocalDataSource.webServerState,
        ::Pair
    )
        .scan(ServicesState.Stopped as ServicesState) { previousState, (advertisingState, webServerState) ->
            stateMachine.nextState(previousState, advertisingState, webServerState)
        }
        .stateIn(
            scope = appScope,
            started = SharingStarted.Eagerly,
            initialValue = ServicesState.Stopped
        )

    // Conditionally observe BPM flow only when services are started
    @OptIn(ExperimentalCoroutinesApi::class)
    private val bpm: SharedFlow<BpmReading> = servicesState
        .flatMapLatest { state ->
            when (state) {
                ServicesState.Started -> webServerLocalDataSource.bpmFlow.onStart {
                    emit(BpmReading(value = DEFAULT_BPM, sequenceNumber = 0))
                }

                else -> flow { emit(BpmReading(value = DEFAULT_BPM, sequenceNumber = 0)) }
            }
        }
        .distinctUntilChanged()
        .shareIn(
            scope = appScope,
            started = SharingStarted.Eagerly,
            replay = 1
        )

    init {
        // React to state transitions - stop all services when entering Stopping state
        appScope.launch {
            servicesState.collect { state ->
                if (state == ServicesState.Stopping) {
                    logger.d("Auto-stopping all services due to state transition")
                    @Suppress("TooGenericExceptionCaught")
                    try {
                        stopAllServices()
                    } catch (e: Exception) {
                        logger.e("Failed to auto-stop services during state transition", e)
                        // Don't rethrow - we're in a background coroutine
                        // The state machine will handle the error state
                    }
                }
            }
        }

        // Notify Bluetooth when BPM updates (only observes when services started)
        // Double-check ensures we don't notify during state transitions
        appScope.launch {
            bpm.collect { bpmReading ->
                // Only notify when actively serving (prevents race during transitions)
                if (servicesState.value == ServicesState.Started) {
                    if (bpmReading.sequenceNumber > 0) {
                        try {
                            bluetoothLocalDataSource.notifyHeartRate(bpmReading.value)
                        } catch (e: Exception) {
                            logger.e("Failed to notify heart rate: $bpmReading", e)
                            // Don't crash - log and continue
                            // Bluetooth might be disconnected or in error state
                        }
                    }
                }
            }
        }
    }

    // Upstream bpm SharedFlow preserves duplicates for Bluetooth notifications
    override val appStateFlow = combine(
        servicesState.onEach { logger.d("Services state: $it") },
        bluetoothLocalDataSource.clientConnectedState.onEach { logger.d("Client connected: $it") },
        bpm.onEach { logger.d("BPM: $it") }
    ) { servicesState, isClientConnected, bpmReading ->
        AppState(
            servicesState = servicesState,
            isClientConnected = isClientConnected,
            bpm = bpmReading
        )
    }
        .onEach { logger.d("New state: $it") }
        .shareIn(
            scope = appScope,
            started = SharingStarted.Eagerly,
            replay = 1
        )

    override fun getHardwareState() = bluetoothLocalDataSource.getHardwareState()

    override fun getMissingPermissions() = bluetoothLocalDataSource.getMissingPermissions()

    override fun permissionsGranted() = bluetoothLocalDataSource.permissionsGranted()

    override suspend fun startServices(): ServiceResult<Unit> {
        // Check if already running
        if (servicesState.value != ServicesState.Stopped) {
            val state = servicesState.value.toString().lowercase()
            logger.i("Services already $state")
            return ServiceResult.Error(ServiceError.AlreadyInState(state))
        }

        var bluetoothStarted = false

        return try {
            // Start Bluetooth advertising
            logger.d("Starting Bluetooth advertising")
            bluetoothLocalDataSource.startAdvertising()
            bluetoothStarted = true
            logger.i("No errors starting Bluetooth advertising")

            // Start web server
            logger.d("Starting web server")
            webServerLocalDataSource.start()
            logger.i("No errors starting web server")

            ServiceResult.Success(Unit)
        } catch (e: IllegalStateException) {
            logger.e("Bluetooth in invalid state", e)
            rollbackStartup(bluetoothStarted)
            ServiceResult.Error(
                ServiceError.BluetoothError(
                    reason = "Bluetooth unavailable or in invalid state",
                    cause = e
                )
            )
        } catch (e: Exception) {
            logger.e("Error starting services", e)
            rollbackStartup(bluetoothStarted)

            // Check if this is a permission error (SecurityException from Android platform)
            // We can't catch SecurityException directly in commonMain, so check by class name
            val isPermissionError = e::class.simpleName == "SecurityException"

            when {
                isPermissionError -> {
                    ServiceResult.Error(
                        ServiceError.PermissionError(
                            permission = "Bluetooth permissions required",
                            cause = e
                        )
                    )
                }

                bluetoothStarted -> {
                    // BT started, so error is from web server
                    ServiceResult.Error(
                        ServiceError.WebServerError(
                            reason = e.message ?: "Failed to start web server",
                            cause = e
                        )
                    )
                }

                else -> {
                    // BT didn't start
                    ServiceResult.Error(
                        ServiceError.BluetoothError(
                            reason = e.message ?: "Failed to start Bluetooth",
                            cause = e
                        )
                    )
                }
            }
        }
    }

    /**
     * Rolls back any successfully started services if startup fails partway through.
     */
    private suspend fun rollbackStartup(bluetoothStarted: Boolean) {
        if (!bluetoothStarted) {
            logger.d("No services to rollback")
            return
        }

        logger.d("Rolling back service startup")

        try {
            bluetoothLocalDataSource.stopAdvertising()
            logger.d("Successfully rolled back Bluetooth advertising")
        } catch (e: Exception) {
            logger.e("Failed to rollback Bluetooth advertising (manual cleanup may be required)", e)
        }
    }

    override suspend fun stopServices(): ServiceResult<Unit> {
        // Check if already stopped
        if (servicesState.value == ServicesState.Stopped) {
            logger.i("Services already stopped")
            return ServiceResult.Error(ServiceError.AlreadyInState("stopped"))
        }

        logger.d("Stopping all services")
        val errors = mutableListOf<Pair<String, Throwable>>()

        // Try to stop all services, even if some fail
        // Stop fake BPM first (least critical)
        // Note: Fake BPM is user-initiated via toggleFakeBpm(), not started in startServices()
        // We defensively stop it here to ensure clean state when services shut down
        try {
            fakeBpmManager.stop()
            logger.d("Fake BPM manager stopped")
        } catch (e: Exception) {
            logger.e("Failed to stop fake BPM manager", e)
            errors.add("Fake BPM" to e)
        }

        // Stop Bluetooth
        try {
            bluetoothLocalDataSource.stopAdvertising()
            logger.d("Bluetooth advertising stopped")
        } catch (e: Exception) {
            logger.e("Failed to stop Bluetooth advertising", e)
            errors.add("Bluetooth" to e)
        }

        // Stop web server
        try {
            webServerLocalDataSource.stop()
            logger.d("Web server stopped")
        } catch (e: Exception) {
            logger.e("Failed to stop web server", e)
            errors.add("WebServer" to e)
        }

        return if (errors.isEmpty()) {
            logger.i("All services stopped successfully")
            ServiceResult.Success(Unit)
        } else {
            createStopServicesError(errors)
        }
    }

    /**
     * Creates appropriate error result from service stop failures.
     * Categorizes error based on the first failed service.
     */
    private fun createStopServicesError(
        errors: List<Pair<String, Throwable>>
    ): ServiceResult<Unit> {
        val errorMessage = "Failed to stop ${errors.size} service(s): ${errors.joinToString { it.first }}"
        val firstError = errors.first()

        return when {
            firstError.first == "Bluetooth" -> {
                ServiceResult.Error(
                    ServiceError.BluetoothError(
                        reason = errorMessage,
                        cause = firstError.second
                    )
                )
            }

            firstError.first == "WebServer" -> {
                ServiceResult.Error(
                    ServiceError.WebServerError(
                        reason = errorMessage,
                        cause = firstError.second
                    )
                )
            }

            else -> {
                ServiceResult.Error(
                    ServiceError.UnknownError(
                        message = errorMessage,
                        cause = firstError.second
                    )
                )
            }
        }
    }

    /**
     * Internal method to stop all services with defensive error handling.
     * Used by reactive init block for state transitions.
     * Attempts to stop all services even if some fail.
     *
     * Note: Fake BPM is user-initiated (not started in startServices),
     * but defensively stopped here to ensure clean state on shutdown.
     */
    private suspend fun stopAllServices() {
        try {
            fakeBpmManager.stop()
        } catch (e: Exception) {
            logger.e("Failed to stop fake BPM during auto-stop", e)
        }

        try {
            bluetoothLocalDataSource.stopAdvertising()
        } catch (e: Exception) {
            logger.e("Failed to stop Bluetooth during auto-stop", e)
        }

        try {
            webServerLocalDataSource.stop()
        } catch (e: Exception) {
            logger.e("Failed to stop web server during auto-stop", e)
        }
    }

    override fun toggleFakeBpm(): Boolean {
        // Only allow fake BPM when services are started
        if (servicesState.value != ServicesState.Started) return false
        return fakeBpmManager.toggle()
    }
}
