package org.noblecow.hrservice

import android.Manifest
import app.cash.turbine.test
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.noblecow.hrservice.data.AdvertisingState
import org.noblecow.hrservice.data.BluetoothRepository
import org.noblecow.hrservice.data.HardwareState
import org.noblecow.hrservice.data.WebServer
import org.noblecow.hrservice.data.WebServerState
import org.noblecow.hrservice.data.blessed.BluetoothLocalDataSource
import org.noblecow.hrservice.ui.FAKE_BPM_START
import org.noblecow.hrservice.ui.GeneralError
import org.noblecow.hrservice.ui.HRViewModel
import org.noblecow.hrservice.ui.UiState

@OptIn(ExperimentalCoroutinesApi::class)
class HRViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: HRViewModel

    private var permissions = arrayOf(
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT
    )
    private val adFlow = MutableStateFlow<AdvertisingState>(AdvertisingState.Stopped)
    private val connectionFlow = MutableStateFlow(false)
    private var bluetoothLocalDataSource: BluetoothLocalDataSource = mockk(relaxed = true) {
        every { advertisingFlow } returns adFlow
        every { clientConnectionFlow } returns connectionFlow
        every { getHardwareState() } returns HardwareState.READY
        every { permissionsGranted() } returns true
    }
    private val webServerStateFlow = MutableStateFlow(WebServerState())
    private val webServer: WebServer = mockk {
        every { webServerState } returns webServerStateFlow
        every { start() } returns true
        every { stop() } just Runs
    }
    private lateinit var bluetoothRepository: BluetoothRepository

    @Before
    fun before() {
        adFlow.value = AdvertisingState.Stopped
        connectionFlow.value = false
        webServerStateFlow.value = WebServerState()
        bluetoothRepository = BluetoothRepository(bluetoothLocalDataSource)
    }

    @After
    fun after() {
        viewModel.stop()
    }

    @Test
    fun `user starts workflow, but hasn't granted permissions yet`() = runTest {
        every { bluetoothLocalDataSource.permissionsGranted() } returns false
        every { bluetoothRepository.getMissingPermissions() } returns permissions
        viewModel = HRViewModel(bluetoothRepository, webServer)
        advanceUntilIdle()

        viewModel.start()

        viewModel.uiState.test {
            assertEquals(UiState.RequestPermissions(permissions.toList()), awaitItem())
        }
    }

    @Test
    fun `user starts workflow and bluetooth is disabled`() = runTest {
        every { bluetoothLocalDataSource.getHardwareState() } returns HardwareState.DISABLED
        viewModel = HRViewModel(bluetoothRepository, webServer)
        advanceUntilIdle()

        viewModel.start()

        viewModel.uiState.test {
            assertEquals(UiState.RequestEnableBluetooth, awaitItem())
        }
    }

    @Test
    fun `use denies permissions request`() = runTest {
        every { bluetoothRepository.getMissingPermissions() } returns permissions

        viewModel = HRViewModel(bluetoothRepository, webServer)
        advanceUntilIdle()
        viewModel.receivePermissions(
            mapOf(
                permissions[0] to true,
                permissions[1] to false
            )
        )

        viewModel.uiState.test {
            assertEquals(UiState.Error(GeneralError.PermissionsDenied()), awaitItem())
        }
    }

    @Test
    fun `user started workflow, but no client is connected yet`() = runTest {
        viewModel = HRViewModel(bluetoothRepository, webServer)

        viewModel.start()
        webServerStateFlow.emit(
            WebServerState(
                bpm = 0,
                error = null,
                running = true
            )
        )

        viewModel.uiState.test {
            assertEquals(
                UiState.AwaitingClient(0, showClientStatus = true),
                awaitItem()
            )
        }
    }

    @Test
    fun `BLE client connects`() = runTest {
        viewModel = HRViewModel(bluetoothRepository, webServer)

        viewModel.start()
        adFlow.value = AdvertisingState.Started
        webServerStateFlow.emit(
            WebServerState(
                bpm = 0,
                error = null,
                running = true
            )
        )
        connectionFlow.emit(true)

        viewModel.uiState.test {
            assertEquals(UiState.ClientConnected(bpm = 0), awaitItem())
        }
    }

    @Test
    fun `bluetooth hardware error`() = runTest {
        every { bluetoothRepository.getHardwareState() } returns HardwareState.HARDWARE_UNSUITABLE
        viewModel = HRViewModel(bluetoothRepository, webServer)
        advanceUntilIdle()

        viewModel.start()
        adFlow.value = AdvertisingState.Started
        advanceUntilIdle()

        viewModel.uiState.test {
            assertEquals(UiState.Error(GeneralError.BleHardware), awaitItem())
        }
    }

    @Test
    fun `user declines enabling bluetooth`() = runTest {
        viewModel = HRViewModel(bluetoothRepository, webServer)

        advanceUntilIdle()
        viewModel.userDeclinedBluetoothEnable()

        viewModel.uiState.test {
            assertEquals(UiState.Idle(showStart = true), awaitItem())
        }
    }

    @Test
    fun `bluetooth is disabled at runtime`() = runTest {
        viewModel = HRViewModel(bluetoothRepository, webServer)

        viewModel.start()
        adFlow.value = AdvertisingState.Started
        adFlow.value = AdvertisingState.Stopped
        advanceUntilIdle()

        viewModel.uiState.test {
            assertEquals(UiState.Idle(true), awaitItem())
        }
    }

    @Test
    fun `bpm received, no client connected`() = runTest {
        viewModel = HRViewModel(bluetoothRepository, webServer)

        viewModel.start()
        adFlow.value = AdvertisingState.Started
        advanceUntilIdle()
        webServerStateFlow.emit(
            WebServerState(
                bpm = FAKE_BPM_START,
                error = null,
                running = true
            )
        )

        viewModel.uiState.test {
            assertEquals(
                UiState.AwaitingClient(
                    FAKE_BPM_START,
                    showClientStatus = true
                ),
                awaitItem()
            )
        }
    }

    @Test
    fun `bpm received, client is connected`() = runTest {
        viewModel = HRViewModel(bluetoothRepository, webServer)

        viewModel.start()
        adFlow.value = AdvertisingState.Started
        connectionFlow.emit(true)
        advanceUntilIdle()
        webServerStateFlow.emit(WebServerState(bpm = FAKE_BPM_START, error = null, running = true))

        viewModel.uiState.test {
            assertEquals(UiState.ClientConnected(FAKE_BPM_START), awaitItem())
        }
    }
}
