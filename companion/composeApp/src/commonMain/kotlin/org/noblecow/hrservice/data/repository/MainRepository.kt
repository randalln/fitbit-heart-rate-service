package org.noblecow.hrservice.data.repository

import co.touchlab.kermit.Logger
import co.touchlab.kermit.loggerConfigInit
import co.touchlab.kermit.platformLogWriter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.noblecow.hrservice.data.source.local.BluetoothLocalDataSource
import org.noblecow.hrservice.data.source.local.FakeBpmLocalDataSource
import org.noblecow.hrservice.data.source.local.HardwareState
import org.noblecow.hrservice.data.source.local.WebServerLocalDataSource
import org.noblecow.hrservice.data.util.DEFAULT_BPM

data class AppState(
    val bpm: Int = DEFAULT_BPM,
    val isClientConnected: Boolean = false,
    val servicesState: ServicesState = ServicesState.Stopped
)

interface MainRepository {
    val appStateFlow: StateFlow<AppState>
    fun getHardwareState(): HardwareState
    fun getMissingPermissions(): Array<out String>
    fun permissionsGranted(): Boolean
    suspend fun startServices()
    suspend fun stopServices()
    fun toggleFakeBpm(): Boolean
}

private const val TAG = "MainRepositoryImpl"

@Suppress("TooManyFunctions")
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
internal class MainRepositoryImpl(
    private val bluetoothLocalDataSource: BluetoothLocalDataSource,
    private val webServerLocalDataSource: WebServerLocalDataSource,
    private val fakeBpmLocalDataSource: FakeBpmLocalDataSource,
    private val appScope: CoroutineScope,
    private val logger: Logger = Logger(loggerConfigInit(platformLogWriter()), TAG)
) : MainRepository {

    private var fakeBpmJob: Job? = null
    private val toggleSignalFlow = MutableSharedFlow<Boolean>()
    private val stateMachine = ServicesStateMachine()

    private val servicesState: StateFlow<ServicesState> = combine(
        toggleSignalFlow.onStart { emit(false) },
        bluetoothLocalDataSource.advertisingState,
        webServerLocalDataSource.webServerState
    ) { _, advertisingState, webServerState ->
        Pair(advertisingState, webServerState)
    }
        .scan(ServicesState.Stopped as ServicesState) { previousState, (advertisingState, webServerState) ->
            stateMachine.nextState(previousState, advertisingState, webServerState)
        }
        .stateIn(
            scope = appScope,
            started = SharingStarted.Eagerly,
            initialValue = ServicesState.Stopped
        )

    init {
        // React to state transitions - stop all services when entering Stopping state
        appScope.launch {
            servicesState.collect { state ->
                if (state == ServicesState.Stopping) {
                    logger.d("Auto-stopping all services due to state transition")
                    stopAllServices()
                }
            }
        }
    }

    private val bpm: SharedFlow<Int> = combine(
        webServerLocalDataSource.bpmFlow.onStart { emit(0) },
        servicesState
    ) { bpm, state ->
        if (state == ServicesState.Started) {
            bluetoothLocalDataSource.notifyHeartRate(bpm)
        }
        bpm
    }
        .shareIn(
            scope = appScope,
            started = SharingStarted.Eagerly,
            replay = 1
        )

    // var stateCounter = 0 // This was useful for debugging
    override val appStateFlow = combine(
        servicesState,
        bluetoothLocalDataSource.clientConnectedState,
        bpm
    ) { servicesState, isClientConnected, bpm ->
        AppState(
            servicesState = servicesState,
            isClientConnected = isClientConnected,
            bpm = if (servicesState == ServicesState.Started) bpm else DEFAULT_BPM
            // counter = stateCounter++
        )
    }
        .stateIn(
            scope = appScope,
            started = SharingStarted.Eagerly,
            initialValue = AppState()
        )

    override fun getHardwareState() = bluetoothLocalDataSource.getHardwareState()

    override fun getMissingPermissions() = bluetoothLocalDataSource.getMissingPermissions()

    override fun permissionsGranted() = bluetoothLocalDataSource.permissionsGranted()

    override suspend fun startServices() {
        if (servicesState.value == ServicesState.Stopped) {
            toggleSignalFlow.emit(true)
            bluetoothLocalDataSource.startAdvertising()
            webServerLocalDataSource.start()
        } else {
            logger.i("Services already started")
        }
    }

    override suspend fun stopServices() {
        if (servicesState.value != ServicesState.Stopped) {
            stopAllServices()
        } else {
            logger.i("Services already stopped")
        }
    }

    private suspend fun stopAllServices() {
        fakeBpmJob?.cancel()
        bluetoothLocalDataSource.stopAdvertising()
        webServerLocalDataSource.stop()
    }

    override fun toggleFakeBpm() = startFakeBpm() || stopFakeBpm()

    @Suppress("TooGenericExceptionCaught")
    private fun startFakeBpm(): Boolean = if (fakeBpmJob == null && servicesState.value == ServicesState.Started) {
        fakeBpmJob = appScope.launch {
            try {
                fakeBpmLocalDataSource.run()
            } catch (e: Throwable) {
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
