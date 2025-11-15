package org.noblecow.hrservice.data.source.local

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
internal data class Request(val bpm: Int)

/**
 * Represents a heart rate measurement with a unique counter.
 *
 * The counter ensures that consecutive identical BPM values are treated as distinct emissions,
 * which is critical for heart rate monitoring where each heartbeat should trigger a notification
 * even if the BPM value remains constant.
 *
 * @property value The beats per minute reading
 * @property sequenceNumber A monotonically increasing counter that makes each emission unique
 */
data class BpmReading(
    val value: Int,
    val sequenceNumber: Int
)

interface WebServerLocalDataSource {
    val bpmFlow: SharedFlow<BpmReading>
    val webServerState: StateFlow<WebServerState>
    suspend fun start()
    suspend fun stop()
}
