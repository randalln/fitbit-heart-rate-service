package org.noblecow.hrservice.worker

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.cash.turbine.test
import co.touchlab.kermit.CommonWriter
import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.loggerConfigInit
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.noblecow.hrservice.MainDispatcherRule
import org.noblecow.hrservice.data.repository.AppState
import org.noblecow.hrservice.data.repository.MainRepository
import org.noblecow.hrservice.data.repository.ServicesState

@OptIn(ExperimentalKermitApi::class, ExperimentalCoroutinesApi::class)
class WorkerCoordinatorTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var workManager: WorkManager
    private lateinit var mainRepository: MainRepository
    private lateinit var testScope: CoroutineScope
    private lateinit var logger: Logger
    private lateinit var coordinator: WorkerCoordinator

    private lateinit var appStateFlow: MutableStateFlow<AppState>
    private lateinit var workInfoFlow: MutableStateFlow<List<WorkInfo>>

    @Before
    fun setup() {
        workManager = mockk(relaxed = true)
        logger = Logger(loggerConfigInit(CommonWriter()), "WorkerCoordinatorTest")
        testScope = CoroutineScope(SupervisorJob() + mainDispatcherRule.testDispatcher)

        appStateFlow = MutableStateFlow(AppState(servicesState = ServicesState.Stopped))
        workInfoFlow = MutableStateFlow(emptyList())

        mainRepository = mockk(relaxed = true) {
            every { appStateFlow } returns this@WorkerCoordinatorTest.appStateFlow
        }

        every { workManager.getWorkInfosForUniqueWorkFlow(WORKER_NAME) } returns workInfoFlow

        coordinator = WorkerCoordinatorImpl(
            workManager = workManager,
            mainRepository = mainRepository,
            appScope = testScope,
            logger = logger
        )
    }

    @Test
    fun `start enqueues worker when services start and worker is not running`() = runTest {
        coordinator.start()
        advanceUntilIdle()

        // Transition to Started state
        appStateFlow.emit(AppState(servicesState = ServicesState.Started))
        advanceUntilIdle()

        // Verify worker was enqueued
        verify {
            workManager.enqueueUniqueWork(
                eq(WORKER_NAME),
                any<ExistingWorkPolicy>(),
                any<OneTimeWorkRequest>()
            )
        }
    }

    @Test
    fun `start does not enqueue worker when services are stopped`() = runTest {
        coordinator.start()
        advanceUntilIdle()

        // Services remain stopped
        appStateFlow.emit(AppState(servicesState = ServicesState.Stopped))
        advanceUntilIdle()

        // Verify worker was NOT enqueued
        verify(exactly = 0) {
            workManager.enqueueUniqueWork(any(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        }
    }

    @Test
    fun `start does not enqueue worker when worker is already running`() = runTest {
        // Setup worker as already running
        val runningWorkInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.RUNNING
        }
        workInfoFlow.value = listOf(runningWorkInfo)

        coordinator.start()
        advanceUntilIdle()

        // Transition to Started state
        appStateFlow.emit(AppState(servicesState = ServicesState.Started))
        advanceUntilIdle()

        // Verify worker was NOT enqueued (already running)
        verify(exactly = 0) {
            workManager.enqueueUniqueWork(any(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        }
    }

    @Test
    fun `start does not enqueue worker when worker is enqueued`() = runTest {
        // Setup worker as enqueued
        val enqueuedWorkInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.ENQUEUED
        }
        workInfoFlow.value = listOf(enqueuedWorkInfo)

        coordinator.start()
        advanceUntilIdle()

        // Transition to Started state
        appStateFlow.emit(AppState(servicesState = ServicesState.Started))
        advanceUntilIdle()

        // Verify worker was NOT enqueued (already enqueued)
        verify(exactly = 0) {
            workManager.enqueueUniqueWork(any(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        }
    }

    @Test
    fun `start uses REPLACE policy when worker previously failed`() = runTest {
        // Setup worker as failed
        val failedWorkInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.FAILED
        }
        workInfoFlow.value = listOf(failedWorkInfo)

        coordinator.start()
        advanceUntilIdle()

        // Transition to Started state
        appStateFlow.emit(AppState(servicesState = ServicesState.Started))
        advanceUntilIdle()

        // Verify worker was enqueued with REPLACE policy
        val policySlot = slot<ExistingWorkPolicy>()
        verify {
            workManager.enqueueUniqueWork(
                eq(WORKER_NAME),
                capture(policySlot),
                any<OneTimeWorkRequest>()
            )
        }
        assertEquals(ExistingWorkPolicy.REPLACE, policySlot.captured)
    }

    @Test
    fun `start uses REPLACE policy when worker was cancelled`() = runTest {
        // Setup worker as cancelled
        val cancelledWorkInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.CANCELLED
        }
        workInfoFlow.value = listOf(cancelledWorkInfo)

        coordinator.start()
        advanceUntilIdle()

        // Transition to Started state
        appStateFlow.emit(AppState(servicesState = ServicesState.Started))
        advanceUntilIdle()

        // Verify worker was enqueued with REPLACE policy
        val policySlot = slot<ExistingWorkPolicy>()
        verify {
            workManager.enqueueUniqueWork(
                eq(WORKER_NAME),
                capture(policySlot),
                any<OneTimeWorkRequest>()
            )
        }
        assertEquals(ExistingWorkPolicy.REPLACE, policySlot.captured)
    }

    @Test
    fun `start uses KEEP policy when worker succeeded`() = runTest {
        // Setup worker as succeeded
        val succeededWorkInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.SUCCEEDED
        }
        workInfoFlow.value = listOf(succeededWorkInfo)

        coordinator.start()
        advanceUntilIdle()

        // Transition to Started state
        appStateFlow.emit(AppState(servicesState = ServicesState.Started))
        advanceUntilIdle()

        // Verify worker was enqueued with KEEP policy
        val policySlot = slot<ExistingWorkPolicy>()
        verify {
            workManager.enqueueUniqueWork(
                eq(WORKER_NAME),
                capture(policySlot),
                any<OneTimeWorkRequest>()
            )
        }
        assertEquals(ExistingWorkPolicy.KEEP, policySlot.captured)
    }

    @Test
    fun `start enqueues worker after it completes and services restart`() = runTest {
        // Start with completed worker
        val succeededWorkInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.SUCCEEDED
        }
        workInfoFlow.value = listOf(succeededWorkInfo)

        coordinator.start()
        advanceUntilIdle()

        // Start services - worker should be enqueued
        appStateFlow.emit(AppState(servicesState = ServicesState.Started))
        advanceUntilIdle()

        verify(exactly = 1) {
            workManager.enqueueUniqueWork(any(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        }

        // Now simulate worker running
        val runningWorkInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.RUNNING
        }
        workInfoFlow.value = listOf(runningWorkInfo)
        advanceUntilIdle()

        // Stop and restart services
        appStateFlow.emit(AppState(servicesState = ServicesState.Stopped))
        advanceUntilIdle()

        // Worker completes
        workInfoFlow.value = listOf(succeededWorkInfo)
        advanceUntilIdle()

        // Restart services
        appStateFlow.emit(AppState(servicesState = ServicesState.Started))
        advanceUntilIdle()

        // Verify worker was enqueued again
        verify(exactly = 2) {
            workManager.enqueueUniqueWork(any(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        }
    }

    @Test
    fun `start handles empty work info list`() = runTest {
        // No worker exists yet
        workInfoFlow.value = emptyList()

        coordinator.start()
        advanceUntilIdle()

        // Transition to Started state
        appStateFlow.emit(AppState(servicesState = ServicesState.Started))
        advanceUntilIdle()

        // Verify worker was enqueued
        verify {
            workManager.enqueueUniqueWork(
                eq(WORKER_NAME),
                any<ExistingWorkPolicy>(),
                any<OneTimeWorkRequest>()
            )
        }
    }

    @Test
    fun `start monitors state changes continuously`() = runTest {
        coordinator.start()
        advanceUntilIdle()

        appStateFlow.test {
            // Initial state
            assertEquals(ServicesState.Stopped, awaitItem().servicesState)

            // Start services
            appStateFlow.emit(AppState(servicesState = ServicesState.Started))
            assertEquals(ServicesState.Started, awaitItem().servicesState)
            advanceUntilIdle()

            // Stop services
            appStateFlow.emit(AppState(servicesState = ServicesState.Stopped))
            assertEquals(ServicesState.Stopped, awaitItem().servicesState)
            advanceUntilIdle()

            // Start again
            appStateFlow.emit(AppState(servicesState = ServicesState.Started))
            assertEquals(ServicesState.Started, awaitItem().servicesState)
            advanceUntilIdle()

            // Verify worker was enqueued twice (once for each start)
            verify(atLeast = 2) {
                workManager.enqueueUniqueWork(any(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
            }
        }
    }
}
