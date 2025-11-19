package org.noblecow.hrservice.data.source.local

import io.ktor.server.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer

/**
 * iOS implementation using CIO engine for lightweight coroutine-based I/O.
 */
actual fun createEmbeddedServer(
    port: Int,
    configure: Application.() -> Unit
): Any = embeddedServer(
    CIO,
    port = port,
    module = configure
)
