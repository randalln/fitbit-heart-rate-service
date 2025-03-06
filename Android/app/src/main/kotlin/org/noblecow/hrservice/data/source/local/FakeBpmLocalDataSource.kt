package org.noblecow.hrservice.data.source.local

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.time.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.noblecow.hrservice.data.di.IoDispatcher
import org.noblecow.hrservice.data.util.FAKE_BPM_END
import org.noblecow.hrservice.data.util.FAKE_BPM_INTERVAL
import org.noblecow.hrservice.data.util.FAKE_BPM_START
import org.noblecow.hrservice.data.util.PORT_LISTEN

@Serializable
internal data class Request(
    val bpm: Int
)

@Singleton
internal class FakeBpmLocalDataSource @Inject constructor(
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {
    private val httpClient: HttpClient by lazy {
        HttpClient(Android) {
            install(ClientContentNegotiation) {
                json()
            }
        }
    }

    suspend fun run() {
        withContext(dispatcher) {
            getFakeBPMFlow()
                .collect {
                    httpClient.post("http://localhost:$PORT_LISTEN") {
                        contentType(ContentType.Application.Json)
                        setBody(Request(it))
                    }
                }
        }
    }

    private fun getFakeBPMFlow(): Flow<Int> = flow {
        var bpm = FAKE_BPM_START
        while (true) {
            if (bpm > FAKE_BPM_END) {
                bpm = FAKE_BPM_START
            }
            emit(bpm++)
            delay(Duration.ofSeconds(FAKE_BPM_INTERVAL))
        }
    }
}
