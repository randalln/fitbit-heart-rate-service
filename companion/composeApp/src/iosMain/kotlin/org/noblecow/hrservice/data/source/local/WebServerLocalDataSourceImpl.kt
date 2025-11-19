package org.noblecow.hrservice.data.source.local

import co.touchlab.kermit.Logger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.ApplicationStopping
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.noblecow.hrservice.data.util.PORT_LISTEN

private const val STOP_GRACE_PERIOD_MS = 1000L
private const val STOP_TIMEOUT_MS = 2000L
private const val TAG = "WebServerLocalDataSourceImpl"

/**
 * iOS implementation of WebServerLocalDataSource using CIO engine.
 *
 * Delegates common server logic to [KtorServerManager] while handling
 * iOS-specific lifecycle and state management. Uses Kermit logger for
 * Xcode console visibility.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
internal class WebServerLocalDataSourceImpl(
    appScope: CoroutineScope,
    logger: Logger
) : WebServerLocalDataSource {
    private val logger = logger.withTag(TAG)

    private val serverManager = KtorServerManager(
        config = KtorServerConfig(),
        logger = this.logger
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

    @Suppress("TooGenericExceptionCaught")
    override suspend fun start() {
        serverMutex.withLock {
            try {
                if (ktorServer == null || internalKtorState == ApplicationStopped) {
                    ktorServer = createEmbeddedServer(PORT_LISTEN) {
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
                            _webServerState.update {
                                when (state) {
                                    ApplicationStarted ->
                                        WebServerState(isReady = true)

                                    ApplicationStopping ->
                                        WebServerState(isReady = false)

                                    else -> it
                                }
                            }
                        }
                    }
                    (ktorServer as? io.ktor.server.engine.EmbeddedServer<*, *>)?.start(wait = false)
                        ?: error("Failed to start server: invalid server instance")

                    // Clear any previous errors on successful start
                    _webServerState.update { it.copy(error = null) }
                }
            } catch (e: Exception) {
                ktorServer = null
                _webServerState.update {
                    WebServerState(error = e, isReady = false)
                }
                throw e
            }
        }
    }

    override suspend fun stop() {
        serverMutex.withLock {
            (ktorServer as? io.ktor.server.engine.EmbeddedServer<*, *>)?.let { server ->
                if (internalKtorState != ApplicationStopped) {
                    server.stop(STOP_GRACE_PERIOD_MS, STOP_TIMEOUT_MS)
                }
            }
            ktorServer = null
        }
    }
}
