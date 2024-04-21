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
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.BaseApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
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
import org.noblecow.hrservice.BuildConfig
import org.noblecow.hrservice.data.AdvertisingState
import org.noblecow.hrservice.data.BluetoothRepository
import org.noblecow.hrservice.data.HardwareState
import org.slf4j.LoggerFactory

internal const val PORT_LISTEN = 12345
internal const val FAKE_BPM_START = 60
private const val FAKE_BPM_END = 70

@HiltViewModel
@Suppress("TooManyFunctions")
internal class HRViewModel @Inject constructor(
    private val bluetoothRepository: BluetoothRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var advertisingJob: Job? = null
    private var clientConnectionJob: Job? = null
    private var fakeBpmJob: Job? = null
    private var ktorServer: BaseApplicationEngine? = null
    private var clientConnected = false
    private var currentRequest: Request? = null
    private val logger = LoggerFactory.getLogger(javaClass.simpleName)

    fun confirmPermissions() {
        if (!bluetoothRepository.permissionsGranted()) {
            val permissionsNeeded = bluetoothRepository.getMissingPermissions()
            transitionState(UiState.RequestPermissions(permissionsNeeded.toList()))
        } else {
            receivePermissions(emptyMap())
        }
    }

    fun receivePermissions(permissions: Map<String, Boolean>) {
        if (permissions.containsValue(false)) {
            transitionState(UiState.Error(GeneralError.PermissionsDenied()))
        } else {
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
    }

    @SuppressLint("MissingPermission")
    private fun startServices() {
        if (!bluetoothRepository.isInitialized) {
            bluetoothRepository.initialize()
        }
        if (advertisingJob == null) {
            advertisingJob = viewModelScope.launch {
                bluetoothRepository.getAdvertisingFlow()
                    .distinctUntilChanged()
                    .collect {
                        when (it) {
                            AdvertisingState.Started -> startKtorServer() // 2. Start web server
                            AdvertisingState.Stopped -> stopServices()
                            is AdvertisingState.Failure -> {
                                transitionState(UiState.Error(GeneralError.BtAdvertise))
                                stopServices()
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

        bluetoothRepository.startAdvertising() // 1. Start advertising
    }

    private fun startKtorServer() {
        ktorServer = embeddedServer(Netty, PORT_LISTEN) {
            install(StatusPages) {
                exception<Throwable> { _, e ->
                    handleKtorError(e)
                }
            }
            install(ContentNegotiation) {
                json()
            }
            if (BuildConfig.DEBUG) {
                install(CallLogging) {
                    format {
                        "Received POST request: ${currentRequest?.bpm}"
                    }
                }
            }
            routing {
                get("/") {
                    call.respond(Response(status = "OK"))
                }

                post("/") {
                    call.receive<Request>().run {
                        currentRequest = this
                        displayBPM(this.bpm)
                        bluetoothRepository.notifyHeartRate(this.bpm)
                        call.respond(this)
                    }
                }
            }
        }.start(wait = false)

        displayBPM(0)
    }

    private fun handleKtorError(e: Throwable) {
        val message = e.localizedMessage ?: e::javaClass.name
        logger.error(message, e)
        transitionState(UiState.Error(GeneralError.Ktor(message)))
        stopServices(updateUI = false)
    }

    private fun displayBPM(bpm: Int) {
        transitionState(
            if (clientConnected) {
                UiState.ClientConnected(
                    bpm = bpm,
                    sendingFakeBPM = fakeBpmJob != null
                )
            } else {
                UiState.AwaitingClient(
                    bpm,
                    sendingFakeBPM = fakeBpmJob != null
                )
            }
        )
    }

    internal fun stopServices(updateUI: Boolean = true) {
        fakeBpmJob?.let {
            it.cancel()
            fakeBpmJob = null
        }
        ktorServer?.let {
            it.stop()
            ktorServer = null
        }

        if (updateUI) {
            transitionState(UiState.Idle())
        }
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

@Serializable
private data class Response(val status: String)
