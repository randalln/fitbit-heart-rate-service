package org.noblecow.hrservice

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.noblecow.hrservice.utils.BluetoothHelper
import org.noblecow.hrservice.utils.PermissionsHelper

@OptIn(ExperimentalCoroutinesApi::class)
class HRViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: HRViewModel

    private var permissions = arrayOf(
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT
    )
    private lateinit var bluetoothHelper: BluetoothHelper
    private lateinit var permissionsHelper: PermissionsHelper

    @Before
    fun before() {
        bluetoothHelper = mockk(relaxed = true) {
            every { getBluetoothReceiverFlow() } returns emptyFlow()
            every { getAdvertisingFlow() } returns emptyFlow()
            every { getGattServerFlow() } returns emptyFlow()
        }
        permissionsHelper = mockk(relaxed = true)
    }

    @After
    fun after() {
        viewModel.stopServices()
    }

    @Test
    fun `user starts flow, but hasn't granted permissions yet`() = runTest {
        every { bluetoothHelper.permissionsRequired } returns permissions
        every {
            permissionsHelper.checkSelfPermission(
                any()
            )
        } returns PackageManager.PERMISSION_DENIED
        viewModel = HRViewModel(bluetoothHelper, permissionsHelper)
        advanceUntilIdle()

        viewModel.confirmPermissions()

        viewModel.uiState.test {
            assertEquals(UiState.RequestPermissions(permissions.toList()), awaitItem())
        }
    }

    @Test
    fun `user starts flow and granted permissions previously`() = runTest {
        every { bluetoothHelper.permissionsRequired } returns permissions
        every {
            permissionsHelper.checkSelfPermission(
                any()
            )
        } returns PackageManager.PERMISSION_GRANTED
        viewModel = HRViewModel(bluetoothHelper, permissionsHelper)
        advanceUntilIdle()

        viewModel.confirmPermissions()

        viewModel.uiState.test {
            assertEquals(UiState.RequestEnableBluetooth, awaitItem())
        }
    }

    @Test
    fun `permissions request denied`() = runTest {
        every { bluetoothHelper.permissionsRequired } returns permissions
        every {
            permissionsHelper.checkSelfPermission(
                any()
            )
        } returns PackageManager.PERMISSION_DENIED
        viewModel = HRViewModel(bluetoothHelper, permissionsHelper)
        advanceUntilIdle()

        viewModel.receivePermissions(
            mapOf(
                permissions[0] to true,
                permissions[1] to false
            )
        )

        viewModel.uiState.test {
            assertEquals(UiState.Error(HeartRateError.PermissionsDenied()), awaitItem())
        }
    }

    @Test
    fun `permissions granted, bluetooth is disabled`() = runTest {
        viewModel = HRViewModel(bluetoothHelper, permissionsHelper)
        advanceUntilIdle()

        viewModel.receivePermissions(
            mapOf(
                permissions[0] to true,
                permissions[1] to true
            )
        )

        viewModel.uiState.test {
            assertEquals(UiState.RequestEnableBluetooth, awaitItem())
        }
    }

    @Test
    fun `permissions granted, bluetooth is enabled, no client connected`() = runTest {
        every { bluetoothHelper.getBLEState() } returns BluetoothHelper.BLEState.READY
        every { bluetoothHelper.registeredDevices } returns emptySet<BluetoothDevice>()
            .toMutableSet()
        viewModel = HRViewModel(bluetoothHelper, permissionsHelper)
        advanceUntilIdle()

        viewModel.receivePermissions(
            mapOf(
                permissions[0] to true,
                permissions[1] to true
            )
        )

        viewModel.uiState.test {
            assertEquals(UiState.AwaitingClient(0, showClientStatus = true), awaitItem())
        }
    }

    @Test
    fun `permissions granted, bluetooth is enabled, client is connected`() = runTest {
        every { bluetoothHelper.getBLEState() } returns BluetoothHelper.BLEState.READY
        every { bluetoothHelper.registeredDevices } returns setOf(
            mockk<BluetoothDevice>()
        ).toMutableSet()
        viewModel = HRViewModel(bluetoothHelper, permissionsHelper)
        advanceUntilIdle()

        viewModel.receivePermissions(
            mapOf(
                permissions[0] to true,
                permissions[1] to true
            )
        )

        viewModel.uiState.test {
            assertEquals(UiState.ClientConnected(0), awaitItem())
        }
    }

    @Test
    fun `permissions granted, bluetooth hardware error`() = runTest {
        every { bluetoothHelper.getBLEState() } returns BluetoothHelper.BLEState.HARDWARE_UNSUITABLE
        viewModel = HRViewModel(bluetoothHelper, permissionsHelper)
        advanceUntilIdle()

        viewModel.receivePermissions(
            mapOf(
                permissions[0] to true,
                permissions[1] to true
            )
        )

        viewModel.uiState.test {
            assertEquals(UiState.Error(HeartRateError.BleHardware), awaitItem())
        }
    }

    @Test
    fun `enable bluetooth denied`() = runTest {
        viewModel = HRViewModel(bluetoothHelper, permissionsHelper)
        advanceUntilIdle()

        viewModel.userDeclinedBluetoothEnable()

        viewModel.uiState.test {
            assertEquals(UiState.Idle(showStart = true), awaitItem())
        }
    }

    @Test
    fun `bluetooth disabled at runtime`() = runTest {
        val bluetoothState = MutableSharedFlow<Int>()
        every { bluetoothHelper.getBluetoothReceiverFlow() } returns bluetoothState
        viewModel = HRViewModel(bluetoothHelper, permissionsHelper)
        advanceUntilIdle()

        viewModel.receivePermissions(
            mapOf(
                permissions[0] to true,
                permissions[1] to true
            )
        )
        bluetoothState.emit(BluetoothAdapter.STATE_OFF)

        viewModel.uiState.test {
            assertEquals(UiState.Idle(true), awaitItem())
        }
    }

    @Test
    fun `bluetooth enabled at runtime`() = runTest {
        val bluetoothState = MutableSharedFlow<Int>()
        every { bluetoothHelper.getBluetoothReceiverFlow() } returns bluetoothState
        viewModel = HRViewModel(bluetoothHelper, permissionsHelper)
        advanceUntilIdle()

        viewModel.receivePermissions(
            mapOf(
                permissions[0] to true,
                permissions[1] to true
            )
        )
        bluetoothState.emit(BluetoothAdapter.STATE_ON)

        viewModel.uiState.test {
            assertEquals(UiState.AwaitingClient(0, showClientStatus = true), awaitItem())
        }
    }

    @Test
    fun `bpm received changes UiState, no client connected`() = runTest {
        viewModel = HRViewModel(bluetoothHelper, permissionsHelper)
        every { bluetoothHelper.getBLEState() } returns BluetoothHelper.BLEState.READY
        every { bluetoothHelper.registeredDevices } returns emptySet<BluetoothDevice>()
            .toMutableSet()
        viewModel = HRViewModel(bluetoothHelper, permissionsHelper)
        viewModel.receivePermissions(
            mapOf(
                permissions[0] to true,
                permissions[1] to true
            )
        )
        advanceUntilIdle()

        viewModel.toggleFakeBPM()

        viewModel.uiState.test {
            assertEquals(
                UiState.AwaitingClient(0, sendingFakeBPM = false, showClientStatus = true),
                awaitItem()
            )
            assertEquals(
                UiState.AwaitingClient(FAKE_BPM, sendingFakeBPM = true, showClientStatus = true),
                awaitItem()
            )
        }
    }

    @Test
    fun `bpm received changes UiState, client is connected`() = runTest {
        viewModel = HRViewModel(bluetoothHelper, permissionsHelper)
        every { bluetoothHelper.getBLEState() } returns BluetoothHelper.BLEState.READY
        every { bluetoothHelper.registeredDevices } returns setOf(
            mockk<BluetoothDevice>()
        ).toMutableSet()
        viewModel = HRViewModel(bluetoothHelper, permissionsHelper)
        viewModel.receivePermissions(
            mapOf(
                permissions[0] to true,
                permissions[1] to true
            )
        )
        advanceUntilIdle()

        viewModel.toggleFakeBPM()

        viewModel.uiState.test {
            assertEquals(UiState.ClientConnected(0), awaitItem())
            assertEquals(UiState.ClientConnected(FAKE_BPM, sendingFakeBPM = true), awaitItem())
        }
    }

    @Test
    fun `turn off fake BPM`() = runTest {
        viewModel = HRViewModel(bluetoothHelper, permissionsHelper)
        every { bluetoothHelper.getBLEState() } returns BluetoothHelper.BLEState.READY
        every { bluetoothHelper.registeredDevices } returns setOf(
            mockk<BluetoothDevice>()
        ).toMutableSet()
        viewModel = HRViewModel(bluetoothHelper, permissionsHelper)
        viewModel.receivePermissions(
            mapOf(
                permissions[0] to true,
                permissions[1] to true
            )
        )

        viewModel.toggleFakeBPM()
        advanceUntilIdle()
        viewModel.toggleFakeBPM()

        viewModel.uiState.test {
            assertEquals(UiState.ClientConnected(0, sendingFakeBPM = false), awaitItem())
        }
    }
}
