package org.noblecow.hrservice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import heartratemonitor.composeapp.generated.resources.Res
import heartratemonitor.composeapp.generated.resources.permissions_denied
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.noblecow.hrservice.data.repository.MainRepository
import org.noblecow.hrservice.data.repository.ServiceResult
import org.noblecow.hrservice.data.repository.ServicesState
import org.noblecow.hrservice.data.repository.toMessage
import org.noblecow.hrservice.data.util.DEFAULT_BPM
import org.noblecow.hrservice.data.util.ResourceHelper
import org.noblecow.hrservice.di.ViewModelScope
import org.noblecow.hrservice.domain.usecase.StartServiceResult
import org.noblecow.hrservice.domain.usecase.StartServicesUseCase

private const val STOP_TIMEOUT_MILLIS = 5000L
private const val TAG = "MainViewModel"

internal data class MainUiState(
    val bpm: Int = DEFAULT_BPM,
    val bpmCount: Int = 0,
    val bluetoothRequested: Boolean? = null,
    val isClientConnected: Boolean = false,
    val permissionsRequested: List<String>? = null,
    val servicesState: ServicesState = ServicesState.Stopped,
    val userMessage: String? = null
)

@Suppress("TooManyFunctions")
@ContributesIntoMap(ViewModelScope::class)
@ViewModelKey(MainViewModel::class)
@Inject
internal class MainViewModel(
    private val mainRepository: MainRepository,
    private val startServicesUseCase: StartServicesUseCase,
    private val resourceHelper: ResourceHelper,
    defaultLogger: Logger
) : ViewModel() {
    private val logger = defaultLogger.withTag(TAG)

    val mainUiState: StateFlow<MainUiState>

    private val permissionsRequested: MutableStateFlow<List<String>?>
    private val enableBluetooth: MutableStateFlow<Boolean?>
    private val userMessage: MutableStateFlow<String?>

    init {
        // Use model defaults as SSOT
        MainUiState().also {
            permissionsRequested = MutableStateFlow(it.permissionsRequested)
            enableBluetooth = MutableStateFlow(it.bluetoothRequested)
            userMessage = MutableStateFlow(it.userMessage)
        }

        mainUiState = combine(
            permissionsRequested.onEach {
                logger.d("permissionsRequested: $it")
            },
            enableBluetooth.onEach {
                logger.d("enableBluetooth: $it")
            },
            userMessage.onEach {
                logger.d("userMessage: $it")
            },
            mainRepository.appStateFlow
        ) {
                permissionsRequestedValue,
                bluetoothEnabled,
                userMessageValue,
                appState
            ->
            MainUiState(
                bpm = appState.bpm.value,
                bpmCount = appState.bpm.sequenceNumber,
                bluetoothRequested = bluetoothEnabled,
                isClientConnected = appState.isClientConnected,
                permissionsRequested = permissionsRequestedValue,
                servicesState = appState.servicesState,
                userMessage = userMessageValue
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = MainUiState()
        )
    }

    fun start() {
        if (enableBluetooth.value == false) {
            enableBluetooth.update { null }
        }
        viewModelScope.launch {
            handleStartServiceResult(startServicesUseCase())
        }
    }

    fun stop() {
        viewModelScope.launch {
            when (val result = mainRepository.stopServices()) {
                is ServiceResult.Success -> {
                    logger.d("Initiated stop services")
                }

                is ServiceResult.Error -> {
                    logger.e("Failed to stop services: ${result.error.toMessage()}")
                    // Show error message to user if not already stopped
                    if (result.error !is org.noblecow.hrservice.data.repository.ServiceError.AlreadyInState) {
                        userMessage.update { result.error.toMessage() }
                    }
                }
            }
        }
    }

    fun receivePermissions(permissions: Map<String, Boolean>) {
        if (permissionsRequested.value != null) {
            permissionsRequested.update { null }
        }
        if (permissions.containsValue(false)) {
            viewModelScope.launch {
                userMessage.update { resourceHelper.getString(Res.string.permissions_denied) }
            }
        } else {
            // Re-attempt start after permissions granted
            start()
        }
    }

    private fun handleStartServiceResult(result: StartServiceResult) {
        logger.d("Start service result: $result")
        when (result) {
            is StartServiceResult.Starting -> {
                if (enableBluetooth.value == true) {
                    enableBluetooth.update { null }
                }
            }

            is StartServiceResult.BluetoothDisabled -> {
                logger.d("Bluetooth disabled: requesting enable")
                enableBluetooth.update { true }
            }

            is StartServiceResult.PermissionsNeeded -> {
                logger.d("Permissions needed: ${result.permissions}")
                permissionsRequested.update { result.permissions }
            }

            is StartServiceResult.HardwareError -> {
                logger.d("Hardware error: ${result.message}")
                userMessage.update { result.message }
            }
        }
    }

    fun toggleFakeBPM() = mainRepository.toggleFakeBpm()

    fun userDeclinedBluetoothEnable() = enableBluetooth.update { false }

    fun userMessageShown() = userMessage.update { null }
}
