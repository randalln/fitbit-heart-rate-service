package org.noblecow.hrservice.data.source.local

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

/**
 * Android implementation using Netty engine for better performance and stability.
 */
actual fun createEmbeddedServer(
    port: Int,
    configure: Application.() -> Unit
): Any = embeddedServer(
    Netty,
    port = port,
    module = configure
)
