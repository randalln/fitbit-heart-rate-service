package org.noblecow.hrservice.di

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter
import org.noblecow.hrservice.BuildConfig

/**
 * Android implementation uses platform log writer (Logcat).
 */
actual fun getAppLogWriter(): LogWriter = platformLogWriter()

actual fun minLogSeverity(): Severity = if (BuildConfig.DEBUG) Severity.Verbose else Severity.Info
