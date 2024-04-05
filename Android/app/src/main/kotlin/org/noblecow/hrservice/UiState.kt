package org.noblecow.hrservice

sealed interface UiState {
    val showStart: Boolean
        get() = false
    val showClientStatus: Boolean
        get() = false

    data class Idle(override val showStart: Boolean = true) : UiState
    data class RequestPermissions(val permissions: List<String>) : UiState
    data object RequestEnableBluetooth : UiState
    data class AwaitingClient(
        val bpm: Int,
        override val showClientStatus: Boolean = true
    ) : UiState
    data class ClientConnected(val bpm: Int) : UiState
    data class Error(val errorType: HeartRateError) : UiState
}

sealed interface HeartRateError {
    val fatal: Boolean
        get() = true
    val message: String?
        get() = null

    data object BleHardware : HeartRateError
    data object BtAdvertise : HeartRateError
    data object BtGatt : HeartRateError
    data class Ktor(override val message: String) : HeartRateError
    data class PermissionsDenied(override val fatal: Boolean = false) : HeartRateError
    data class Uncategorized(override val message: String) : HeartRateError
}
