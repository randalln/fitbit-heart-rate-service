package org.noblecow.hrservice.data.source.local

import io.ktor.server.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer

/**
 * iOS implementation using CIO engine for lightweight coroutine-based I/O.
 *
 * Note: reuseAddress is enabled to bypass Darwin's TIME_WAIT port binding restriction.
 * This allows immediate port rebinding after server stop, eliminating the need for
 * the 8-second delay that was previously required.
 */
actual fun createEmbeddedServer(
    port: Int,
    configure: Application.() -> Unit
): Any = embeddedServer(
    factory = CIO,
    configure = {
        // Enable SO_REUSEADDR to allow immediate port rebinding
        reuseAddress = true

        // Configure the connector with the specified port
        connector {
            this.port = port
        }
    },
    module = configure
)
