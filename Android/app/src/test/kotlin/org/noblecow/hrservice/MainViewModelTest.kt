package org.noblecow.hrservice

import android.Manifest
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.noblecow.hrservice.data.repository.AppState
import org.noblecow.hrservice.data.repository.MainRepository
import org.noblecow.hrservice.data.repository.ServicesState
import org.noblecow.hrservice.data.source.local.HardwareState
import org.noblecow.hrservice.data.util.FAKE_BPM_START
import org.noblecow.hrservice.data.util.PermissionsHelper
import org.noblecow.hrservice.ui.MainUiState
import org.noblecow.hrservice.ui.MainViewModel

class MainViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: MainViewModel

    private var bluetoothPermissions = arrayOf(
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT
    )
    private val permissionsHelper: PermissionsHelper = mockk {
        every { getMissingNotificationsPermissions() } returns emptyArray()
    }

    private val appStateFlow = MutableStateFlow(AppState())
    private val mainRepository: MainRepository = mockk {
        every { permissionsGranted() } returns true
        every { getAppStateStream() } returns appStateFlow
        every { getHardwareState() } returns HardwareState.READY
    }

    @Test
    fun `user starts workflow and bluetooth is disabled`() = runTest {
        every { mainRepository.permissionsGranted() } returns true
        every { mainRepository.getHardwareState() } returns HardwareState.DISABLED
        viewModel = MainViewModel(mainRepository, permissionsHelper)

        viewModel.mainUiState.test {
            assertEquals(MainUiState(), awaitItem())
            viewModel.start()
            assertEquals(MainUiState(bluetoothRequested = true), awaitItem())
            viewModel.start()
        }
    }

    @Test
    fun `user starts workflow and grants permissions`() = runTest {
        every { mainRepository.permissionsGranted() } returns false
        every { mainRepository.getMissingPermissions() } returns bluetoothPermissions
        val notificationPermissions = arrayOf(
            Manifest.permission.POST_NOTIFICATIONS
        )
        every { permissionsHelper.getMissingNotificationsPermissions() } returns
            notificationPermissions
        viewModel = MainViewModel(mainRepository, permissionsHelper)
        val allPermissions = bluetoothPermissions + notificationPermissions

        viewModel.mainUiState.test {
            assertEquals(MainUiState(), awaitItem())
            viewModel.start()
            assertEquals(MainUiState(permissionsRequested = allPermissions.toList()), awaitItem())
            viewModel.receivePermissions(
                mapOf(
                    bluetoothPermissions[0] to true,
                    bluetoothPermissions[1] to true
                )
            )
            assertEquals(MainUiState(), awaitItem())
            assertEquals(MainUiState(startAndroidService = true), awaitItem())
        }
    }

    @Test
    fun `user denies permissions request`() = runTest {
        every { mainRepository.getMissingPermissions() } returns bluetoothPermissions
        viewModel = MainViewModel(mainRepository, permissionsHelper)

        viewModel.mainUiState.test {
            assertEquals(MainUiState(), awaitItem())
            viewModel.receivePermissions(
                mapOf(
                    bluetoothPermissions[0] to true,
                    bluetoothPermissions[1] to false
                )
            )
            assertEquals(MainUiState(userMessage = R.string.permissions_denied), awaitItem())
            viewModel.userMessageShown()
            assertEquals(MainUiState(), awaitItem())
        }
    }

    @Test
    fun `user starts workflow, but no client is connected yet`() = runTest {
        viewModel = MainViewModel(mainRepository, permissionsHelper)

        happyPathToStarted()
    }

    private suspend fun happyPathToStarted() {
        viewModel.mainUiState.test {
            assertEquals(MainUiState(), awaitItem())
            viewModel.start()
            assertEquals(MainUiState(startAndroidService = true), awaitItem())
            viewModel.androidServiceStarted()
            assertEquals(MainUiState(), awaitItem())
            appStateFlow.value = AppState(servicesState = ServicesState.Starting)
            assertEquals(MainUiState(servicesState = ServicesState.Starting), awaitItem())
            appStateFlow.value = AppState(servicesState = ServicesState.Started)
            assertEquals(MainUiState(servicesState = ServicesState.Started), awaitItem())
        }
    }

    @Test
    fun `BLE client connects`() = runTest {
        viewModel = MainViewModel(mainRepository, permissionsHelper)

        viewModel.mainUiState.test {
            assertEquals(MainUiState(), awaitItem())
            appStateFlow.value = AppState(
                servicesState = ServicesState.Started,
                isClientConnected = true
            )
            assertEquals(
                MainUiState(servicesState = ServicesState.Started, isClientConnected = true),
                awaitItem()
            )
        }
    }

    @Test
    fun `bluetooth hardware error`() = runTest {
        every { mainRepository.getHardwareState() } returns HardwareState.HARDWARE_UNSUITABLE
        viewModel = MainViewModel(mainRepository, permissionsHelper)

        viewModel.mainUiState.test {
            assertEquals(MainUiState(), awaitItem())
            viewModel.start()
            assertEquals(MainUiState(userMessage = R.string.error_hardware), awaitItem())
            viewModel.userMessageShown()
            assertEquals(MainUiState(), awaitItem())
        }
    }

    @Test
    fun `services stopped for some reason like BT being turned off`() = runTest {
        viewModel = MainViewModel(mainRepository, permissionsHelper)

        happyPathToStarted()
        viewModel.mainUiState.test {
            awaitItem()
            appStateFlow.value = AppState(servicesState = ServicesState.Stopping)
            assertEquals(MainUiState(servicesState = ServicesState.Stopping), awaitItem())
            appStateFlow.value = AppState(servicesState = ServicesState.Stopped)
            assertEquals(MainUiState(servicesState = ServicesState.Stopped), awaitItem())
        }
    }

    @Test
    fun `bpm received, no client connected`() = runTest {
        viewModel = MainViewModel(mainRepository, permissionsHelper)

        happyPathToStarted()
        viewModel.mainUiState.test {
            awaitItem()
            appStateFlow.value = AppState(
                bpm = FAKE_BPM_START,
                servicesState = ServicesState.Started
            )
            assertEquals(
                MainUiState(bpm = FAKE_BPM_START, servicesState = ServicesState.Started),
                awaitItem()
            )
        }
    }

    @Test
    fun `bpm received, client is connected`() = runTest {
        viewModel = MainViewModel(mainRepository, permissionsHelper)

        happyPathToStarted()
        viewModel.mainUiState.test {
            awaitItem()
            appStateFlow.value = AppState(
                bpm = FAKE_BPM_START,
                isClientConnected = true,
                servicesState = ServicesState.Started
            )
            assertEquals(
                MainUiState(
                    bpm = FAKE_BPM_START,
                    isClientConnected = true,
                    servicesState = ServicesState.Started
                ),
                awaitItem()
            )
        }
    }
}
