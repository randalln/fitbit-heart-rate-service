package org.noblecow.hrservice.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.noblecow.hrservice.R
import org.noblecow.hrservice.data.repository.MainRepository
import org.noblecow.hrservice.data.repository.ServicesState
import org.noblecow.hrservice.data.source.local.HardwareState
import org.noblecow.hrservice.data.util.DEFAULT_BPM
import org.noblecow.hrservice.data.util.PermissionsHelper
import org.slf4j.LoggerFactory

internal data class MainUiState(
    val bpm: Int = DEFAULT_BPM,
    val isBluetoothEnabled: Boolean = true,
    val isClientConnected: Boolean = false,
    val permissionsRequested: List<String>? = null,
    val servicesState: ServicesState = ServicesState.Stopped,
    val startAndroidService: Boolean = false,
    val userMessage: Int? = null
)

private const val STOP_TIMEOUT_MILLIS = 5000L
private const val TAG = "MainViewModel"

@HiltViewModel
@Suppress("TooManyFunctions")
internal class MainViewModel @Inject constructor(
    private val mainRepository: MainRepository,
    private val permissionsHelper: PermissionsHelper
) : ViewModel() {

    val mainUiState: StateFlow<MainUiState>

    private val permissionsRequested: MutableStateFlow<List<String>?>
    private val bluetoothEnabled: MutableStateFlow<Boolean>
    private val startAndroidServiceFlow: MutableStateFlow<Boolean>
    private val userMessage: MutableStateFlow<Int?>

    private val logger = LoggerFactory.getLogger(TAG)

    init {
        // Use model defaults as SSOT
        MainUiState().also {
            permissionsRequested = MutableStateFlow(it.permissionsRequested)
            bluetoothEnabled = MutableStateFlow(it.isBluetoothEnabled)
            startAndroidServiceFlow = MutableStateFlow(it.startAndroidService)
            userMessage = MutableStateFlow(it.userMessage)
        }

        mainUiState = combine(
            permissionsRequested,
            bluetoothEnabled,
            userMessage,
            mainRepository.getAppStateStream(),
            startAndroidServiceFlow
        ) { permissionsRequested, bluetoothEnabled, userMessage, appState, startAndroidService ->
            MainUiState(
                bpm = appState.bpm,
                isBluetoothEnabled = bluetoothEnabled,
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
        confirmPermissions()
    }

    private fun confirmPermissions() {
        val missingNotificationPerms = permissionsHelper.getMissingNotificationsPermissions()

        if (missingNotificationPerms.isNotEmpty() || !mainRepository.permissionsGranted()) {
            val permissionsNeeded = mainRepository.getMissingPermissions() +
                missingNotificationPerms
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
            permissionsRequested.update { null }
        }
        if (!bluetoothEnabled.value) {
            bluetoothEnabled.update { false }
        }
        val bleState = mainRepository.getHardwareState()
        if (bleState == HardwareState.HARDWARE_UNSUITABLE) {
            userMessage.update { R.string.error_hardware }
        } else {
            if (bleState == HardwareState.READY) {
                logger.debug("Bluetooth is enabled...starting Android service")
                startAndroidServiceFlow.update { true }
            } else {
                bluetoothEnabled.update { false }
            }
        }
    }

    fun androidServiceStarted() = startAndroidServiceFlow.update { false }

    fun toggleFakeBPM() = mainRepository.toggleFakeBpm()

    fun userDeclinedBluetoothEnable() = bluetoothEnabled.update { true }

    fun userMessageShown() = userMessage.update { null }
}
