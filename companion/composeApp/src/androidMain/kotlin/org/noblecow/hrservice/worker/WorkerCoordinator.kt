package org.noblecow.hrservice.worker

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import co.touchlab.kermit.Logger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.noblecow.hrservice.data.repository.MainRepository
import org.noblecow.hrservice.data.repository.ServicesState

private const val TAG = "WorkerCoordinator"

/**
 * Coordinates MainWorker lifecycle with ServicesState.
 *
 * Observes the MainRepository's service state and automatically starts/stops
 * the MainWorker foreground service via WorkManager based on state transitions.
 */
interface WorkerCoordinator {
    /**
     * Start observing service state and managing Worker lifecycle.
     * Should be called once during application initialization.
     */
    fun start()
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
internal class WorkerCoordinatorImpl(
    private val workManager: WorkManager,
    private val mainRepository: MainRepository,
    private val appScope: CoroutineScope,
    logger: Logger
) : WorkerCoordinator {
    private val logger = logger.withTag(TAG)

    override fun start() {
        logger.d("Starting WorkerCoordinator")

        appScope.launch {
            // Monitor both WorkManager state and repository service state
            combine(
                workManager.getWorkInfosForUniqueWorkFlow(WORKER_NAME),
                mainRepository.appStateFlow
            ) { workInfoList, appState ->
                Pair(workInfoList.firstOrNull()?.state, appState.servicesState)
            }.collect { (workerState, servicesState) ->
                logger.d("Worker: $workerState, Services: $servicesState")

                val isWorkerActive = workerState == WorkInfo.State.RUNNING ||
                    workerState == WorkInfo.State.ENQUEUED
                val startWorker = workerState == null ||
                    workerState == WorkInfo.State.SUCCEEDED ||
                    workerState == WorkInfo.State.FAILED ||
                    workerState == WorkInfo.State.CANCELLED

                // Start worker when services are started and worker is not active
                if (servicesState == ServicesState.Started && !isWorkerActive && startWorker) {
                    logger.d("Starting MainWorker (current state: $workerState)")

                    // Use REPLACE policy to retry failed workers, KEEP for others
                    val policy = if (workerState == WorkInfo.State.FAILED ||
                        workerState == WorkInfo.State.CANCELLED
                    ) {
                        ExistingWorkPolicy.REPLACE
                    } else {
                        ExistingWorkPolicy.KEEP
                    }

                    val workRequest = OneTimeWorkRequestBuilder<MainWorker>().build()
                    workManager.enqueueUniqueWork(WORKER_NAME, policy, workRequest)
                }
            }
        }
    }
}
