package org.noblecow.hrservice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import heartratemonitor.composeapp.generated.resources.Res
import heartratemonitor.composeapp.generated.resources.permissions_denied
import kotlin.time.TimeSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.noblecow.hrservice.data.repository.MainRepository
import org.noblecow.hrservice.data.repository.ServiceResult
import org.noblecow.hrservice.data.repository.ServicesState
import org.noblecow.hrservice.data.repository.toMessage
import org.noblecow.hrservice.data.util.DEFAULT_BPM
import org.noblecow.hrservice.data.util.ResourceHelper
import org.noblecow.hrservice.domain.usecase.StartServiceResult
import org.noblecow.hrservice.domain.usecase.StartServicesUseCase

private const val STOP_TIMEOUT_MILLIS = 5000L
private const val MIN_TRANSITION_STATE_DURATION_MS = 1000L
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

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions")
@ContributesIntoMap(AppScope::class)
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

        // Transform appStateFlow to ensure transition states (Starting/Stopping) are displayed
        // for at least MIN_TRANSITION_STATE_DURATION_MS
        data class StateWithTimestamp(
            val state: org.noblecow.hrservice.data.repository.AppState,
            val entryMark: kotlin.time.TimeMark
        )

        data class StateTransition(
            val current: StateWithTimestamp,
            val previous: StateWithTimestamp?
        )

        val delayedAppStateFlow = mainRepository.appStateFlow
            .runningFold<org.noblecow.hrservice.data.repository.AppState, StateTransition?>(
                null
            ) { transition, newState ->
                StateTransition(
                    current = StateWithTimestamp(newState, TimeSource.Monotonic.markNow()),
                    previous = transition?.current
                )
            }
            .filterNotNull()
            .onEach { transition ->
                // If previous was a transition state, ensure it was displayed for minimum duration
                val previous = transition.previous
                if (previous != null) {
                    val wasTransitionState = previous.state.servicesState is ServicesState.Starting ||
                        previous.state.servicesState is ServicesState.Stopping

                    if (wasTransitionState) {
                        val elapsed = previous.entryMark.elapsedNow().inWholeMilliseconds
                        val remaining = MIN_TRANSITION_STATE_DURATION_MS - elapsed
                        if (remaining > 0) {
                            logger.d("Delaying transition from ${previous.state.servicesState} by ${remaining}ms")
                            delay(remaining)
                        }
                    }
                }
            }
            .shareIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                replay = 1
            )
            .also { stateTransitionFlow ->
                // Separate logging flow that only logs when services state changes
                stateTransitionFlow
                    .map { it.current.state.servicesState }
                    .distinctUntilChanged()
                    .onEach { logger.d("Services state changed: $it") }
                    .launchIn(viewModelScope)
            }
            .map { it.current.state }

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
            delayedAppStateFlow
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
        logger.d("Stop button tapped - launching coroutine")
        viewModelScope.launch {
            logger.d("Coroutine started - calling repository.stopServices()")
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
            logger.d("stop() coroutine completed")
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
