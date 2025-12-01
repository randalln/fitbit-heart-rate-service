package org.noblecow.hrservice.data.source.local.fake

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.noblecow.hrservice.data.source.local.AdvertisingState
import org.noblecow.hrservice.data.source.local.BluetoothLocalDataSource
import org.noblecow.hrservice.data.source.local.HardwareState

/**
 * Fake implementation of BluetoothLocalDataSource for testing.
 *
 * This fake provides controllable behavior for integration tests of MainRepository,
 * MainViewModel, and other components that depend on BluetoothLocalDataSource.
 *
 * **Usage in Tests:**
 * ```kotlin
 * val fake = FakeBluetoothLocalDataSource()
 *
 * // Control behavior
 * fake.shouldFailStartAdvertising = true
 * fake.hardwareStateToReturn = HardwareState.DISABLED
 *
 * // Simulate events
 * fake.simulateClientConnected()
 * fake.simulateAdvertisingFailure()
 *
 * // Verify interactions
 * assertTrue(fake.startAdvertisingCalled > 0)
 * assertEquals(75, fake.notifyHeartRateCalls.last())
 * ```
 *
 * **Advantages over Mocking:**
 * - Works on both Android (with MockK) and iOS (without MockK)
 * - Provides explicit state control for deterministic tests
 * - Easy to verify call counts and arguments
 * - Simpler than maintaining platform-specific mocks
 */
class FakeBluetoothLocalDataSource : BluetoothLocalDataSource {

    // ========================================
    // State Flows
    // ========================================

    private val _advertisingState = MutableStateFlow<AdvertisingState>(AdvertisingState.Stopped)
    override val advertisingState: StateFlow<AdvertisingState> = _advertisingState.asStateFlow()

    private val _clientConnectedState = MutableStateFlow<Boolean>(false)
    override val clientConnectedState: StateFlow<Boolean> = _clientConnectedState.asStateFlow()

    // ========================================
    // Call Tracking
    // ========================================

    /** Number of times startAdvertising() was called */
    var startAdvertisingCalled: Int = 0
        private set

    /** Number of times stopAdvertising() was called */
    var stopAdvertisingCalled: Int = 0
        private set

    /** List of BPM values passed to notifyHeartRate() */
    val notifyHeartRateCalls: MutableList<Int> = mutableListOf()

    /** Number of times getHardwareState() was called */
    var getHardwareStateCalled: Int = 0
        private set

    /** Number of times permissionsGranted() was called */
    var permissionsGrantedCalled: Int = 0
        private set

    /** Number of times getMissingPermissions() was called */
    var getMissingPermissionsCalled: Int = 0
        private set

    // ========================================
    // Controllable Behavior
    // ========================================

    /** If true, startAdvertising() will throw IllegalStateException */
    var shouldFailStartAdvertising: Boolean = false

    /** If true, startAdvertising() sets state to Failure instead of Started */
    var shouldFailStartAdvertisingWithoutException: Boolean = false

    /** Hardware state to return from getHardwareState() */
    var hardwareStateToReturn: HardwareState = HardwareState.READY

    /** Permissions granted state to return from permissionsGranted() */
    var permissionsGrantedToReturn: Boolean = true

    /** If true, notifyHeartRate() will return false even for valid BPM */
    var shouldFailNotifications: Boolean = false

    // ========================================
    // BluetoothLocalDataSource Implementation
    // ========================================

    override suspend fun startAdvertising() {
        startAdvertisingCalled++

        if (shouldFailStartAdvertising) {
            throw IllegalStateException("Bluetooth unavailable (simulated failure)")
        }

        if (shouldFailStartAdvertisingWithoutException) {
            _advertisingState.value = AdvertisingState.Failure
        } else {
            _advertisingState.value = AdvertisingState.Started
        }
    }

    override fun stopAdvertising() {
        stopAdvertisingCalled++
        _advertisingState.value = AdvertisingState.Stopped
        _clientConnectedState.value = false
    }

    override fun notifyHeartRate(bpm: Int): Boolean {
        // Validate BPM range (same as real implementation)
        if (bpm !in 0..255) {
            return false
        }

        notifyHeartRateCalls.add(bpm)

        if (shouldFailNotifications) {
            return false
        }

        // Only succeed if client is connected and advertising
        return _clientConnectedState.value && _advertisingState.value == AdvertisingState.Started
    }

    override fun getHardwareState(): HardwareState {
        getHardwareStateCalled++
        return hardwareStateToReturn
    }

    override fun permissionsGranted(): Boolean {
        permissionsGrantedCalled++
        return permissionsGrantedToReturn
    }

    override fun getMissingPermissions(): Array<String> {
        getMissingPermissionsCalled++
        return if (permissionsGrantedToReturn) {
            emptyArray()
        } else {
            arrayOf("Bluetooth")
        }
    }

    // ========================================
    // Test Helper Methods
    // ========================================

    /**
     * Simulates a client connecting and subscribing to notifications.
     * Sets clientConnectedState to true.
     */
    fun simulateClientConnected() {
        _clientConnectedState.value = true
    }

    /**
     * Simulates a client disconnecting.
     * Sets clientConnectedState to false.
     */
    fun simulateClientDisconnected() {
        _clientConnectedState.value = false
    }

    /**
     * Simulates an advertising failure.
     * Sets advertisingState to Failure.
     */
    fun simulateAdvertisingFailure() {
        _advertisingState.value = AdvertisingState.Failure
    }

    /**
     * Simulates advertising starting (e.g., from a background service).
     * Sets advertisingState to Started.
     */
    fun simulateAdvertisingStarted() {
        _advertisingState.value = AdvertisingState.Started
    }

    /**
     * Resets all call counters and state to initial values.
     * Useful for test setup/teardown.
     */
    fun reset() {
        startAdvertisingCalled = 0
        stopAdvertisingCalled = 0
        notifyHeartRateCalls.clear()
        getHardwareStateCalled = 0
        permissionsGrantedCalled = 0
        getMissingPermissionsCalled = 0

        _advertisingState.value = AdvertisingState.Stopped
        _clientConnectedState.value = false

        shouldFailStartAdvertising = false
        shouldFailStartAdvertisingWithoutException = false
        hardwareStateToReturn = HardwareState.READY
        permissionsGrantedToReturn = true
        shouldFailNotifications = false
    }
}
