package org.noblecow.hrservice.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.noblecow.hrservice.R
import org.noblecow.hrservice.data.di.IoDispatcher
import org.noblecow.hrservice.data.source.local.AdvertisingState
import org.noblecow.hrservice.data.source.local.BluetoothLocalDataSource
import org.noblecow.hrservice.data.source.local.FakeBpmLocalDataSource
import org.noblecow.hrservice.data.source.local.HardwareState
import org.noblecow.hrservice.data.source.local.WebServerLocalDataSource
import org.noblecow.hrservice.data.source.local.WebServerState
import org.noblecow.hrservice.data.util.DEFAULT_BPM
import org.slf4j.LoggerFactory

internal data class AppState(
    val bpm: Int = DEFAULT_BPM,
    val isClientConnected: Boolean = false,
    val servicesState: ServicesState = ServicesState.Stopped
)

@Suppress("MagicNumber")
internal sealed class ServicesState(val order: Int = -1) {
    data object Starting : ServicesState(0), ServicesTransitionState
    data object Started : ServicesState(1)
    data object Stopping : ServicesState(2), ServicesTransitionState
    data object Stopped : ServicesState(3)
    data class Error(val id: Int) : ServicesState()
}

internal interface ServicesTransitionState

internal interface MainRepository {
    fun getAppStateStream(): StateFlow<AppState>
    fun getHardwareState(): HardwareState
    fun getMissingPermissions(): Array<String>
    fun permissionsGranted(): Boolean
    suspend fun startServices()
    suspend fun stopServices()
    fun toggleFakeBpm()
}

private const val TAG = "MainRepositoryImpl"

@Singleton
internal class MainRepositoryImpl @Inject constructor(
    private val bluetoothLocalDataSource: BluetoothLocalDataSource,
    private val webServerLocalDataSource: WebServerLocalDataSource,
    private val fakeBpmLocalDataSource: FakeBpmLocalDataSource,
    @IoDispatcher dispatcher: CoroutineDispatcher
) : MainRepository {

    private var fakeBpmJob: Job? = null
    private val localScope: CoroutineScope = CoroutineScope(Job() + dispatcher)
    private val logger = LoggerFactory.getLogger(TAG)
    private var startingOrStopping: ServicesTransitionState? = null
    private val startOrStopFlow = MutableSharedFlow<Boolean>()
    private var previousServicesState: ServicesState? = null

    private val servicesState: StateFlow<ServicesState> = combine(
        startOrStopFlow,
        bluetoothLocalDataSource.advertising,
        webServerLocalDataSource.webServerState
    ) { _, advertisingState, webServerState ->
        val newState = getErrorState(advertisingState, webServerState)
            ?: getNormalState(advertisingState, webServerState)
        previousServicesState = newState
        newState
    }
        .stateIn(
            scope = localScope,
            started = SharingStarted.Eagerly,
            initialValue = ServicesState.Stopped
        )

    private val bpm: SharedFlow<Int> = webServerLocalDataSource.bpmStream
        .onEach {
            if (servicesState.value == ServicesState.Started) {
                bluetoothLocalDataSource.notifyHeartRate(it)
            }
        }
        // No need to send duplicates upstream after side-effect
        .stateIn(
            scope = localScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = 0
        )

    private fun getErrorState(
        advertisingState: AdvertisingState,
        webServerState: WebServerState
    ): ServicesState? {
        return when {
            advertisingState is AdvertisingState.Failure ->
                ServicesState.Error(R.string.error_advertise)
            webServerState.error != null ->
                ServicesState.Error(R.string.error_web_server)
            else -> null
        }
    }

    private fun getNormalState(
        advertisingState: AdvertisingState,
        webServerState: WebServerState
    ): ServicesState {
        val partiallyStarted =
            advertisingState == AdvertisingState.Started && !webServerState.isRunning ||
                advertisingState == AdvertisingState.Stopped && webServerState.isRunning

        // Don't change state until both services have started
        return previousServicesState.takeIf {
            it != null && partiallyStarted && it is ServicesTransitionState
        } ?: run {
            if (advertisingState == AdvertisingState.Started && webServerState.isRunning) {
                startingOrStopping?.let {
                    val tmp = startingOrStopping
                    startingOrStopping = null
                    tmp as ServicesState
                } ?: ServicesState.Started
            } else {
                startingOrStopping?.let {
                    val tmp = startingOrStopping
                    startingOrStopping = null
                    tmp as ServicesState
                } ?: ServicesState.Stopped
            }
        }
    }

    override fun getAppStateStream(): StateFlow<AppState> = combine(
        servicesState,
        bluetoothLocalDataSource.clientConnected,
        bpm
    ) { servicesState, isClientConnected, bpm ->
        val newState = AppState(
            servicesState = servicesState,
            isClientConnected = isClientConnected,
            bpm = if (servicesState == ServicesState.Started) bpm else DEFAULT_BPM
        )
        newState
    }
        .stateIn(
            scope = localScope,
            started = SharingStarted.Eagerly,
            initialValue = AppState()
        )

    override fun getHardwareState() = bluetoothLocalDataSource.getHardwareState()

    override fun getMissingPermissions() = bluetoothLocalDataSource.getMissingPermissions()

    override fun permissionsGranted() = bluetoothLocalDataSource.permissionsGranted()

    override suspend fun startServices() {
        try {
            check(servicesState.value == ServicesState.Stopped) { servicesState.value }

            startingOrStopping = ServicesState.Starting
            startOrStopFlow.emit(true)
            bluetoothLocalDataSource.startAdvertising()
            webServerLocalDataSource.start()
        } catch (e: IllegalStateException) {
            logger.error(e.localizedMessage, e)
        }
    }

    override suspend fun stopServices() {
        try {
            check(servicesState.value == ServicesState.Started) { servicesState.value }

            startingOrStopping = ServicesState.Stopping
            startOrStopFlow.emit(true)
            fakeBpmJob?.cancel()
            bluetoothLocalDataSource.stop()
            webServerLocalDataSource.stop()
        } catch (e: IllegalStateException) {
            logger.error(e.localizedMessage, e)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun toggleFakeBpm() {
        fakeBpmJob?.let {
            it.cancel()
            fakeBpmJob = null
        } ?: run {
            if (servicesState.value == ServicesState.Started) {
                fakeBpmJob = localScope.launch {
                    try {
                        fakeBpmLocalDataSource.run()
                    } catch (error: Throwable) {
                        logger.error(error.localizedMessage, error)
                    }
                }
            }
        }
    }
}
