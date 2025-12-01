package org.noblecow.hrservice.data.source.local

import co.touchlab.kermit.Logger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.noblecow.hrservice.data.util.FAKE_BPM_END
import org.noblecow.hrservice.data.util.FAKE_BPM_INTERVAL_MS
import org.noblecow.hrservice.data.util.FAKE_BPM_START
import org.noblecow.hrservice.data.util.PORT_LISTEN
import org.noblecow.hrservice.di.IoDispatcher

private const val TAG = "FakeBpmLocalDataSource"

@SingleIn(AppScope::class)
@Inject
internal class FakeBpmLocalDataSource(
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
    logger: Logger
) {
    private val logger = logger.withTag(TAG)
    private var httpClient: HttpClient? = null

    private fun getOrCreateHttpClient(): HttpClient = httpClient ?: HttpClient {
        install(ClientContentNegotiation) {
            json()
        }
    }.also { httpClient = it }

    suspend fun run() {
        withContext(dispatcher) {
            getFakeBPMFlow()
                .collect {
                    getOrCreateHttpClient().post("http://localhost:$PORT_LISTEN") {
                        contentType(ContentType.Application.Json)
                        setBody(Request(it))
                    }
                }
        }
    }

    /**
     * Closes the HTTP client to release any open connections.
     * This is critical when stopping FakeBPM to ensure the server port is released quickly.
     */
    fun cleanup() {
        httpClient?.close()
        httpClient = null
        logger.d { "HttpClient closed and cleaned up" }
    }

    private fun getFakeBPMFlow(): Flow<Int> = flow {
        var bpm = FAKE_BPM_START
        while (true) {
            if (bpm > FAKE_BPM_END) {
                bpm = FAKE_BPM_START
            }
            emit(bpm++)
            delay(FAKE_BPM_INTERVAL_MS)
        }
    }
}
