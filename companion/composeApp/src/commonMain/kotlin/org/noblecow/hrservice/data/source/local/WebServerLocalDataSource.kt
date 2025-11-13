package org.noblecow.hrservice.data.source.local

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
internal data class Request(val bpm: Int)

interface WebServerLocalDataSource {
    val bpmFlow: SharedFlow<Int>
    val webServerState: StateFlow<WebServerState>
    suspend fun start()
    suspend fun stop()
}
