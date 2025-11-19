package org.noblecow.hrservice.di

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Message
import co.touchlab.kermit.Severity
import co.touchlab.kermit.Tag

/**
 * iOS implementation uses print() for Xcode console visibility.
 * OSLog (platformLogWriter) doesn't show in Xcode's debug console by default.
 */
actual fun getAppLogWriter(): LogWriter = object : LogWriter() {
    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        val logMessage = buildString {
            append("[${severity.name}] ")
            append("[$tag] ")
            append(message)
            throwable?.let {
                append("\n")
                append(it.stackTraceToString())
            }
        }
        println(logMessage)
    }
}
