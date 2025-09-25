package org.noblecow.hrservice.data.source.local

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
internal data class Request(
    val bpm: Int
)

internal interface WebServerLocalDataSource {
    val bpmStream: SharedFlow<Int>
    val webServerState: StateFlow<WebServerState>
    fun start()
    fun stop()
}
