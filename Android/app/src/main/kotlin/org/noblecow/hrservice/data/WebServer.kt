package org.noblecow.hrservice.data

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.BaseApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import org.noblecow.hrservice.BuildConfig
import org.noblecow.hrservice.ui.GeneralError
import org.noblecow.hrservice.ui.PORT_LISTEN
import org.noblecow.hrservice.ui.Request
import org.slf4j.LoggerFactory

internal class WebServer @Inject constructor() {

    private val _webServerState = MutableStateFlow(WebServerState())
    val webServerState = _webServerState.asStateFlow()

    private var currentRequest: Request? = null
    private var ktorServer: BaseApplicationEngine? = null
    private val logger = LoggerFactory.getLogger(WebServer::class.simpleName)

    fun start(): Boolean {
        return if (ktorServer == null) {
            ktorServer = embeddedServer(Netty, PORT_LISTEN) {
                install(StatusPages) {
                    exception<Throwable> { _, e ->
                        handleKtorError(e)
                    }
                }
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
                        call.respond(Response(status = "OK"))
                    }

                    post("/") {
                        // throw IllegalStateException("foo") // testing
                        call.receive<Request>().run {
                            currentRequest = this
                            _webServerState.update {
                                it.copy(bpm = this.bpm)
                            }
                            call.respond(this)
                        }
                    }
                }
            }.start(wait = false)

            _webServerState.update {
                WebServerState(running = true)
            }

            true
        } else {
            false
        }
    }

    private fun handleKtorError(e: Throwable) {
        val message = e.localizedMessage ?: e::class.java.simpleName
        logger.error(message, e)
        _webServerState.update {
            WebServerState(bpm = 0, error = GeneralError.Ktor(message), running = true)
        }
    }

    fun stop() {
        if (stopKtor()) {
            _webServerState.update {
                WebServerState(bpm = 0, error = null, running = false)
            }
        }
    }

    private fun stopKtor(): Boolean {
        return ktorServer?.let {
            it.stop()
            ktorServer = null
            true
        } ?: false
    }
}

@Serializable
private data class Response(val status: String)
