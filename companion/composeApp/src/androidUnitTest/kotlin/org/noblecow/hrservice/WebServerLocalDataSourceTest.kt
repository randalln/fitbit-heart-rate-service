package org.noblecow.hrservice

import app.cash.turbine.test
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.noblecow.hrservice.data.source.local.BpmReading
import org.noblecow.hrservice.data.source.local.Request
import org.noblecow.hrservice.data.source.local.WebServerLocalDataSource
import org.noblecow.hrservice.data.source.local.WebServerLocalDataSourceImpl
import org.noblecow.hrservice.data.source.local.WebServerState
import org.noblecow.hrservice.data.util.FAKE_BPM_START
import org.noblecow.hrservice.data.util.PORT_LISTEN

class WebServerLocalDataSourceTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var webServerLocalDataSource: WebServerLocalDataSource

    @Before
    fun before() {
        val testScope = CoroutineScope(SupervisorJob() + mainDispatcherRule.testDispatcher)
        webServerLocalDataSource = WebServerLocalDataSourceImpl(testScope)
    }

    @After
    fun after() {
        runBlocking {
            try {
                webServerLocalDataSource.stop()
                // Wait for server to fully stop with timeout
                withTimeout(5000) {
                    webServerLocalDataSource.webServerState.first { !it.isReady }
                }
                // Netty stop() has gracePeriod=1000ms + timeout=2000ms
                // We need to wait for the full shutdown cycle plus OS cleanup
                // Extended delay to ensure port is fully released between test variants
                // Total: 2000ms (Netty) + 8000ms (OS + test variant cleanup) = 10000ms
                delay(15000)
            } catch (e: Exception) {
                // Ignore cleanup errors but still delay to prevent port conflicts
                println("Cleanup error: ${e.message}")
                delay(10000)
            }
        }
    }

    @Test
    fun `Starting server is idempotent`() = runTest {
        webServerLocalDataSource.start()
        webServerLocalDataSource.start()
        webServerLocalDataSource.start()
        webServerLocalDataSource.start()

        webServerLocalDataSource.webServerState.test {
            assertEquals(
                WebServerState(error = null, isReady = true),
                awaitItem()
            )
        }
    }

    @Test
    fun `Stopping server is idempotent`() = runTest {
        webServerLocalDataSource.start()
        webServerLocalDataSource.stop()
        webServerLocalDataSource.stop()
        webServerLocalDataSource.stop()
        webServerLocalDataSource.stop()

        webServerLocalDataSource.webServerState.test {
            assertEquals(
                WebServerState(error = null, isReady = false),
                awaitItem()
            )
        }
    }

    @Test
    fun `When bpm is received, a new webserver state is not emitted`() = runTest {
        webServerLocalDataSource.start()

        webServerLocalDataSource.webServerState.test {
            skipItems(1) // Skip the start state
            sendBpm(FAKE_BPM_START)
            // Can fail with Unconsumed events
        }
    }

    @Test
    fun `When bad path is requested, a 404 is received`() = runTest {
        webServerLocalDataSource.start()

        val client = HttpClient(CIO) {
            install(ContentNegotiation) {
                json()
            }
        }
        val response: HttpResponse = client.post("http://localhost:$PORT_LISTEN/bad_path") {
            contentType(ContentType.Application.Json)
            setBody(Request(42))
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `When web server is stopped, a new state is emitted`() = runTest {
        webServerLocalDataSource.start()
        webServerLocalDataSource.stop()

        webServerLocalDataSource.webServerState.test {
            assertEquals(
                WebServerState(error = null, isReady = false),
                awaitItem()
            )
        }
    }

    @Test
    fun `When multiple bpm values are received, bpmFlow emits all values`() = runTest {
        webServerLocalDataSource.start()

        webServerLocalDataSource.bpmFlow.test {
            sendBpm(60)
            assertEquals(BpmReading(value = 60, sequenceNumber = 1), awaitItem())

            sendBpm(75)
            assertEquals(BpmReading(value = 75, sequenceNumber = 2), awaitItem())

            sendBpm(90)
            assertEquals(BpmReading(value = 90, sequenceNumber = 3), awaitItem())
        }
    }

    @Test
    fun `When GET request is made to root, OK response is returned`() = runTest {
        webServerLocalDataSource.start()

        val client = HttpClient(CIO) {
            install(ContentNegotiation) {
                json()
            }
        }
        val response: HttpResponse = client.get("http://localhost:$PORT_LISTEN/")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<Map<String, String>>()
        assertEquals("OK", body["status"])
    }

    @Test
    fun `Initial webServerState has isReady false`() = runTest {
        webServerLocalDataSource.webServerState.test {
            val state = awaitItem()
            assertEquals(false, state.isReady)
            Assert.assertNull(state.error)
        }
    }

    private suspend fun sendBpm(bpm: Int) {
        val client = HttpClient(CIO) {
            install(ContentNegotiation) {
                json()
            }
        }
        client.post("http://localhost:$PORT_LISTEN") {
            contentType(ContentType.Application.Json)
            setBody(Request(bpm))
        }
    }
}
