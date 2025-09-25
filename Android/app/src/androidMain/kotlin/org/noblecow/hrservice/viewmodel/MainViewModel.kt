package org.noblecow.hrservice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.noblecow.hrservice.R
import org.noblecow.hrservice.data.repository.MainRepository
import org.noblecow.hrservice.data.repository.ServicesState
import org.noblecow.hrservice.data.source.local.HardwareState
import org.noblecow.hrservice.data.util.DEFAULT_BPM
import org.noblecow.hrservice.data.util.PermissionsHelper
import org.noblecow.hrservice.di.ViewModelScope
import org.slf4j.LoggerFactory

internal data class MainUiState(
    val bpm: Int = DEFAULT_BPM,
    val bpmCount: Int = 0,
    val bluetoothRequested: Boolean? = null,
    val isClientConnected: Boolean = false,
    val permissionsRequested: List<String>? = null,
    val servicesState: ServicesState = ServicesState.Stopped,
    val startAndroidService: Boolean = false,
    val userMessage: Int? = null
)

private const val STOP_TIMEOUT_MILLIS = 5000L
private const val TAG = "MainViewModel"

@Suppress("TooManyFunctions")
@ContributesIntoMap(ViewModelScope::class)
@ViewModelKey(MainViewModel::class)
@Inject
internal class MainViewModel(
    private val mainRepository: MainRepository,
    private val permissionsHelper: PermissionsHelper
) : ViewModel() {

    val mainUiState: StateFlow<MainUiState>

    private var bpmCount = 0
    private val permissionsRequested: MutableStateFlow<List<String>?>
    private val enableBluetooth: MutableStateFlow<Boolean?>
    private val startAndroidServiceFlow: MutableStateFlow<Boolean>
    private val userMessage: MutableStateFlow<Int?>

    private val logger = LoggerFactory.getLogger(TAG)

    init {
        // Use model defaults as SSOT
        MainUiState().also {
            permissionsRequested = MutableStateFlow(it.permissionsRequested)
            enableBluetooth = MutableStateFlow(it.bluetoothRequested)
            startAndroidServiceFlow = MutableStateFlow(it.startAndroidService)
            userMessage = MutableStateFlow(it.userMessage)
        }

        mainUiState = combine(
            permissionsRequested,
            enableBluetooth,
            userMessage,
            mainRepository.getAppStateStream().onEach { bpmCount++ },
            startAndroidServiceFlow
        ) { permissionsRequested, bluetoothEnabled, userMessage, appState, startAndroidService ->
            MainUiState(
                bpm = appState.bpm,
                bpmCount = bpmCount,
                bluetoothRequested = bluetoothEnabled,
                isClientConnected = appState.isClientConnected,
                permissionsRequested = permissionsRequested,
                servicesState = appState.servicesState,
                startAndroidService = startAndroidService,
                userMessage = userMessage
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = MainUiState()
            )
    }

    fun start() {
        if (enableBluetooth.value == false) {
            enableBluetooth.update { null }
        }
        confirmPermissions()
    }

    fun stop() {
        mainRepository.stopServices()
    }

    private fun confirmPermissions() {
        val missingNotificationPerms = permissionsHelper.getMissingNotificationsPermissions()

        if (missingNotificationPerms.isNotEmpty() || !mainRepository.permissionsGranted()) {
            val permissionsNeeded = missingNotificationPerms + mainRepository.getMissingPermissions()
            permissionsRequested.update { permissionsNeeded.toList() }
        } else {
            confirmBluetoothState()
        }
    }

    fun receivePermissions(permissions: Map<String, Boolean>) {
        if (permissionsRequested.value != null) {
            permissionsRequested.update { null }
        }
        if (permissions.containsValue(false)) {
            userMessage.update { R.string.permissions_denied }
        } else {
            confirmBluetoothState()
        }
    }

    private fun confirmBluetoothState() {
        if (permissionsRequested.value != null) {
            permissionsRequested.update { null } // reset
        }
        if (enableBluetooth.value != false) { // User declined to enable BT
            val bleState = mainRepository.getHardwareState()
            if (bleState == HardwareState.HARDWARE_UNSUITABLE) {
                userMessage.update { R.string.error_hardware }
            } else {
                if (bleState == HardwareState.READY) {
                    logger.debug("Bluetooth is enabled...starting Android service")
                    enableBluetooth.update { null }
                    startAndroidServiceFlow.update { true }
                } else {
                    enableBluetooth.update { true }
                }
            }
        }
    }

    fun androidServiceStarted() = startAndroidServiceFlow.update { false }

    fun toggleFakeBPM() = mainRepository.toggleFakeBpm()

    fun userDeclinedBluetoothEnable() = enableBluetooth.update { false }

    fun userMessageShown() = userMessage.update { null }
}
