package org.noblecow.hrservice.viewmodel

import android.Manifest
import app.cash.turbine.test
import co.touchlab.kermit.CommonWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.loggerConfigInit
import heartratemonitor.composeapp.generated.resources.Res
import heartratemonitor.composeapp.generated.resources.error_hardware
import heartratemonitor.composeapp.generated.resources.permissions_denied
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.noblecow.hrservice.MainDispatcherRule
import org.noblecow.hrservice.data.repository.AppState
import org.noblecow.hrservice.data.repository.MainRepository
import org.noblecow.hrservice.data.repository.ServicesState
import org.noblecow.hrservice.data.source.local.HardwareState
import org.noblecow.hrservice.data.util.FAKE_BPM_START
import org.noblecow.hrservice.data.util.ResourceHelper
import org.noblecow.hrservice.domain.usecase.StartServiceResult
import org.noblecow.hrservice.domain.usecase.StartServicesUseCase

class MainViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: MainViewModel

    private var bluetoothPermissions = arrayOf(
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT
    )

    private val resourceHelper: ResourceHelper = mockk {
        coEvery { getString(Res.string.permissions_denied) } returns "Required permissions denied"
        coEvery { getString(Res.string.error_hardware) } returns "Hardware unsuitable"
    }

    private val testAppStateFlow = MutableStateFlow(AppState())
    private val mainRepository: MainRepository = mockk {
        every { permissionsGranted() } returns true
        every { appStateFlow } returns testAppStateFlow
        every { getHardwareState() } returns HardwareState.READY
        coEvery { startServices() } just Runs
    }

    private val startServicesUseCase: StartServicesUseCase = mockk()
    val logger = Logger(loggerConfigInit(CommonWriter()), MainViewModelTest::class.simpleName ?: "")

    @Test
    fun `user starts workflow and bluetooth is disabled`() = runTest {
        coEvery { startServicesUseCase() } returns StartServiceResult.BluetoothDisabled
        viewModel = MainViewModel(mainRepository, startServicesUseCase, resourceHelper, logger)

        viewModel.mainUiState.test {
            assertEquals(MainUiState(bpmCount = 1), awaitItem())
            viewModel.start()
            assertEquals(MainUiState(bpmCount = 1, bluetoothRequested = true), awaitItem())
            viewModel.start()
        }
    }

    @Test
    fun `user declines to enable bluetooth then re-requests`() = runTest {
        coEvery { startServicesUseCase() } returns StartServiceResult.BluetoothDisabled
        viewModel = MainViewModel(mainRepository, startServicesUseCase, resourceHelper, logger)

        viewModel.mainUiState.test {
            assertEquals(MainUiState(bpmCount = 1), awaitItem())
            viewModel.start()
            assertEquals(MainUiState(bpmCount = 1, bluetoothRequested = true), awaitItem())
            viewModel.userDeclinedBluetoothEnable()
            assertEquals(MainUiState(bpmCount = 1, bluetoothRequested = false), awaitItem())
            viewModel.start()
            assertEquals(MainUiState(bpmCount = 1, bluetoothRequested = null), awaitItem())
            assertEquals(MainUiState(bpmCount = 1, bluetoothRequested = true), awaitItem())
        }
    }

    @Test
    fun `user starts workflow and grants permissions`() = runTest {
        val notificationPermissions = arrayOf(
            Manifest.permission.POST_NOTIFICATIONS
        )
        val allPermissions = notificationPermissions + bluetoothPermissions
        coEvery { startServicesUseCase() } returnsMany listOf(
            StartServiceResult.PermissionsNeeded(allPermissions.toList()),
            StartServiceResult.Starting
        )
        viewModel = MainViewModel(mainRepository, startServicesUseCase, resourceHelper, logger)

        viewModel.mainUiState.test {
            assertEquals(MainUiState(bpmCount = 1), awaitItem())
            viewModel.start()
            assertEquals(
                MainUiState(bpmCount = 1, permissionsRequested = allPermissions.toList()),
                awaitItem()
            )
            viewModel.receivePermissions(
                mapOf(
                    bluetoothPermissions[0] to true,
                    bluetoothPermissions[1] to true
                )
            )
            assertEquals(MainUiState(bpmCount = 1), awaitItem())
        }
    }

    @Test
    fun `user denies permissions request`() = runTest {
        coEvery { startServicesUseCase() } returns StartServiceResult.Starting
        viewModel = MainViewModel(mainRepository, startServicesUseCase, resourceHelper, logger)

        viewModel.mainUiState.test {
            assertEquals(MainUiState(bpmCount = 1), awaitItem())
            viewModel.receivePermissions(
                mapOf(
                    bluetoothPermissions[0] to true,
                    bluetoothPermissions[1] to false
                )
            )
            assertEquals(
                MainUiState(
                    bpmCount = 1,
                    userMessage = resourceHelper.getString(Res.string.permissions_denied)
                ),
                awaitItem()
            )
            viewModel.userMessageShown()
            assertEquals(MainUiState(bpmCount = 1), awaitItem())
        }
    }

    @Test
    fun `user denies permissions then grants on retry`() = runTest {
        coEvery { startServicesUseCase() } returnsMany listOf(
            StartServiceResult.PermissionsNeeded(bluetoothPermissions.toList()),
            StartServiceResult.PermissionsNeeded(bluetoothPermissions.toList()),
            StartServiceResult.Starting
        )
        viewModel = MainViewModel(mainRepository, startServicesUseCase, resourceHelper, logger)

        viewModel.mainUiState.test {
            assertEquals(MainUiState(bpmCount = 1), awaitItem())

            // First attempt - request permissions
            viewModel.start()
            assertEquals(
                MainUiState(bpmCount = 1, permissionsRequested = bluetoothPermissions.toList()),
                awaitItem()
            )

            // User denies one permission
            viewModel.receivePermissions(
                mapOf(
                    bluetoothPermissions[0] to true,
                    bluetoothPermissions[1] to false
                )
            )
            assertEquals(MainUiState(bpmCount = 1), awaitItem())
            assertEquals(
                MainUiState(
                    bpmCount = 1,
                    userMessage = resourceHelper.getString(Res.string.permissions_denied)
                ),
                awaitItem()
            )

            // Clear message and retry
            viewModel.userMessageShown()
            assertEquals(MainUiState(bpmCount = 1), awaitItem())
            viewModel.start()
            assertEquals(
                MainUiState(bpmCount = 1, permissionsRequested = bluetoothPermissions.toList()),
                awaitItem()
            )

            // User grants all permissions this time
            viewModel.receivePermissions(
                mapOf(
                    bluetoothPermissions[0] to true,
                    bluetoothPermissions[1] to true
                )
            )
            assertEquals(MainUiState(bpmCount = 1), awaitItem())
        }
    }

    @Test
    fun `user starts workflow, but no client is connected yet`() = runTest {
        coEvery { startServicesUseCase() } returns StartServiceResult.Starting
        viewModel = MainViewModel(mainRepository, startServicesUseCase, resourceHelper, logger)

        viewModel.mainUiState.test {
            assertEquals(MainUiState(bpmCount = 1), awaitItem())
            viewModel.start()
        }
    }

    @Test
    fun `BLE client connects`() = runTest {
        coEvery { startServicesUseCase() } returns StartServiceResult.Starting
        viewModel = MainViewModel(mainRepository, startServicesUseCase, resourceHelper, logger)

        viewModel.mainUiState.test {
            assertEquals(MainUiState(bpmCount = 1), awaitItem())
            testAppStateFlow.value = AppState(
                servicesState = ServicesState.Started,
                isClientConnected = true
            )
            val state = awaitItem()
            assertEquals(2, state.bpmCount)
            assertEquals(ServicesState.Started, state.servicesState)
            assertTrue(state.isClientConnected)
        }
    }

    @Test
    fun `bluetooth hardware error`() = runTest {
        coEvery { startServicesUseCase() } returns StartServiceResult.HardwareError("Hardware unsuitable")
        viewModel = MainViewModel(mainRepository, startServicesUseCase, resourceHelper, logger)

        viewModel.mainUiState.test {
            assertEquals(MainUiState(bpmCount = 1), awaitItem())
            viewModel.start()
            assertEquals(
                MainUiState(
                    bpmCount = 1,
                    userMessage = "Hardware unsuitable"
                ),
                awaitItem()
            )
            viewModel.userMessageShown()
            assertEquals(MainUiState(bpmCount = 1), awaitItem())
        }
    }

    @Test
    fun `services stopped for some reason like BT being turned off`() = runTest {
        coEvery { startServicesUseCase() } returns StartServiceResult.Starting
        viewModel = MainViewModel(mainRepository, startServicesUseCase, resourceHelper, logger)

        viewModel.mainUiState.test {
            assertEquals(MainUiState(bpmCount = 1), awaitItem())
            testAppStateFlow.value = AppState(servicesState = ServicesState.Starting)
            assertEquals(MainUiState(bpmCount = 2, servicesState = ServicesState.Starting), awaitItem())
            testAppStateFlow.value = AppState(servicesState = ServicesState.Started)
            assertEquals(MainUiState(bpmCount = 3, servicesState = ServicesState.Started), awaitItem())

            // Now test services being stopped
            testAppStateFlow.value = AppState(servicesState = ServicesState.Stopping)
            assertEquals(MainUiState(bpmCount = 4, servicesState = ServicesState.Stopping), awaitItem())
            testAppStateFlow.value = AppState(servicesState = ServicesState.Stopped)
            assertEquals(MainUiState(bpmCount = 5, servicesState = ServicesState.Stopped), awaitItem())
        }
    }

    @Test
    fun `bpm received, no client connected`() = runTest {
        coEvery { startServicesUseCase() } returns StartServiceResult.Starting
        viewModel = MainViewModel(mainRepository, startServicesUseCase, resourceHelper, logger)

        viewModel.mainUiState.test {
            assertEquals(MainUiState(bpmCount = 1), awaitItem())
            testAppStateFlow.value = AppState(servicesState = ServicesState.Starting)
            assertEquals(MainUiState(bpmCount = 2, servicesState = ServicesState.Starting), awaitItem())
            testAppStateFlow.value = AppState(servicesState = ServicesState.Started)
            assertEquals(MainUiState(bpmCount = 3, servicesState = ServicesState.Started), awaitItem())

            // Now test BPM being received
            testAppStateFlow.value = AppState(
                bpm = FAKE_BPM_START,
                servicesState = ServicesState.Started
            )
            assertEquals(
                MainUiState(bpm = FAKE_BPM_START, bpmCount = 4, servicesState = ServicesState.Started),
                awaitItem()
            )
        }
    }

    @Test
    fun `bpm received, client is connected`() = runTest {
        coEvery { startServicesUseCase() } returns StartServiceResult.Starting
        viewModel = MainViewModel(mainRepository, startServicesUseCase, resourceHelper, logger)

        viewModel.mainUiState.test {
            assertEquals(MainUiState(bpmCount = 1), awaitItem())
            testAppStateFlow.value = AppState(servicesState = ServicesState.Starting)
            assertEquals(MainUiState(bpmCount = 2, servicesState = ServicesState.Starting), awaitItem())
            testAppStateFlow.value = AppState(servicesState = ServicesState.Started)
            assertEquals(MainUiState(bpmCount = 3, servicesState = ServicesState.Started), awaitItem())

            // Now test BPM being received with client connected
            testAppStateFlow.value = AppState(
                bpm = FAKE_BPM_START,
                isClientConnected = true,
                servicesState = ServicesState.Started
            )
            assertEquals(
                MainUiState(
                    bpm = FAKE_BPM_START,
                    bpmCount = 4,
                    isClientConnected = true,
                    servicesState = ServicesState.Started
                ),
                awaitItem()
            )
        }
    }

    /*
    @Test
    fun `platformServiceStarted resets flag to false`() = runTest {
        // This functionality has been removed - WorkManager is now started automatically
        // by HeartRateApp when servicesState changes to Started
    }
     */

    /*
    @Test
    fun `user starts workflow when services already started`() = runTest {
        coEvery { startServicesUseCase() } returns StartServiceResult.AlreadyStarted
        viewModel = MainViewModel(mainRepository, startServicesUseCase, resourceHelper, logger)

        viewModel.mainUiState.test {
            assertEquals(MainUiState(bpmCount = 1), awaitItem())
            viewModel.start()
            // Should not request anything since services are already started
            expectNoEvents()
        }
    }
     */

    @Test
    fun `userMessageShown clears user message`() = runTest {
        coEvery { startServicesUseCase() } returns StartServiceResult.HardwareError("Hardware unsuitable")
        viewModel = MainViewModel(mainRepository, startServicesUseCase, resourceHelper, logger)

        viewModel.mainUiState.test {
            assertEquals(MainUiState(bpmCount = 1), awaitItem())
            viewModel.start()
            assertEquals(
                MainUiState(
                    bpmCount = 1,
                    userMessage = "Hardware unsuitable"
                ),
                awaitItem()
            )
            viewModel.userMessageShown()
            assertEquals(MainUiState(bpmCount = 1, userMessage = null), awaitItem())
        }
    }

    @Test
    fun `stop calls repository`() = runTest {
        coEvery { startServicesUseCase() } returns StartServiceResult.Starting
        viewModel = MainViewModel(mainRepository, startServicesUseCase, resourceHelper, logger)
        coEvery { mainRepository.stopServices() } just Runs

        viewModel.stop()

        coVerify(exactly = 1) { mainRepository.stopServices() }
    }

    @Test
    fun `toggle fake bpm calls repository`() = runTest {
        coEvery { startServicesUseCase() } returns StartServiceResult.Starting
        viewModel = MainViewModel(mainRepository, startServicesUseCase, resourceHelper, logger)
        every { mainRepository.toggleFakeBpm() } returns true

        viewModel.toggleFakeBPM()

        verify(exactly = 1) { mainRepository.toggleFakeBpm() }
    }
}
