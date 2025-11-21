package org.noblecow.hrservice.data.source.local

import co.touchlab.kermit.Logger
import io.ktor.server.application.Application

/**
 * Platform-specific configuration for the Ktor embedded server.
 *
 * Provides server engine type, logging configuration, and platform-specific plugins.
 */
expect class KtorServerConfig() {
    /**
     * Configure the Ktor application with platform-specific plugins.
     *
     * @param application The Ktor application to configure
     * @param logger Optional logger for request/response logging
     * @param getCurrentBpm Lambda to get current BPM value for logging context
     */
    fun configureApplication(
        application: Application,
        logger: Logger?,
        getCurrentBpm: () -> Int?
    )

    /**
     * Determine if lifecycle logs should be enabled based on platform debug settings.
     *
     * @return true if lifecycle logging is enabled (e.g., BuildConfig.DEBUG on Android)
     */
    fun isLifecycleLoggingEnabled(): Boolean
}
