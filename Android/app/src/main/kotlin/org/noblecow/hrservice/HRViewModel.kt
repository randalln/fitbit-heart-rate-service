package org.noblecow.hrservice

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.pm.PackageManager
import android.util.Log
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import kotlinx.serialization.Serializable
import org.noblecow.hrservice.utils.BluetoothHelper
import org.noblecow.hrservice.utils.PermissionsHelper

private const val PORT_LISTEN = 12345
private const val TAG = "HRViewModel"
internal const val FAKE_BPM = 60
private const val FAKE_BPM_MAX = 10

@HiltViewModel
@Suppress("TooManyFunctions")
class HRViewModel @Inject constructor(
    private val bluetoothHelper: BluetoothHelper,
    private val permissionsHelper: PermissionsHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var advertisingJob: Job? = null
    private var bluetoothReceiverJob: Job? = null
    private var fakeBPMJob: Job? = null
    private var gattServerJob: Job? = null
    private var ktorServer: BaseApplicationEngine? = null

    private var currentRequest: Request? = null

    internal fun confirmPermissions() {
        val permissionsNeeded = bluetoothHelper.permissionsRequired.filter { permission ->
            permissionsHelper.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsNeeded.isNotEmpty()) {
            transitionState(UiState.RequestPermissions(permissionsNeeded))
        } else {
            receivePermissions(emptyMap())
        }
    }

    internal fun receivePermissions(permissions: Map<String, Boolean>) {
        if (permissions.containsValue(false)) {
            transitionState(UiState.Error(HeartRateError.PermissionsDenied()))
        } else {
            val bleState = bluetoothHelper.getBLEState()
            if (bleState == BluetoothHelper.BLEState.HARDWARE_UNSUITABLE) {
                transitionState(UiState.Error(HeartRateError.BleHardware))
            } else {
                if (bluetoothReceiverJob == null) {
                    bluetoothReceiverJob = viewModelScope.launch {
                        bluetoothHelper.getBluetoothReceiverFlow().collect { state ->
                            when (state) {
                                BluetoothAdapter.STATE_ON -> {
                                    Log.d(TAG, "Bluetooth just enabled...starting services")
                                    startServices()
                                }

                                BluetoothAdapter.STATE_OFF -> {
                                    Log.d(TAG, "Bluetooth just disabled...stopping services")
                                    stopServices()
                                }
                            }
                        }
                    }
                }

                if (bleState == BluetoothHelper.BLEState.READY) {
                    Log.d(TAG, "Bluetooth enabled...starting services")
                    startServices()
                } else {
                    transitionState(UiState.RequestEnableBluetooth)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startServices() {
        advertisingJob = viewModelScope.launch {
            bluetoothHelper.getAdvertisingFlow().collect {
                transitionState(UiState.Error(HeartRateError.BtAdvertise))
                stopServices()
            }
        }
        gattServerJob = viewModelScope.launch {
            bluetoothHelper.getGattServerFlow().collect { device ->
                device?.let {
                    Log.d(TAG, "gattServerFlow: ${device.address} ${device.type}")
                }
            }
        }

        startKtorServer()
        transitionState(
            if (bluetoothHelper.registeredDevices.size > 0) {
                UiState.ClientConnected(0)
            } else {
                UiState.AwaitingClient(0)
            }
        )
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
                        bluetoothHelper.notifyRegisteredDevices(this.bpm)
                        call.respond(this)
                    }
                }
            }
        }.start(wait = false)
    }

    private fun handleKtorError(e: Throwable) {
        val message = e.localizedMessage ?: e::javaClass.name
        Log.e(TAG, message, e)
        transitionState(UiState.Error(HeartRateError.Ktor(message)))
        stopServices(updateUI = false)
    }

    private fun displayBPM(bpm: Int) {
        transitionState(
            if (bluetoothHelper.registeredDevices.size > 0) {
                UiState.ClientConnected(
                    bpm = bpm,
                    sendingFakeBPM = fakeBPMJob != null
                )
            } else {
                UiState.AwaitingClient(
                    bpm,
                    sendingFakeBPM = fakeBPMJob != null
                )
            }
        )
    }

    internal fun stopServices(updateUI: Boolean = true) {
        fakeBPMJob?.let {
            it.cancel()
            fakeBPMJob = null
        }
        advertisingJob?.let {
            it.cancel()
            advertisingJob = null
        }
        bluetoothReceiverJob?.let {
            it.cancel()
            bluetoothReceiverJob = null
        }
        gattServerJob?.let {
            it.cancel()
            gattServerJob = null
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
        fakeBPMJob?.let {
            it.cancel()
            fakeBPMJob = null
            displayBPM(0)
        } ?: run {
            fakeBPMJob = viewModelScope.launch {
                getFakeBPMFlow()
                    .catch {
                        Log.d(TAG, it.localizedMessage, it)
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
        var bpm = FAKE_BPM
        while (true) {
            if (bpm > FAKE_BPM + FAKE_BPM_MAX) {
                bpm = FAKE_BPM
            }
            emit(bpm++)
            delay(Duration.ofSeconds(1))
        }
    }

    private fun transitionState(newUiState: UiState) {
        Log.d(TAG, "Transitioning to new state: ${newUiState::class.java}")
        _uiState.update {
            newUiState
        }
    }
}

@Serializable
private data class Request(val bpm: Int)

@Serializable
private data class Response(val status: String)
