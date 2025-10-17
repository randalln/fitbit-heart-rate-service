package org.noblecow.hrservice.data.repository

import co.touchlab.kermit.Logger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import heartratemonitor.composeapp.generated.resources.Res
import heartratemonitor.composeapp.generated.resources.error_advertise
import heartratemonitor.composeapp.generated.resources.error_web_server
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
import org.jetbrains.compose.resources.StringResource
import org.noblecow.hrservice.data.source.local.AdvertisingState
import org.noblecow.hrservice.data.source.local.BluetoothLocalDataSource
import org.noblecow.hrservice.data.source.local.FakeBpmLocalDataSource
import org.noblecow.hrservice.data.source.local.HardwareState
import org.noblecow.hrservice.data.source.local.WebServerLocalDataSource
import org.noblecow.hrservice.data.source.local.WebServerState
import org.noblecow.hrservice.data.util.DEFAULT_BPM
import org.noblecow.hrservice.di.IoDispatcher

internal data class AppState(
    val bpm: Int = DEFAULT_BPM,
    val isClientConnected: Boolean = false,
    val servicesState: ServicesState = ServicesState.Stopped
)

internal sealed class ServicesState {
    data object Starting : ServicesState(), ServicesTransitionState
    data object Started : ServicesState()
    data object Stopping : ServicesState(), ServicesTransitionState
    data object Stopped : ServicesState()
    data class Error(
        // val id: Int
        val text: StringResource
    ) : ServicesState()
}

internal interface ServicesTransitionState

internal interface MainRepository {
    fun getAppStateStream(): StateFlow<AppState>
    fun getHardwareState(): HardwareState
    fun getMissingPermissions(): Array<out String>
    fun permissionsGranted(): Boolean
    suspend fun startServices()
    fun stopServices()
    fun toggleFakeBpm(): Boolean
}

private const val TAG = "MainRepositoryImpl"

@Suppress("TooManyFunctions")
@ContributesBinding(AppScope::class)
@Inject
internal class MainRepositoryImpl(
    private val bluetoothLocalDataSource: BluetoothLocalDataSource,
    private val webServerLocalDataSource: WebServerLocalDataSource,
    private val fakeBpmLocalDataSource: FakeBpmLocalDataSource,
    @IoDispatcher dispatcher: CoroutineDispatcher
) : MainRepository {

    private var fakeBpmJob: Job? = null
    private val localScope: CoroutineScope = CoroutineScope(Job() + dispatcher)

    // private val logger = LoggerFactory.getLogger(TAG)
    private val logger = Logger.withTag(TAG)
    private val toggleSignalFlow = MutableSharedFlow<Boolean>()
    private var previousServicesState: ServicesState? = null

    private val servicesState: StateFlow<ServicesState> = combine(
        toggleSignalFlow,
        bluetoothLocalDataSource.advertisingState,
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
    ): ServicesState? = when {
        advertisingState is AdvertisingState.Failure ->
            ServicesState.Error(Res.string.error_advertise)

        webServerState.error != null ->
            ServicesState.Error(Res.string.error_web_server)

        else -> null
    }

    private fun getNormalState(
        advertisingState: AdvertisingState,
        webServerState: WebServerState
    ): ServicesState {
        val newState = if (advertisingState == AdvertisingState.Stopped &&
            !webServerState.isRunning
        ) {
            ServicesState.Stopped
        } else if (advertisingState == AdvertisingState.Started && webServerState.isRunning) {
            ServicesState.Started
        } else {
            previousServicesState?.let {
                when (it) {
                    ServicesState.Started -> ServicesState.Stopping
                    ServicesState.Stopped -> ServicesState.Starting
                    else -> null
                }
            } ?: ServicesState.Starting
        }

        if (newState == ServicesState.Stopping) {
            localScope.launch {
                stopAllServices()
            }
        }

        return newState
    }

    override fun getAppStateStream(): StateFlow<AppState> = combine(
        servicesState,
        bluetoothLocalDataSource.clientConnectedState,
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
            toggleSignalFlow.emit(true)
            bluetoothLocalDataSource.startAdvertising()
            webServerLocalDataSource.start()
        } catch (e: IllegalStateException) {
            // logger.error(e.localizedMessage, e)
            logger.e(e.message ?: "", e)
        }
    }

    override fun stopServices() {
        try {
            check(servicesState.value == ServicesState.Started) { servicesState.value }
            stopAllServices()
        } catch (e: IllegalStateException) {
            // logger.error(e.localizedMessage, e)
            logger.e(e.message ?: "", e)
        }
    }

    private fun stopAllServices() {
        fakeBpmJob?.cancel()
        bluetoothLocalDataSource.stop()
        webServerLocalDataSource.stop()
    }

    override fun toggleFakeBpm() = startFakeBpm() || stopFakeBpm()

    @Suppress("TooGenericExceptionCaught")
    private fun startFakeBpm(): Boolean = if (fakeBpmJob == null && servicesState.value == ServicesState.Started) {
        fakeBpmJob = localScope.launch {
            try {
                fakeBpmLocalDataSource.run()
            } catch (e: Throwable) {
                // logger.error(error.localizedMessage, error)
                logger.e(e.message ?: "", e)
            }
        }
        true
    } else {
        false
    }

    private fun stopFakeBpm(): Boolean = fakeBpmJob?.let {
        it.cancel()
        fakeBpmJob = null
        true
    } == true
}
