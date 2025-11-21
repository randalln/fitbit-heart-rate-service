package org.noblecow.hrservice.data.source.local

import co.touchlab.kermit.Logger
import io.ktor.events.Events
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStarting
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable

@Serializable
private data class Response(val status: String)

private const val BPM_EMIT_TIMEOUT_MS = 5000L

/**
 * Shared configuration and routing logic for the Ktor web server.
 *
 * Handles common server setup including content negotiation, routing,
 * and BPM request processing. Platform-specific configuration is delegated
 * to [KtorServerConfig].
 */
internal class KtorServerManager(
    private val config: KtorServerConfig,
    private val logger: Logger
) {
    private val currentRequest = AtomicRef<Request?>(null)

    /**
     * Configure the Ktor application with content negotiation, routing, and plugins.
     *
     * @param application The Ktor application to configure
     * @param onBpmReceived Callback invoked when a BPM value is received via POST
     */
    fun configureApplication(
        application: Application,
        onBpmReceived: suspend (Int) -> Unit
    ) {
        application.apply {
            // Install content negotiation for JSON
            install(ContentNegotiation) {
                json()
            }

            // Apply platform-specific configuration (e.g., CallLogging on Android)
            config.configureApplication(this, logger) { currentRequest.get()?.bpm }

            // Configure routes
            routing {
                get("/") {
                    logger.d("GET /")
                    call.respond(Response(status = "OK"))
                }

                post("/") {
                    call.receive<Request>().run {
                        currentRequest.set(this)
                        logger.d("Received POST request: $bpm")

                        // Attempt to emit BPM with timeout
                        // Swallowed exceptions are intentional - we respond to HTTP request even if emit fails
                        @Suppress("SwallowedException", "TooGenericExceptionCaught")
                        try {
                            withTimeout(BPM_EMIT_TIMEOUT_MS) {
                                onBpmReceived(this@run.bpm)
                            }
                        } catch (e: Exception) {
                            // Swallow and log non-cancellation exceptions for resilience
                            // The HTTP request succeeds even if BPM notification fails
                            when (e) {
                                is kotlinx.coroutines.CancellationException -> throw e
                                else -> logger.e("Failed to emit BPM", e)
                            }
                        }
                        call.respond(this)
                    }
                }
            }
        }
    }

    /**
     * Setup reactive state monitoring for server lifecycle events.
     *
     * @param monitor The Ktor events monitor
     * @param onStateChange Callback invoked when server state changes
     */
    fun setupStateMonitoring(
        monitor: Events,
        onStateChange: (Any) -> Unit
    ) {
        monitor.apply {
            subscribe(ApplicationStarting) { application ->
                logger.d("Server is starting")
                onStateChange(ApplicationStarting)
            }
            subscribe(ApplicationStarted) { application ->
                logger.d("Server is started")
                onStateChange(ApplicationStarted)
            }
            subscribe(ApplicationStopping) { application ->
                logger.d("Server is stopping")
                onStateChange(ApplicationStopping)
            }
            subscribe(ApplicationStopped) { application ->
                logger.d("Server is stopped")
                onStateChange(ApplicationStopped)
            }
        }
    }
}
