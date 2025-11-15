package org.noblecow.hrservice.data.repository

import co.touchlab.kermit.Logger
import co.touchlab.kermit.loggerConfigInit
import co.touchlab.kermit.platformLogWriter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.noblecow.hrservice.data.source.local.FakeBpmLocalDataSource

private const val TAG = "FakeBpmManager"

/**
 * Manages the lifecycle of fake BPM generation for testing purposes.
 */
@SingleIn(AppScope::class)
@Inject
internal class FakeBpmManager(
    private val scope: CoroutineScope,
    private val fakeBpmLocalDataSource: FakeBpmLocalDataSource,
    private val logger: Logger = Logger(loggerConfigInit(platformLogWriter()), TAG)
) {
    private var job: Job? = null

    /**
     * Starts fake BPM generation if not already running.
     * @return true if started successfully, false if already running
     */
    @Suppress("TooGenericExceptionCaught")
    fun start(): Boolean {
        if (job != null) return false

        job = scope.launch {
            try {
                fakeBpmLocalDataSource.run()
            } catch (e: Throwable) {
                logger.e(e.message ?: "Fake BPM generation failed", e)
            }
        }
        return true
    }

    /**
     * Stops fake BPM generation if running.
     * @return true if stopped successfully, false if not running
     */
    fun stop(): Boolean = job?.let {
        it.cancel()
        job = null
        true
    } ?: false

    /**
     * Toggles fake BPM generation on/off.
     * @return true if state changed, false otherwise
     */
    fun toggle(): Boolean = if (job == null) start() else stop()

    /**
     * Checks if fake BPM generation is currently running.
     */
    val isRunning: Boolean
        get() = job != null
}
