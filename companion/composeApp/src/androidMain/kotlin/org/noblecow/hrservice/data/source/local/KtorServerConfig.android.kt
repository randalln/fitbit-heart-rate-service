package org.noblecow.hrservice.data.source.local

import co.touchlab.kermit.Logger
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import org.noblecow.hrservice.BuildConfig

/**
 * Android implementation of KtorServerConfig using Netty engine.
 *
 * Includes CallLogging plugin in debug builds for structured request/response logging.
 */
actual class KtorServerConfig {
    actual fun configureApplication(
        application: Application,
        logger: Logger?,
        getCurrentBpm: () -> Int?
    ) {
        if (BuildConfig.DEBUG) {
            application.install(CallLogging) {
                format {
                    "Received POST request: ${getCurrentBpm()}"
                }
            }
        }
    }

    actual fun isLifecycleLoggingEnabled(): Boolean = BuildConfig.DEBUG
}
