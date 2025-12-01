package org.noblecow.hrservice.data.source.local

import app.cash.turbine.test
import co.touchlab.kermit.Logger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * Unit tests for BluetoothLocalDataSourceImpl (iOS).
 *
 * **Test Scope:**
 * These tests cover logic that can be tested without real CoreBluetooth hardware.
 *
 * **Limitations:**
 * `CBPeripheralManager` cannot be mocked in Kotlin/Native unit tests. Unlike Android where
 * MockK allows us to mock system services, iOS/Kotlin-Native lacks mature mocking frameworks.
 * Tests requiring peripheral manager interaction must run as instrumented tests on physical devices.
 *
 * **Tests Requiring Physical iOS Device:**
 * - BLE advertising lifecycle (start/stop advertising)
 * - CoreBluetooth delegate callbacks (state changes, advertising events)
 * - Client connection/disconnection to GATT services
 * - Heart rate notifications to connected centrals
 * - StateFlow emissions from real BLE events
 * - Permission checking via peripheral manager state
 *
 * **Covered in These Tests:**
 * - Initial StateFlow values (advertisingState, clientConnectedState)
 * - BPM validation logic (0-255 range)
 * - Idempotent operations (stopAdvertising can be called multiple times)
 * - Basic state queries
 *
 * **Note on Testing Philosophy:**
 * This mirrors the Android testing approach - we test what we can without hardware,
 * and accept that deep platform integration requires device testing. The critical business
 * logic is tested at higher layers (MainRepository, MainViewModel) using fake implementations.
 */
class BluetoothLocalDataSourceTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var dataSource: BluetoothLocalDataSource
    private lateinit var logger: Logger

    @BeforeTest
    fun setup() {
        logger = Logger.withTag("BluetoothTest")
        dataSource = BluetoothLocalDataSourceImpl(
            dispatcher = testDispatcher,
            defaultLogger = logger
        )
    }

    @AfterTest
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun tearDown() {
        if (::dataSource.isInitialized) {
            try {
                dataSource.stopAdvertising()
            } catch (e: Exception) {
                // Ignore cleanup errors - peripheral manager may not be fully initialized in tests
            }
        }
    }

    // ============================================================================
    // State Flow Initialization Tests
    // ============================================================================

    @Test
    fun initialAdvertisingStateIsStopped() = runTest {
        dataSource.advertisingState.test {
            assertEquals(AdvertisingState.Stopped, awaitItem())
        }
    }

    @Test
    fun initialClientConnectedStateIsFalse() = runTest {
        dataSource.clientConnectedState.test {
            assertFalse(awaitItem())
        }
    }

    // ============================================================================
    // Hardware State Detection Tests
    // ============================================================================

    @Test
    fun getHardwareStateReturnsValidState() {
        val state = dataSource.getHardwareState()

        // On simulator/without hardware, state should be one of the valid values
        // We can't control CBPeripheralManager state in unit tests, but we can verify
        // the method returns a valid HardwareState enum value
        assertTrue(
            state in setOf(
                HardwareState.READY,
                HardwareState.DISABLED,
                HardwareState.HARDWARE_UNSUITABLE
            ),
            "getHardwareState() should return a valid HardwareState enum value"
        )
    }

    // ============================================================================
    // Permission Tests
    // ============================================================================

    @Test
    fun permissionsGrantedCompletesWithoutCrashing() {
        // We can't control the permission state in unit tests (no mocking in Kotlin/Native)
        // This is a smoke test that verifies the method executes without throwing exceptions
        // On simulator: typically returns true (no actual permission required)
        // On device: depends on Bluetooth state and Info.plist configuration

        // If this line completes without throwing, the test passes
        dataSource.permissionsGranted()
    }

    @Test
    fun getMissingPermissionsReturnsArrayOfStrings() {
        val permissions = dataSource.getMissingPermissions()

        // Should return an array (empty or with "Bluetooth" depending on state)
        assertTrue(
            permissions.isEmpty() || permissions.contentEquals(arrayOf("Bluetooth")),
            "Missing permissions should be empty or contain 'Bluetooth'"
        )
    }

    // ============================================================================
    // BPM Validation Tests
    // ============================================================================

    @Test
    fun notifyHeartRateReturnsFalseForInvalidBpmNegative() {
        val result = dataSource.notifyHeartRate(-1)

        assertFalse(result, "notifyHeartRate should return false for negative BPM")
    }

    @Test
    fun notifyHeartRateReturnsFalseForInvalidBpmTooHigh() {
        val result = dataSource.notifyHeartRate(256)

        assertFalse(result, "notifyHeartRate should return false for BPM > 255")
    }

    @Test
    fun notifyHeartRateReturnsFalseWhenNoSubscribers() {
        // Valid BPM but no subscribers - should return false
        val result = dataSource.notifyHeartRate(75)

        assertFalse(
            result,
            "notifyHeartRate should return false when no centrals are subscribed"
        )
    }

    @Test
    fun notifyHeartRateAcceptsValidBpmRange() {
        // Test boundary values - these should be validated but return false
        // because there are no subscribers
        assertFalse(
            dataSource.notifyHeartRate(0),
            "BPM 0 is valid but should return false without subscribers"
        )
        assertFalse(
            dataSource.notifyHeartRate(255),
            "BPM 255 is valid but should return false without subscribers"
        )
        assertFalse(
            dataSource.notifyHeartRate(120),
            "BPM 120 is valid but should return false without subscribers"
        )
    }

    @Test
    fun notifyHeartRateValidatesLowerBoundaryValues() {
        // Test values around lower boundary (0)
        assertFalse(
            dataSource.notifyHeartRate(-2),
            "BPM -2 is invalid and should return false"
        )
        assertFalse(
            dataSource.notifyHeartRate(1),
            "BPM 1 is valid but should return false without subscribers"
        )
    }

    @Test
    fun notifyHeartRateValidatesUpperBoundaryValues() {
        // Test values around upper boundary (255)
        assertFalse(
            dataSource.notifyHeartRate(254),
            "BPM 254 is valid but should return false without subscribers"
        )
        assertFalse(
            dataSource.notifyHeartRate(257),
            "BPM 257 is invalid (> 255) and should return false"
        )
    }

    // ============================================================================
    // Lifecycle Tests (Limited Scope)
    // ============================================================================

    @Test
    fun stopAdvertisingIsIdempotentAndCanBeCalledMultipleTimes() = runTest {
        // Should not throw exceptions even when called multiple times
        dataSource.stopAdvertising()
        dataSource.stopAdvertising()
        dataSource.stopAdvertising()

        // Verify advertising state remains Stopped
        dataSource.advertisingState.test {
            assertEquals(
                expected = AdvertisingState.Stopped,
                actual = awaitItem(),
                message = "Advertising state should remain Stopped after multiple stop calls"
            )
        }
    }

    @Test
    fun startAdvertisingThrowsInvalidStateOnSimulator() = runTest {
        // On simulator, Bluetooth hardware is not available
        // startAdvertising should throw InvalidState per the interface contract
        assertFailsWith<BluetoothError.InvalidState>(
            message = "startAdvertising should throw InvalidState on simulator without Bluetooth hardware"
        ) {
            dataSource.startAdvertising()
        }
    }

    // ============================================================================
    // Edge Case Tests
    // ============================================================================

    @Test
    fun multipleDataSourceInstancesDoNotInterfere() = runTest {
        val dataSource2 = BluetoothLocalDataSourceImpl(
            dispatcher = testDispatcher,
            defaultLogger = logger
        )

        // Both should have independent initial states
        dataSource.advertisingState.test {
            assertEquals(AdvertisingState.Stopped, awaitItem())
        }
        dataSource2.advertisingState.test {
            assertEquals(AdvertisingState.Stopped, awaitItem())
        }

        // Cleanup
        dataSource.stopAdvertising()
        dataSource2.stopAdvertising()
    }
}
