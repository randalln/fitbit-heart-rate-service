package org.noblecow.hrservice.di

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter
import org.noblecow.hrservice.BuildKonfig

/**
 * Android implementation uses platform log writer (Logcat).
 */
actual fun getAppLogWriter(): LogWriter = platformLogWriter()

actual fun minLogSeverity(): Severity = if (BuildKonfig.DEBUG) Severity.Verbose else Severity.Info
