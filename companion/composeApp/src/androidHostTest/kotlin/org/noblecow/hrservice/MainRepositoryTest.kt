package org.noblecow.hrservice

import app.cash.turbine.test
import co.touchlab.kermit.CommonWriter
import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.loggerConfigInit
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.noblecow.hrservice.data.repository.AppState
import org.noblecow.hrservice.data.repository.FakeBpmManager
import org.noblecow.hrservice.data.repository.MainRepository
import org.noblecow.hrservice.data.repository.MainRepositoryImpl
import org.noblecow.hrservice.data.repository.ServiceError
import org.noblecow.hrservice.data.repository.ServiceResult
import org.noblecow.hrservice.data.repository.ServicesState
import org.noblecow.hrservice.data.source.local.AdvertisingState
import org.noblecow.hrservice.data.source.local.BluetoothError
import org.noblecow.hrservice.data.source.local.BluetoothLocalDataSource
import org.noblecow.hrservice.data.source.local.BpmReading
import org.noblecow.hrservice.data.source.local.WebServerLocalDataSource
import org.noblecow.hrservice.data.source.local.WebServerState
import org.noblecow.hrservice.data.util.FAKE_BPM_START

@OptIn(ExperimentalKermitApi::class)
class MainRepositoryTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var mainRepository: MainRepository

    private val mockAdvertisingFlow: MutableStateFlow<AdvertisingState> =
        MutableStateFlow(AdvertisingState.Stopped)
    private val mockBpm = MutableStateFlow<BpmReading>(BpmReading(value = 0, sequenceNumber = 0))
    private val mockClientConnectionFlow: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val mockWebServerState: MutableStateFlow<WebServerState> =
        MutableStateFlow(WebServerState())

    private val bluetoothLocalDataSource: BluetoothLocalDataSource = mockk(relaxed = true) {
        every { advertisingState } returns mockAdvertisingFlow
        every { clientConnectedState } returns mockClientConnectionFlow
    }
    private val webServerLocalDataSource: WebServerLocalDataSource = mockk(relaxed = true) {
        every { webServerState } returns mockWebServerState
        every { bpmFlow } returns mockBpm
    }
    private val fakeBpmManager: FakeBpmManager = mockk(relaxed = true)

    @Before
    fun before() {
        val testScope = CoroutineScope(SupervisorJob() + mainDispatcherRule.testDispatcher)
        val logger = Logger(loggerConfigInit(CommonWriter()), "MainRepositoryTest")

        // Configure default delays for stop operations
        every { bluetoothLocalDataSource.stopAdvertising() } answers {
            Thread.sleep(1000)
        }
        coEvery { webServerLocalDataSource.stop() } coAnswers {
            delay(1000)
        }

        mainRepository = MainRepositoryImpl(
            bluetoothLocalDataSource,
            webServerLocalDataSource,
            fakeBpmManager,
            testScope,
            mainDispatcherRule.testDispatcher, // IO dispatcher for tests
            logger
        )
    }

    @Test
    fun `All four ServicesStates are emitted`() = runTest {
        every { bluetoothLocalDataSource.stopAdvertising() } answers {
            Thread.sleep(1000)
            mockAdvertisingFlow.value = AdvertisingState.Stopped
        }
        coEvery { webServerLocalDataSource.stop() } coAnswers {
            delay(1000)
            mockWebServerState.value = WebServerState(isReady = false)
        }

        mainRepository.appStateFlow.test {
            // SharedFlow with replay=1 emits the replayed value immediately
            assertEquals(AppState(servicesState = ServicesState.Stopped), awaitItem())
            mainRepository.startServices()
            mockAdvertisingFlow.value = AdvertisingState.Started
            assertEquals(AppState(servicesState = ServicesState.Starting), awaitItem())
            mockWebServerState.value = WebServerState(isReady = true)
            assertEquals(AppState(servicesState = ServicesState.Started), awaitItem())

            mainRepository.stopServices()
            verify { bluetoothLocalDataSource.stopAdvertising() }
            coVerify { webServerLocalDataSource.stop() }
            assertEquals(AppState(servicesState = ServicesState.Stopping), awaitItem())
            assertEquals(AppState(servicesState = ServicesState.Stopped), awaitItem())
        }
    }

    @Test
    fun `Web server stops when Bluetooth stops advertising`() = runTest {
        mainRepository.appStateFlow.test {
            assertEquals(AppState(servicesState = ServicesState.Stopped), awaitItem())
            mainRepository.startServices()
            mockAdvertisingFlow.value = AdvertisingState.Started
            assertEquals(AppState(servicesState = ServicesState.Starting), awaitItem())
            mockWebServerState.value = WebServerState(isReady = true)
            assertEquals(AppState(servicesState = ServicesState.Started), awaitItem())
            mockAdvertisingFlow.value = AdvertisingState.Stopped
            coVerify { webServerLocalDataSource.stop() }
            assertEquals(AppState(servicesState = ServicesState.Stopping), awaitItem())
            mockWebServerState.value = WebServerState(isReady = false)
            assertEquals(AppState(servicesState = ServicesState.Stopped), awaitItem())
        }
    }

    @Test
    fun `All services stop when the web server stops`() = runTest {
        mainRepository.appStateFlow.test {
            assertEquals(AppState(servicesState = ServicesState.Stopped), awaitItem())
            mainRepository.startServices()
            mockAdvertisingFlow.value = AdvertisingState.Started
            assertEquals(AppState(servicesState = ServicesState.Starting), awaitItem())
            mockWebServerState.value = WebServerState(isReady = true)
            assertEquals(AppState(servicesState = ServicesState.Started), awaitItem())

            mockWebServerState.value = WebServerState(isReady = false)
            assertEquals(AppState(servicesState = ServicesState.Stopping), awaitItem())
            verify { bluetoothLocalDataSource.stopAdvertising() }
            mockAdvertisingFlow.value = AdvertisingState.Stopped
            assertEquals(AppState(servicesState = ServicesState.Stopped), awaitItem())
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `BPM with the same value are sent over BLE`() = runTest {
        val job = launch {
            mainRepository.appStateFlow.collect {
                println("extra subscriber: $it")
            }
        }

        mainRepository.appStateFlow.test {
            assertEquals(AppState(servicesState = ServicesState.Stopped), awaitItem())
            mainRepository.startServices()
            mockWebServerState.value = WebServerState(isReady = true)
            assertEquals(AppState(servicesState = ServicesState.Starting), awaitItem())
            mockAdvertisingFlow.value = AdvertisingState.Started
            assertEquals(AppState(servicesState = ServicesState.Started), awaitItem())

            mockBpm.emit(BpmReading(value = FAKE_BPM_START, sequenceNumber = 0))
            assertEquals(
                AppState(bpm = BpmReading(FAKE_BPM_START, 0), servicesState = ServicesState.Started),
                awaitItem()
            )
            mockBpm.emit(BpmReading(value = FAKE_BPM_START, sequenceNumber = 1))
            assertEquals(
                AppState(bpm = BpmReading(FAKE_BPM_START, 1), servicesState = ServicesState.Started),
                awaitItem()
            )
        }

        advanceUntilIdle()
        job.cancel()
    }

    // ============================================================================
    // Error Handling Tests
    // ============================================================================

    @Test
    fun `startServices returns PermissionError when Bluetooth throws PermissionDenied`() = runTest {
        coEvery { bluetoothLocalDataSource.startAdvertising() } throws BluetoothError.PermissionDenied()

        val result = mainRepository.startServices()

        assertTrue(result is ServiceResult.Error)
        val error = (result as ServiceResult.Error).error
        assertTrue(error is ServiceError.PermissionError)
        assertTrue((error as ServiceError.PermissionError).permission.contains("Bluetooth"))
    }

    @Test
    fun `startServices returns BluetoothError when Bluetooth throws InvalidState`() = runTest {
        coEvery { bluetoothLocalDataSource.startAdvertising() } throws
            BluetoothError.InvalidState("Bluetooth unavailable")

        val result = mainRepository.startServices()

        assertTrue(result is ServiceResult.Error)
        val error = (result as ServiceResult.Error).error
        assertTrue(error is ServiceError.BluetoothError)
        assertTrue((error as ServiceError.BluetoothError).reason.contains("unavailable"))
    }

    @Test
    fun `startServices returns WebServerError when WebServer throws exception`() = runTest {
        coEvery { webServerLocalDataSource.start() } throws Exception("Port already in use")

        val result = mainRepository.startServices()

        assertTrue(result is ServiceResult.Error)
        val error = (result as ServiceResult.Error).error
        assertTrue(error is ServiceError.WebServerError)
        assertTrue((error as ServiceError.WebServerError).reason.contains("Port"))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `startServices returns AlreadyInState when already started`() = runTest {
        mockAdvertisingFlow.value = AdvertisingState.Started
        advanceUntilIdle()

        val result = mainRepository.startServices()

        assertTrue(result is ServiceResult.Error)
        val error = (result as ServiceResult.Error).error
        assertTrue(error is ServiceError.AlreadyInState)
    }

    @Test
    fun `startServices rolls back Bluetooth when WebServer fails`() = runTest {
        coEvery { webServerLocalDataSource.start() } throws Exception("Server failed")

        mainRepository.startServices()

        // Assert - Bluetooth should be stopped (rollback)
        coVerify { bluetoothLocalDataSource.startAdvertising() }
        verify { bluetoothLocalDataSource.stopAdvertising() }
    }

    @Test
    fun `startServices handles rollback failure gracefully`() = runTest {
        coEvery { webServerLocalDataSource.start() } throws Exception("Server failed")
        every { bluetoothLocalDataSource.stopAdvertising() } throws Exception("Rollback failed")

        val result = mainRepository.startServices()

        // Assert - Should still return error, not crash
        assertTrue(result is ServiceResult.Error)
        assertTrue((result as ServiceResult.Error).error is ServiceError.WebServerError)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `stopServices returns Success when all services stop cleanly`() = runTest {
        mockAdvertisingFlow.value = AdvertisingState.Started
        mockWebServerState.value = WebServerState(isReady = true)
        advanceUntilIdle()

        val result = mainRepository.stopServices()

        assertTrue(result is ServiceResult.Success)
        verify { fakeBpmManager.stop() }
        verify { bluetoothLocalDataSource.stopAdvertising() }
        coVerify { webServerLocalDataSource.stop() }
    }

    @Test
    fun `stopServices returns AlreadyInState when already stopped`() = runTest {
        val result = mainRepository.stopServices()

        assertTrue(result is ServiceResult.Error)
        val error = (result as ServiceResult.Error).error
        assertTrue(error is ServiceError.AlreadyInState)
        assertEquals("stopped", (error as ServiceError.AlreadyInState).state)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `stopServices returns BluetoothError when Bluetooth fails to stop`() = runTest {
        mockAdvertisingFlow.value = AdvertisingState.Started
        mockWebServerState.value = WebServerState(isReady = true)
        advanceUntilIdle()
        every { bluetoothLocalDataSource.stopAdvertising() } throws Exception("Bluetooth stop failed")

        val result = mainRepository.stopServices()

        assertTrue(result is ServiceResult.Error)
        val error = (result as ServiceResult.Error).error
        assertTrue(error is ServiceError.BluetoothError)
        assertTrue((error as ServiceError.BluetoothError).reason.contains("Bluetooth"))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `stopServices returns WebServerError when WebServer fails to stop`() = runTest {
        mockAdvertisingFlow.value = AdvertisingState.Started
        mockWebServerState.value = WebServerState(isReady = true)
        advanceUntilIdle()
        coEvery { webServerLocalDataSource.stop() } throws Exception("Server stop failed")

        val result = mainRepository.stopServices()

        assertTrue(result is ServiceResult.Error)
        val error = (result as ServiceResult.Error).error
        assertTrue(error is ServiceError.WebServerError)
        assertTrue((error as ServiceError.WebServerError).reason.contains("WebServer"))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `stopServices attempts to stop all services even when some fail`() = runTest {
        mockAdvertisingFlow.value = AdvertisingState.Started
        mockWebServerState.value = WebServerState(isReady = true)
        advanceUntilIdle()
        every { fakeBpmManager.stop() } throws Exception("FakeBPM failed")
        every { bluetoothLocalDataSource.stopAdvertising() } throws Exception("Bluetooth failed")
        coEvery { webServerLocalDataSource.stop() } throws Exception("Server failed")

        val result = mainRepository.stopServices()

        // Assert - All stop methods should be called despite failures
        verify { fakeBpmManager.stop() }
        verify { bluetoothLocalDataSource.stopAdvertising() }
        coVerify { webServerLocalDataSource.stop() }

        // Should return error with count of failures
        assertTrue(result is ServiceResult.Error)
    }

    @Test
    fun `BPM notification error does not crash background coroutine`() = runTest {
        every { bluetoothLocalDataSource.notifyHeartRate(any()) } throws Exception("Bluetooth disconnected")

        mainRepository.appStateFlow.test {
            awaitItem()
            mainRepository.startServices()
            mockAdvertisingFlow.value = AdvertisingState.Started
            mockWebServerState.value = WebServerState(isReady = true)
            awaitItem()
            awaitItem()

            // Emit BPM - should log error but not crash
            mockBpm.emit(BpmReading(value = 75, sequenceNumber = 1))
            awaitItem() // Should still update state

            // Verify notification was attempted
            verify { bluetoothLocalDataSource.notifyHeartRate(75) }
        }
    }
}
