package org.noblecow.hrservice

import android.Manifest
import app.cash.turbine.test
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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
import org.noblecow.hrservice.data.blessed.BluetoothLocalDataSource
import org.noblecow.hrservice.ui.FAKE_BPM_START
import org.noblecow.hrservice.ui.GeneralError
import org.noblecow.hrservice.ui.HRViewModel
import org.noblecow.hrservice.ui.PORT_LISTEN
import org.noblecow.hrservice.ui.Request
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
    private val adFlow = MutableSharedFlow<AdvertisingState>()
    private val connectionFlow = MutableStateFlow(false)
    private var bluetoothLocalDataSource: BluetoothLocalDataSource = mockk(relaxed = true) {
        every { advertisingFlow } returns adFlow
        every { clientConnectionFlow } returns connectionFlow
    }
    private lateinit var bluetoothRepository: BluetoothRepository

    @Before
    fun before() {
        bluetoothRepository = BluetoothRepository(bluetoothLocalDataSource)
    }

    @After
    fun after() {
        viewModel.stopServices()
    }

    @Test
    fun `user starts flow, but hasn't granted permissions yet`() = runTest {
        every { bluetoothRepository.getMissingPermissions() } returns permissions
        viewModel = HRViewModel(bluetoothRepository)
        advanceUntilIdle()

        viewModel.confirmPermissions()

        viewModel.uiState.test {
            assertEquals(UiState.RequestPermissions(permissions.toList()), awaitItem())
        }
    }

    @Test
    fun `user starts flow and granted permissions previously`() = runTest {
        every { bluetoothLocalDataSource.permissionsGranted() } returns true
        viewModel = HRViewModel(bluetoothRepository)
        advanceUntilIdle()

        viewModel.confirmPermissions()

        viewModel.uiState.test {
            assertEquals(UiState.RequestEnableBluetooth, awaitItem())
        }
    }

    @Test
    fun `permissions request denied`() = runTest {
        every { bluetoothRepository.getMissingPermissions() } returns permissions
        viewModel = HRViewModel(bluetoothRepository)
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
    fun `permissions granted, bluetooth is disabled`() = runTest {
        viewModel = HRViewModel(bluetoothRepository)
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
        every { bluetoothRepository.getHardwareState() } returns HardwareState.READY
        viewModel = HRViewModel(bluetoothRepository)

        viewModel.receivePermissions(
            mapOf(
                permissions[0] to true,
                permissions[1] to true
            )
        )
        adFlow.emit(AdvertisingState.Started)
        advanceUntilIdle()

        viewModel.uiState.test {
            assertEquals(
                UiState.AwaitingClient(0, sendingFakeBPM = false, showClientStatus = true),
                awaitItem()
            )
        }
    }

    @Test
    fun `permissions granted, bluetooth is enabled, client is connected`() = runTest {
        every { bluetoothRepository.getHardwareState() } returns HardwareState.READY
        viewModel = HRViewModel(bluetoothRepository)

        viewModel.receivePermissions(
            mapOf(
                permissions[0] to true,
                permissions[1] to true
            )
        )
        adFlow.emit(AdvertisingState.Started)
        connectionFlow.emit(true)

        viewModel.uiState.test {
            assertEquals(UiState.ClientConnected(bpm = 0, sendingFakeBPM = false), awaitItem())
        }
    }

    @Test
    fun `permissions granted, bluetooth hardware error`() = runTest {
        every { bluetoothRepository.getHardwareState() } returns HardwareState.HARDWARE_UNSUITABLE
        viewModel = HRViewModel(bluetoothRepository)
        advanceUntilIdle()

        viewModel.receivePermissions(
            mapOf(
                permissions[0] to true,
                permissions[1] to true
            )
        )

        viewModel.uiState.test {
            assertEquals(UiState.Error(GeneralError.BleHardware), awaitItem())
        }
    }

    @Test
    fun `enable bluetooth denied`() = runTest {
        viewModel = HRViewModel(bluetoothRepository)
        advanceUntilIdle()

        viewModel.userDeclinedBluetoothEnable()

        viewModel.uiState.test {
            assertEquals(UiState.Idle(showStart = true), awaitItem())
        }
    }

    @Test
    fun `bluetooth disabled at runtime`() = runTest {
        every { bluetoothLocalDataSource.getHardwareState() } returns HardwareState.READY
        viewModel = HRViewModel(bluetoothRepository)

        viewModel.receivePermissions(
            mapOf(
                permissions[0] to true,
                permissions[1] to true
            )
        )
        adFlow.emit(AdvertisingState.Started)
        advanceUntilIdle()
        adFlow.emit(AdvertisingState.Stopped)

        viewModel.uiState.test {
            assertEquals(UiState.Idle(true), awaitItem())
        }
    }

    @Test
    fun `bluetooth enabled at runtime`() = runTest {
        every { bluetoothLocalDataSource.getHardwareState() } returns HardwareState.READY
        viewModel = HRViewModel(bluetoothRepository)

        viewModel.receivePermissions(
            mapOf(
                permissions[0] to true,
                permissions[1] to true
            )
        )
        advanceUntilIdle()
        adFlow.emit(AdvertisingState.Started)

        viewModel.uiState.test {
            assertEquals(UiState.AwaitingClient(0, showClientStatus = true), awaitItem())
        }
    }

    @Test
    fun `bpm received changes UiState, no client connected`() = runTest {
        viewModel = HRViewModel(bluetoothRepository)
        every { bluetoothLocalDataSource.getHardwareState() } returns HardwareState.READY
        viewModel = HRViewModel(bluetoothRepository)

        viewModel.receivePermissions(
            mapOf(
                permissions[0] to true,
                permissions[1] to true
            )
        )
        adFlow.emit(AdvertisingState.Started)
        advanceUntilIdle()
        sendBpm(FAKE_BPM_START)

        viewModel.uiState.test {
            assertEquals(
                UiState.AwaitingClient(
                    FAKE_BPM_START,
                    sendingFakeBPM = false,
                    showClientStatus = true
                ),
                awaitItem()
            )
        }
    }

    @Test
    fun `bpm received changes UiState, client is connected`() = runTest {
        viewModel = HRViewModel(bluetoothRepository)
        every { bluetoothLocalDataSource.getHardwareState() } returns HardwareState.READY
        viewModel = HRViewModel(bluetoothRepository)

        viewModel.receivePermissions(
            mapOf(
                permissions[0] to true,
                permissions[1] to true
            )
        )
        adFlow.emit(AdvertisingState.Started)
        connectionFlow.emit(true)
        advanceUntilIdle()
        sendBpm(FAKE_BPM_START)

        viewModel.uiState.test {
            assertEquals(
                UiState.ClientConnected(FAKE_BPM_START, sendingFakeBPM = false),
                awaitItem()
            )
        }
    }

    @Test
    fun `turn off fake BPM`() = runTest {
        viewModel = HRViewModel(bluetoothRepository)
        every { bluetoothLocalDataSource.getHardwareState() } returns HardwareState.READY
        viewModel = HRViewModel(bluetoothRepository)

        viewModel.receivePermissions(
            mapOf(
                permissions[0] to true,
                permissions[1] to true
            )
        )
        adFlow.emit(AdvertisingState.Started)
        advanceUntilIdle()
        viewModel.toggleFakeBPM()

        viewModel.uiState.test {
            assertEquals(
                UiState.AwaitingClient(0, sendingFakeBPM = true, showClientStatus = true),
                awaitItem()
            )
        }
    }

    private suspend fun sendBpm(bpm: Int) {
        val client = HttpClient(Android) {
            install(ContentNegotiation) {
                json()
            }
        }
        client.post("http://localhost:$PORT_LISTEN") {
            contentType(ContentType.Application.Json)
            setBody(Request(bpm))
        }
    }
}
