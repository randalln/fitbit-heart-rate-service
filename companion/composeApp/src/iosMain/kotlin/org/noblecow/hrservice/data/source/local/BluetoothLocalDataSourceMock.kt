package org.noblecow.hrservice.data.source.local

import co.touchlab.kermit.Logger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Mock implementation of BluetoothLocalDataSource for iOS.
 *
 * This mock simulates successful Bluetooth operations without actual BLE peripheral functionality.
 * All operations succeed immediately and hardware state is always READY.
 *
 * @property logger Logger for debugging
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
internal class BluetoothLocalDataSourceMock(private val logger: Logger) : BluetoothLocalDataSource {

    private val _advertisingState = MutableStateFlow<AdvertisingState>(AdvertisingState.Stopped)
    override val advertisingState: Flow<AdvertisingState> = _advertisingState.asStateFlow()

    private val _clientConnectedState = MutableStateFlow(false)
    override val clientConnectedState: Flow<Boolean> = _clientConnectedState.asStateFlow()

    /**
     * Returns hardware state as always READY for iOS mock.
     */
    override fun getHardwareState(): HardwareState {
        logger.d("iOS Mock: getHardwareState() -> READY")
        return HardwareState.READY
    }

    /**
     * Returns empty array as no permissions are needed for mock.
     */
    override fun getMissingPermissions(): Array<out String> {
        logger.d("iOS Mock: getMissingPermissions() -> []")
        return emptyArray()
    }

    /**
     * Simulates starting BLE advertising.
     * Immediately transitions to Started state.
     */
    override fun startAdvertising() {
        logger.d("iOS Mock: startAdvertising()")
        _advertisingState.value = AdvertisingState.Started
        // Simulate a client connecting after a brief period
        // In a real implementation, this would happen when an actual BLE central connects
        _clientConnectedState.value = true
    }

    /**
     * Simulates stopping BLE advertising.
     * Immediately transitions to Stopped state.
     */
    override fun stopAdvertising() {
        logger.d("iOS Mock: stopAdvertising()")
        _advertisingState.value = AdvertisingState.Stopped
        _clientConnectedState.value = false
    }

    /**
     * Simulates notifying a heart rate value to connected clients.
     * Always returns true to indicate success.
     *
     * @param bpm The heart rate in beats per minute
     * @return true (always successful in mock)
     */
    override fun notifyHeartRate(bpm: Int): Boolean {
        logger.d("iOS Mock: notifyHeartRate($bpm)")
        return true
    }

    /**
     * Returns true as all permissions are considered granted for mock.
     */
    override fun permissionsGranted(): Boolean {
        logger.d("iOS Mock: permissionsGranted() -> true")
        return true
    }
}
