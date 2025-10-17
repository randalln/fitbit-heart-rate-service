package org.noblecow.hrservice.data.source.local

internal data class WebServerState(
    val error: Throwable? = null,
    val isRunning: Boolean = false
)
