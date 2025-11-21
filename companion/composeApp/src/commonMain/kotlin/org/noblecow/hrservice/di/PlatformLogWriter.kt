package org.noblecow.hrservice.di

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity

/**
 * Provides a platform-specific log writer optimized for development.
 *
 * - Android: Uses Logcat (platformLogWriter)
 * - iOS: Uses print() for Xcode console visibility
 */
expect fun getAppLogWriter(): LogWriter

expect fun minLogSeverity(): Severity
