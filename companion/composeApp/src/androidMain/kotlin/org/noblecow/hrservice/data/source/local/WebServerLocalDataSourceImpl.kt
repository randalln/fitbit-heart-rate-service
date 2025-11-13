package org.noblecow.hrservice.data.source.local

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.events.Events
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStarting
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import org.noblecow.hrservice.BuildConfig
import org.noblecow.hrservice.data.util.PORT_LISTEN

@Serializable
private data class Response(val status: String)

private const val BPM_EMIT_TIMEOUT_MS = 5000L
private const val STOP_GRACE_PERIOD_MS = 1000L
private const val STOP_TIMEOUT_MS = 2000L

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
internal class WebServerLocalDataSourceImpl : WebServerLocalDataSource {

    // SharedFlow because heart rate can stay the same
    private val _bpmFlow = MutableSharedFlow<Int>()
    override val bpmFlow: SharedFlow<Int> = _bpmFlow.asSharedFlow()
    private var internalKtorState = ApplicationStopped
    private val _webServerState = MutableStateFlow(WebServerState(isReady = false))
    override val webServerState = _webServerState.asStateFlow()

    private var currentRequest: Request? = null
    private var ktorServer: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private val serverMutex = Mutex()

    @Suppress("TooGenericExceptionCaught")
    override suspend fun start() {
        serverMutex.withLock {
            try {
                if (ktorServer == null || internalKtorState == ApplicationStopped) {
                    ktorServer = embeddedServer(Netty, port = PORT_LISTEN) {
                        install(ContentNegotiation) {
                            json()
                        }
                        if (BuildConfig.DEBUG) {
                            install(CallLogging) {
                                format {
                                    "Received POST request: ${currentRequest?.bpm}"
                                }
                            }
                        }
                        routing {
                            get("/") {
                                call.application.environment.log.info("GET /")
                                call.respond(Response(status = "OK"))
                            }

                            post("/") {
                                // check(false) { "fake error " } // testing
                                call.receive<Request>().run {
                                    currentRequest = this
                                    try {
                                        withTimeout(BPM_EMIT_TIMEOUT_MS) {
                                            _bpmFlow.emit(this@run.bpm)
                                        }
                                    } catch (e: Exception) {
                                        call.application.environment.log.error("Failed to emit BPM", e)
                                    }
                                    call.respond(this)
                                }
                            }
                        }
                        setupWebServerStateFlow(monitor)
                    }.start(wait = false)
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

    private fun setupWebServerStateFlow(monitor: Events) {
        internalKtorState = ApplicationStopped
        monitor.apply {
            subscribe(ApplicationStarting) { application ->
                if (BuildConfig.DEBUG) {
                    application.environment.log.info("Server is starting")
                }
                internalKtorState = ApplicationStarting
            }
            subscribe(ApplicationStarted) { application ->
                if (BuildConfig.DEBUG) {
                    application.environment.log.info("Server is started")
                }
                _webServerState.update {
                    WebServerState(isReady = true)
                }
                internalKtorState = ApplicationStarted
            }
            subscribe(ApplicationStopping) { application ->
                if (BuildConfig.DEBUG) {
                    application.environment.log.info("Server is stopping")
                }
                _webServerState.update {
                    WebServerState(isReady = false)
                }
                internalKtorState = ApplicationStopping
            }
            subscribe(ApplicationStopped) { application ->
                if (BuildConfig.DEBUG) {
                    application.environment.log.info("Server is stopped")
                }
                internalKtorState = ApplicationStopped
            }
        }
    }

    override suspend fun stop() {
        serverMutex.withLock {
            ktorServer?.let { ktorServer ->
                if (internalKtorState != ApplicationStopped) {
                    ktorServer.stop(STOP_GRACE_PERIOD_MS, STOP_TIMEOUT_MS)
                }
            }
            ktorServer = null
        }
    }
}
