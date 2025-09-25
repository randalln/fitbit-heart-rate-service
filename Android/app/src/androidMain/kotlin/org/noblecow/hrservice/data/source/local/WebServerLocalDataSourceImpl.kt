package org.noblecow.hrservice.data.source.local

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import org.noblecow.hrservice.data.util.PORT_LISTEN
import org.slf4j.LoggerFactory

@Serializable
private data class Response(
    val status: String
)

private const val TAG = "WebServerLocalDataSourceImpl"

@ContributesBinding(AppScope::class)
@Inject
@SingleIn(AppScope::class)
internal class WebServerLocalDataSourceImpl : WebServerLocalDataSource {

    // SharedFlow, because heart rate can be unchanged
    private val _bpmStream = MutableSharedFlow<Int>()
    override val bpmStream: SharedFlow<Int> = _bpmStream.asSharedFlow()
    private val _webServerState = MutableStateFlow(WebServerState())
    override val webServerState = _webServerState.asStateFlow()

    private var currentRequest: Request? = null
    private var ktorServer: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    private val logger = LoggerFactory.getLogger(TAG)

    override fun start() {
        if (ktorServer == null) {
            ktorServer = embeddedServer(Netty, port = PORT_LISTEN) {
                install(StatusPages) {
                    exception<Throwable> { call, e ->
                        val message = e.message ?: e::class.simpleName ?: ""
                        call.respondText(
                            message,
                            ContentType.Text.Plain,
                            HttpStatusCode.InternalServerError
                        )
                        logger.error(message, e)
                        _webServerState.update {
                            WebServerState(error = e, isRunning = true)
                        }
                    }
                }
                install(ContentNegotiation) {
                    json()
                }
                // if (BuildConfig.DEBUG) {
                install(CallLogging) {
                    format {
                        "Received POST request: ${currentRequest?.bpm}"
                    }
                }
                // }
                routing {
                    get("/") {
                        call.application.environment.log.info("GET /")
                        call.respond(Response(status = "OK"))
                    }

                    post("/") {
                        // check(false) { "fake error " } // testing
                        call.receive<Request>().run {
                            currentRequest = this
                            _bpmStream.emit(this.bpm)
                            call.respond(this)
                        }
                    }
                }
            }.start(wait = false)

            _webServerState.update {
                WebServerState(isRunning = true)
            }
        }
    }

    override fun stop() {
        if (stopKtor()) {
            _webServerState.update {
                WebServerState(error = null, isRunning = false)
            }
        }
    }

    private fun stopKtor(): Boolean = ktorServer?.let {
        it.stop()
        ktorServer = null
        true
    } == true
}
