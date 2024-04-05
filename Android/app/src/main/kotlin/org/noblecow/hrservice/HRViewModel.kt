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
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.noblecow.hrservice.utils.BluetoothHelper
import org.noblecow.hrservice.utils.PermissionsHelper

private const val PORT_LISTEN = 12345
private const val TAG = "HRViewModel"

@HiltViewModel
class HRViewModel @Inject constructor(
    private val bluetoothHelper: BluetoothHelper,
    private val permissionsHelper: PermissionsHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var advertisingJob: Job? = null
    private var bluetoothReceiverJob: Job? = null
    private var gattServerJob: Job? = null
    private var ktorServer: BaseApplicationEngine? = null

    internal fun confirmPermissions() {
        val permissionsNeeded = bluetoothHelper.permissionsRequired.filter { permission ->
            permissionsHelper.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsNeeded.isNotEmpty()) {
            _uiState.update {
                UiState.RequestPermissions(permissionsNeeded)
            }
        } else {
            receivePermissions(emptyMap())
        }
    }

    internal fun receivePermissions(permissions: Map<String, Boolean>) {
        if (permissions.containsValue(false)) {
            _uiState.update {
                UiState.Error(HeartRateError.PermissionsDenied())
            }
        } else {
            val bleState = bluetoothHelper.getBLEState()
            if (bleState == BluetoothHelper.BLEState.HARDWARE_UNSUITABLE) {
                _uiState.update {
                    UiState.Error(HeartRateError.BleHardware)
                }
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
                    _uiState.update {
                        UiState.RequestEnableBluetooth
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startServices() {
        advertisingJob = viewModelScope.launch {
            bluetoothHelper.getAdvertisingFlow().collect {
                _uiState.update {
                    UiState.Error(HeartRateError.BtAdvertise)
                }
            }
        }
        gattServerJob = viewModelScope.launch {
            bluetoothHelper.getGattServerFlow().collect { device ->
                device?.let {
                    Log.d(TAG, "serverFlow: ${device.name} ${device.address} ${device.type}")
                }
            }
        }

        startKtorServer()
        _uiState.update {
            if (bluetoothHelper.registeredDevices.size > 0) {
                UiState.ClientConnected(0)
            } else {
                UiState.AwaitingClient(0)
            }
        }
    }

    private fun startKtorServer() {
        ktorServer = embeddedServer(Netty, PORT_LISTEN) {
            install(StatusPages) {
                exception<Throwable> { _, e ->
                    val message = e.localizedMessage ?: e::javaClass.name
                    Log.e(TAG, message, e)
                    _uiState.update {
                        UiState.Error(HeartRateError.Ktor(message))
                    }
                    stopServices(HeartRateError.Ktor(message))
                }
            }
            install(ContentNegotiation) {
                json()
            }
            routing {
                get("/") {
                    call.respond(Response(status = "OK"))
                }

                post("/") {
                    val request = call.receive<Request>()
                    Log.d(TAG, "Received POST request: $request")
                    _uiState.update {
                        if (bluetoothHelper.registeredDevices.size > 0) {
                            UiState.ClientConnected(request.bpm)
                        } else {
                            UiState.AwaitingClient(request.bpm)
                        }
                    }
                    bluetoothHelper.notifyRegisteredDevices(request.bpm)
                    call.respond(request)
                }
            }
        }.start(wait = false)
    }

    internal fun stopServices(error: HeartRateError? = null) {
        advertisingJob?.cancel()
        advertisingJob = null
        bluetoothReceiverJob?.cancel()
        bluetoothReceiverJob = null
        gattServerJob?.cancel()
        gattServerJob = null
        ktorServer?.stop()
        ktorServer = null

        if (error == null) {
            _uiState.update {
                UiState.Idle()
            }
        }
    }

    internal fun userDeclinedBluetoothEnable() {
        _uiState.update {
            UiState.Idle()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    internal fun sendFakeBPM(bpm: Int) {
        viewModelScope.launch {
            val client = HttpClient(Android) {
                install(ClientContentNegotiation) {
                    json(
                        Json {
                            prettyPrint = true
                        }
                    )
                }
            }
            try {
                client.post("http://localhost:$PORT_LISTEN") {
                    contentType(ContentType.Application.Json)
                    setBody(Request(bpm))
                }
            } catch (e: Exception) {
                Log.d(TAG, e.localizedMessage, e)
            }
        }
    }
}

@Serializable
private data class Request(val bpm: Int)

@Serializable
private data class Response(val status: String)
