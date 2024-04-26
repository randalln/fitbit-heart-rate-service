package org.noblecow.hrservice.data

import org.noblecow.hrservice.ui.GeneralError

internal data class WebServerState(
    val bpm: Int = 0,
    val error: GeneralError.Ktor? = null,
    val running: Boolean = false
)
