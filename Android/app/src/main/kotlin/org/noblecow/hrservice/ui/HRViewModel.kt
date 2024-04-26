package org.noblecow.hrservice.ui

import android.annotation.SuppressLint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import kotlinx.serialization.Serializable
import org.noblecow.hrservice.data.AdvertisingState
import org.noblecow.hrservice.data.BluetoothRepository
import org.noblecow.hrservice.data.HardwareState
import org.noblecow.hrservice.data.WebServer
import org.slf4j.LoggerFactory

internal const val PORT_LISTEN = 12345
internal const val FAKE_BPM_START = 60
private const val FAKE_BPM_END = 70

@HiltViewModel
@Suppress("TooManyFunctions")
internal class HRViewModel @Inject constructor(
    private val bluetoothRepository: BluetoothRepository,
    private val webServer: WebServer
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var advertisingJob: Job? = null
    private var clientConnectionJob: Job? = null
    private var webServerJob: Job? = null
    private var fakeBpmJob: Job? = null

    private var clientConnected = false
    private val logger = LoggerFactory.getLogger(HRViewModel::class.simpleName)

    fun start() {
        confirmPermissions()
    }

    private fun confirmPermissions() {
        if (!bluetoothRepository.permissionsGranted()) {
            val permissionsNeeded = bluetoothRepository.getMissingPermissions()
            transitionState(UiState.RequestPermissions(permissionsNeeded.toList()))
        } else {
            confirmBluetoothState()
        }
    }

    fun receivePermissions(permissions: Map<String, Boolean>) {
        if (permissions.containsValue(false)) {
            transitionState(UiState.Error(GeneralError.PermissionsDenied()))
        } else {
            confirmBluetoothState()
        }
    }

    private fun confirmBluetoothState() {
        val bleState = bluetoothRepository.getHardwareState()
        if (bleState == HardwareState.HARDWARE_UNSUITABLE) {
            transitionState(UiState.Error(GeneralError.BleHardware))
        } else {
            if (bleState == HardwareState.READY) {
                logger.debug("Bluetooth enabled...starting services")
                startServices()
            } else {
                transitionState(UiState.RequestEnableBluetooth)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startServices() {
        if (advertisingJob == null) {
            advertisingJob = viewModelScope.launch {
                bluetoothRepository.getAdvertisingFlow()
                    .distinctUntilChanged()
                    .collect {
                        when (it) {
                            AdvertisingState.Started -> onAdvertisingStarted()
                            AdvertisingState.Stopped -> onAdvertisingStopped()
                            is AdvertisingState.Failure -> {
                                transitionState(UiState.Error(GeneralError.BtAdvertise))
                                onAdvertisingStopped()
                            }
                        }
                    }
            }
        }
        if (clientConnectionJob == null) {
            clientConnectionJob = viewModelScope.launch {
                bluetoothRepository.getClientConnectionFlow().collect { connected ->
                    clientConnected = connected
                    if (_uiState.value is UiState.AwaitingClient ||
                        _uiState.value is UiState.ClientConnected
                    ) {
                        displayBPM(_uiState.value.bpm)
                    }
                }
            }
        }
        if (webServerJob == null) {
            webServerJob = viewModelScope.launch {
                webServer.webServerState.collect { state ->
                    if (state.error != null) {
                        transitionState(UiState.Error(state.error))
                        stop()
                    } else if (state.running) {
                        bluetoothRepository.notifyHeartRate(state.bpm)
                        displayBPM(state.bpm)
                    }
                }
            }
        }

        bluetoothRepository.startAdvertising()
    }

    private fun onAdvertisingStarted() {
        webServer.start()
    }

    private fun displayBPM(bpm: Int) {
        transitionState(
            if (clientConnected) {
                UiState.ClientConnected(bpm = bpm)
            } else {
                UiState.AwaitingClient(bpm)
            }
        )
    }

    /**
     * Tell the BLESSED library to stop, which in turn shuts down everything else
     */
    internal fun stop() {
        bluetoothRepository.stop()
    }

    private fun onAdvertisingStopped() {
        advertisingJob?.let {
            it.cancel()
            advertisingJob = null
        }
        clientConnectionJob?.let {
            it.cancel()
            clientConnectionJob = null
        }
        fakeBpmJob?.let {
            it.cancel()
            fakeBpmJob = null
        }
        webServerJob?.let {
            webServer.stop()
            webServerJob = null
        }

        transitionState(UiState.Idle())
    }

    internal fun userDeclinedBluetoothEnable() = transitionState(UiState.Idle())

    internal fun toggleFakeBPM() {
        fakeBpmJob?.let {
            it.cancel()
            fakeBpmJob = null
            displayBPM(0)
        } ?: run {
            fakeBpmJob = viewModelScope.launch {
                getFakeBPMFlow()
                    .catch {
                        logger.debug(it.localizedMessage, it)
                    }
                    .collect {
                        val client = HttpClient(Android) {
                            install(ClientContentNegotiation) {
                                json()
                            }
                        }
                        client.post("http://localhost:$PORT_LISTEN") {
                            contentType(ContentType.Application.Json)
                            setBody(Request(it))
                        }
                    }
            }
        }
    }

    private suspend fun getFakeBPMFlow(): Flow<Int> = flow {
        var bpm = FAKE_BPM_START
        while (true) {
            if (bpm > FAKE_BPM_END) {
                bpm = FAKE_BPM_START
            }
            emit(bpm++)
            delay(Duration.ofSeconds(1))
        }
    }

    private fun transitionState(newUiState: UiState) {
        logger.debug("Transitioning to new state: {}", newUiState)
        _uiState.update {
            newUiState
        }
    }
}

@Serializable
internal data class Request(val bpm: Int)
