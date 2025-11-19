package org.noblecow.hrservice.data.source.local

import io.ktor.server.application.Application

/**
 * Platform-specific factory for creating Ktor embedded servers.
 *
 * Android uses Netty engine, iOS uses CIO engine.
 * Returns Any to avoid expect/actual type compatibility issues.
 */
expect fun createEmbeddedServer(
    port: Int,
    configure: Application.() -> Unit
): Any
