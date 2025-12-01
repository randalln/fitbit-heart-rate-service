package org.noblecow.hrservice.data.source.local

import co.touchlab.kermit.Logger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.engine.EmbeddedServer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.noblecow.hrservice.data.util.PORT_LISTEN
import org.noblecow.hrservice.di.IoDispatcher

private const val STOP_GRACE_PERIOD_MS = 1000L
private const val STOP_TIMEOUT_MS = 2000L
private const val READY_WAIT_MAX_ATTEMPTS = 25 // Wait up to 5 seconds for server to reach ready state
private const val READY_WAIT_DELAY_MS = 200L
private const val TAG = "WebServerLocalDataSourceImpl"

/**
 * Multiplatform implementation of WebServerLocalDataSource
 *
 * Uses platform-specific Ktor engines (Netty on Android, CIO on iOS) configured via
 * [createEmbeddedServer] expect function. Delegates common server logic to [KtorServerManager].
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
internal class WebServerLocalDataSourceImpl(
    appScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    defaultLogger: Logger
) : WebServerLocalDataSource {
    val logger = defaultLogger.withTag(TAG)

    private val serverManager = KtorServerManager(
        config = KtorServerConfig(),
        logger = logger
    )

    // BpmReading with sequence number ensures each emission is unique even if BPM value repeats
    private var bpmSequenceNumber = 0
    private val _bpmFlow = MutableSharedFlow<BpmReading>()
    override val bpmFlow: SharedFlow<BpmReading> = _bpmFlow
        .shareIn(
            scope = appScope,
            started = SharingStarted.WhileSubscribed()
        )

    private var internalKtorState: Any = ApplicationStopped
    private val _webServerState = MutableStateFlow(WebServerState(isReady = false))
    override val webServerState = _webServerState.asStateFlow()

    private var ktorServer: Any? = null
    private val serverMutex = Mutex()
    private var isStartupInProgress = false

    @Suppress("TooGenericExceptionCaught", "LongMethod", "CyclomaticComplexMethod")
    override suspend fun start() = withContext(ioDispatcher) {
        serverMutex.withLock {
            if (ktorServer != null && internalKtorState != ApplicationStopped) {
                logger.d { "Server already running, skipping start" }
                return@withLock
            }

            // Set flag to suppress state updates during startup
            isStartupInProgress = true

            try {
                val serverInstance = createEmbeddedServer(PORT_LISTEN) {
                    serverManager.configureApplication(this) { bpm ->
                        _bpmFlow.emit(
                            BpmReading(
                                value = bpm,
                                sequenceNumber = ++bpmSequenceNumber
                            )
                        )
                    }
                    serverManager.setupStateMonitoring(monitor) { state ->
                        internalKtorState = state
                        // Only update public state if not in startup phase
                        if (!isStartupInProgress) {
                            _webServerState.update {
                                when (state) {
                                    ApplicationStarted ->
                                        WebServerState(isReady = true, error = null)

                                    ApplicationStopping ->
                                        WebServerState(isReady = false, error = null)

                                    else -> it
                                }
                            }
                        }
                    }
                }

                // Start the server with wait = false (non-blocking)
                val server = serverInstance as? EmbeddedServer<*, *>
                    ?: error("Failed to start server: invalid server instance")

                // Start the server with wait=false to avoid blocking indefinitely
                // The ApplicationStarted event will be fired when the server is ready
                try {
                    server.start(wait = false)
                } catch (e: Exception) {
                    logger.e(e) { "Server.start() threw exception" }
                    // Clean up the failed server instance
                    try {
                        server.stop(0, 0)
                    } catch (stopException: Exception) {
                        logger.w(stopException) { "Failed to stop partially started server" }
                    }
                    throw e
                }

                // Wait for server to be ready (ApplicationStarted event)
                // The state monitoring callback will update internalKtorState
                var readyWaitAttempts = 0
                while (internalKtorState != ApplicationStarted && readyWaitAttempts < READY_WAIT_MAX_ATTEMPTS) {
                    delay(READY_WAIT_DELAY_MS)
                    readyWaitAttempts++
                }

                if (internalKtorState != ApplicationStarted) {
                    // Server didn't reach ApplicationStarted state within timeout
                    val timeoutMs = READY_WAIT_MAX_ATTEMPTS * READY_WAIT_DELAY_MS
                    val errorMessage = "Server failed to reach ready state after ${timeoutMs}ms"
                    val error = Exception(errorMessage)
                    logger.e(error) { "Server start verification failed" }
                    // Clean up the failed server
                    try {
                        server.stop(0, 0)
                    } catch (stopException: Exception) {
                        logger.w(stopException) { "Failed to stop server that didn't reach ready state" }
                    }
                    throw error
                }

                // Success! Save the server instance
                ktorServer = serverInstance
                isStartupInProgress = false
                // Explicitly update state on success
                _webServerState.update {
                    WebServerState(isReady = true, error = null)
                }
                logger.d { "Server started successfully" }
            } catch (e: Exception) {
                // Startup failed - update state with error
                ktorServer = null
                logger.e(e) { "Failed to start server" }
                _webServerState.update {
                    WebServerState(error = e, isReady = false)
                }
                throw e
            } finally {
                // Ensure flag is always cleared, even if exception occurs
                isStartupInProgress = false
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun stop() {
        serverMutex.withLock {
            (ktorServer as? EmbeddedServer<*, *>)?.let { server ->
                if (internalKtorState != ApplicationStopped) {
                    logger.d { "Stopping server, waiting for graceful shutdown" }
                    try {
                        server.stop(STOP_GRACE_PERIOD_MS, STOP_TIMEOUT_MS)
                    } catch (e: Exception) {
                        logger.w(e) { "Exception during server.stop()" }
                    }
                    ktorServer = null
                    internalKtorState = ApplicationStopped
                }
            }
            if (ktorServer != null) {
                ktorServer = null
                internalKtorState = ApplicationStopped
            }

            logger.d { "Server stopped" }
        }
    }
}
