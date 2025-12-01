package org.noblecow.hrservice.data.source.local

import kotlinx.coroutines.flow.Flow

internal interface BluetoothLocalDataSource {
    val advertisingState: Flow<AdvertisingState>
    val clientConnectedState: Flow<Boolean>
    fun getHardwareState(): HardwareState
    fun getMissingPermissions(): Array<out String>

    /**
     * Starts BLE advertising.
     *
     * This is a suspend function that waits for advertising to actually start before returning.
     * Platform-specific implementations handle delegate callbacks/listeners internally.
     *
     * @throws BluetoothError.PermissionDenied if Bluetooth permissions are not granted
     * @throws BluetoothError.InvalidState if Bluetooth is not in a valid state to advertise
     * @throws BluetoothError.AdvertisingFailed if advertising fails for other reasons
     */
    suspend fun startAdvertising()

    fun stopAdvertising()
    fun notifyHeartRate(bpm: Int): Boolean
    fun permissionsGranted(): Boolean
}

sealed class AdvertisingState {
    data object Started : AdvertisingState()
    data object Stopped : AdvertisingState()
    data object Stopping : AdvertisingState()
    data object Failure : AdvertisingState()
}

/**
 * Common Bluetooth errors that can occur across all platforms.
 * Platform-specific exceptions should be caught and converted to these error types.
 */
sealed class BluetoothError(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    /**
     * User has not granted required Bluetooth permissions.
     * This is thrown when the app lacks necessary permissions to use Bluetooth.
     */
    class PermissionDenied(cause: Throwable? = null) :
        BluetoothError("Bluetooth permissions not granted", cause)

    /**
     * Bluetooth is in an invalid state for the requested operation.
     * Examples: Bluetooth is off, resetting, or unsupported on the device.
     */
    class InvalidState(
        message: String,
        cause: Throwable? = null
    ) : BluetoothError(message, cause)

    /**
     * Advertising operation failed for an unknown reason.
     */
    class AdvertisingFailed(
        message: String,
        cause: Throwable? = null
    ) : BluetoothError(message, cause)
}

enum class HardwareState {
    DISABLED,
    HARDWARE_UNSUITABLE,
    READY
}
