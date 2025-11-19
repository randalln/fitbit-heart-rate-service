package org.noblecow.hrservice.di

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.platformLogWriter

/**
 * Android implementation uses platform log writer (Logcat).
 */
actual fun getAppLogWriter(): LogWriter = platformLogWriter()
