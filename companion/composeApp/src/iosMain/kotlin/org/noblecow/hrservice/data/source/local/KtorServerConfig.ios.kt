package org.noblecow.hrservice.data.source.local

import co.touchlab.kermit.Logger
import io.ktor.server.application.Application

/**
 * iOS implementation of KtorServerConfig using CIO engine.
 *
 * Uses Kermit logger for request logging to ensure visibility in Xcode console.
 */
actual class KtorServerConfig {
    actual fun configureApplication(
        application: Application,
        logger: Logger?,
        getCurrentBpm: () -> Int?
    ) {
        // iOS uses manual logging via Kermit in route handlers for Xcode console visibility
        // No additional plugins needed
    }

    actual fun isLifecycleLoggingEnabled(): Boolean = true
}
