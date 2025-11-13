package org.noblecow.hrservice

import app.cash.turbine.test
import co.touchlab.kermit.CommonWriter
import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.loggerConfigInit
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.noblecow.hrservice.data.repository.AppState
import org.noblecow.hrservice.data.repository.MainRepository
import org.noblecow.hrservice.data.repository.MainRepositoryImpl
import org.noblecow.hrservice.data.repository.ServicesState
import org.noblecow.hrservice.data.source.local.AdvertisingState
import org.noblecow.hrservice.data.source.local.BluetoothLocalDataSource
import org.noblecow.hrservice.data.source.local.FakeBpmLocalDataSource
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
    private val mockBpm = MutableSharedFlow<Int>()
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
    private val fakeBpmLocalDataSource: FakeBpmLocalDataSource = mockk()

    @Before
    fun before() {
        val testScope = CoroutineScope(SupervisorJob() + mainDispatcherRule.testDispatcher)
        val logger = Logger(loggerConfigInit(CommonWriter()), "MainRepositoryTest")

        mainRepository = MainRepositoryImpl(
            bluetoothLocalDataSource,
            webServerLocalDataSource,
            fakeBpmLocalDataSource,
            testScope,
            logger
        )
    }

    @Test
    fun `All four ServicesStates are emitted`() = runTest {
        mainRepository.appStateFlow.test {
            assertEquals(AppState(servicesState = ServicesState.Stopped), awaitItem())
            mainRepository.startServices()
            mockAdvertisingFlow.value = AdvertisingState.Started
            assertEquals(AppState(servicesState = ServicesState.Starting), awaitItem())
            mockWebServerState.value = WebServerState(isReady = true)
            assertEquals(AppState(servicesState = ServicesState.Started), awaitItem())

            mainRepository.stopServices()
            verify { bluetoothLocalDataSource.stopAdvertising() }
            coVerify { webServerLocalDataSource.stop() }
            mockAdvertisingFlow.value = AdvertisingState.Stopped
            assertEquals(AppState(servicesState = ServicesState.Stopping), awaitItem())
            mockWebServerState.value = WebServerState(isReady = false)
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
            verify { bluetoothLocalDataSource.stopAdvertising() }
            assertEquals(AppState(servicesState = ServicesState.Stopping), awaitItem())
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
            awaitItem()
            mainRepository.startServices()
            mockWebServerState.value = WebServerState(isReady = true)
            awaitItem()
            mockAdvertisingFlow.value = AdvertisingState.Started
            assertEquals(AppState(servicesState = ServicesState.Started), awaitItem())

            mockBpm.emit(FAKE_BPM_START)
            assertEquals(
                AppState(bpm = FAKE_BPM_START, servicesState = ServicesState.Started),
                awaitItem()
            )
            mockBpm.emit(FAKE_BPM_START)
            verify(exactly = 2) { bluetoothLocalDataSource.notifyHeartRate(FAKE_BPM_START) }
        }

        advanceUntilIdle()
        job.cancel()
    }
}
