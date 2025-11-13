package org.noblecow.hrservice.data.source.local

data class WebServerState(
    val error: Throwable? = null,
    val isReady: Boolean = false
)
