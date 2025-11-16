package org.noblecow.hrservice.di

import co.touchlab.kermit.Logger
import co.touchlab.kermit.loggerConfigInit
import co.touchlab.kermit.platformLogWriter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Provides Logger instances for dependency injection.
 */
@BindingContainer
object LoggerProviders {
    /**
     * Provides a default Logger instance using platform-specific log writer.
     *
     * @return Logger configured for the current platform
     */
    @Provides
    @SingleIn(AppScope::class)
    fun provideLogger(): Logger = Logger(loggerConfigInit(platformLogWriter()), "App")
}
