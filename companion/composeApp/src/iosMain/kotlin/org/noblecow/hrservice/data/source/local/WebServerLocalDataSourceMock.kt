package org.noblecow.hrservice.data.source.local

import co.touchlab.kermit.Logger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Mock implementation of WebServerLocalDataSource for iOS.
 *
 * This mock simulates an HTTP server that receives heart rate data. When started,
 * it automatically generates fake BPM readings every 2-3 seconds to simulate
 * incoming HTTP requests.
 *
 * @property appScope Scope for background coroutines
 * @property logger Logger for debugging
 */
@Inject
@SingleIn(AppScope::class)
// @ContributesBinding(AppScope::class)
internal class WebServerLocalDataSourceMock(
    private val appScope: CoroutineScope,
    private val logger: Logger
) : WebServerLocalDataSource {

    private val _bpmFlow = MutableSharedFlow<BpmReading>(
        replay = 0,
        extraBufferCapacity = 1
    )
    override val bpmFlow: SharedFlow<BpmReading> = _bpmFlow.asSharedFlow()

    private val _webServerState = MutableStateFlow(WebServerState(isReady = false))
    override val webServerState: StateFlow<WebServerState> = _webServerState.asStateFlow()

    private var bpmGeneratorJob: Job? = null
    private var sequenceNumber = 0

    /**
     * Simulates starting the web server.
     * Begins generating fake BPM readings every 2-3 seconds.
     */
    override suspend fun start() {
        logger.d("iOS Mock: start()")
        if (_webServerState.value.isReady) {
            logger.d("iOS Mock: Web server already started")
            return
        }

        _webServerState.value = WebServerState(isReady = true)

        // Start background coroutine to generate fake BPM readings
        bpmGeneratorJob?.cancel()
        bpmGeneratorJob = appScope.launch {
            logger.d("iOS Mock: Starting BPM generator")
            while (isActive) {
                // delay(Random.nextLong(2000, 3000)) // 2-3 seconds
                delay(1000)
                val fakeBpm = Random.nextInt(60, 100) // Random BPM between 60-100
                val reading = BpmReading(
                    value = fakeBpm,
                    sequenceNumber = ++sequenceNumber
                )
                logger.d("iOS Mock: Emitting fake BPM reading: $reading")
                _bpmFlow.emit(reading)
            }
        }
    }

    /**
     * Simulates stopping the web server.
     * Stops generating fake BPM readings.
     */
    override suspend fun stop() {
        logger.d("iOS Mock: stop()")
        if (!_webServerState.value.isReady) {
            logger.d("iOS Mock: Web server already stopped")
            return
        }

        bpmGeneratorJob?.cancel()
        bpmGeneratorJob = null
        _webServerState.value = WebServerState(isReady = false)
        logger.d("iOS Mock: BPM generator stopped")
    }
}
