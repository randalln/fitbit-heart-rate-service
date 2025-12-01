package org.noblecow.hrservice

import app.cash.turbine.test
import co.touchlab.kermit.CommonWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.loggerConfigInit
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.noblecow.hrservice.data.source.local.BpmReading
import org.noblecow.hrservice.data.source.local.KtorServerConfig
import org.noblecow.hrservice.data.source.local.KtorServerManager
import org.noblecow.hrservice.data.source.local.Request

/**
 * Unit tests for web server routing using Ktor's testApplication.
 *
 * These tests use Ktor's test host instead of a real server, eliminating:
 * - Port binding conflicts
 * - Network delays
 * - OS-level port cleanup delays (previously 15+ seconds per test)
 *
 * Tests now run instantly and can be parallelized.
 */
class WebServerLocalDataSourceTest {
    private val logger = Logger(loggerConfigInit(CommonWriter()), "WebServerTest")

    @Test
    fun `When bad path is requested, a 404 is received`() = runTest {
        testApplication {
            val bpmFlow = MutableSharedFlow<BpmReading>()
            val serverManager = createServerManager()

            application {
                serverManager.configureApplication(this) { bpm ->
                    bpmFlow.emit(BpmReading(value = bpm, sequenceNumber = 1))
                }
            }

            val client = createClient {
                install(ContentNegotiation) {
                    json()
                }
            }

            val response: HttpResponse = client.post("/bad_path") {
                contentType(ContentType.Application.Json)
                setBody(Request(42))
            }

            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }

    @Test
    fun `When GET request is made to root, OK response is returned`() = runTest {
        testApplication {
            val bpmFlow = MutableSharedFlow<BpmReading>()
            val serverManager = createServerManager()

            application {
                serverManager.configureApplication(this) { bpm ->
                    bpmFlow.emit(BpmReading(value = bpm, sequenceNumber = 1))
                }
            }

            val client = createClient {
                install(ContentNegotiation) {
                    json()
                }
            }

            val response: HttpResponse = client.get("/")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.body<Map<String, String>>()
            assertEquals("OK", body["status"])
        }
    }

    @Test
    fun `When multiple bpm values are received, bpmFlow emits all values`() = runTest {
        testApplication {
            val bpmFlow = MutableSharedFlow<BpmReading>()
            var sequenceNumber = 0
            val serverManager = createServerManager()

            application {
                serverManager.configureApplication(this) { bpm ->
                    bpmFlow.emit(BpmReading(value = bpm, sequenceNumber = ++sequenceNumber))
                }
            }

            val client = createClient {
                install(ContentNegotiation) {
                    json()
                }
            }

            bpmFlow.test {
                client.post("/") {
                    contentType(ContentType.Application.Json)
                    setBody(Request(60))
                }
                assertEquals(BpmReading(value = 60, sequenceNumber = 1), awaitItem())

                client.post("/") {
                    contentType(ContentType.Application.Json)
                    setBody(Request(75))
                }
                assertEquals(BpmReading(value = 75, sequenceNumber = 2), awaitItem())

                client.post("/") {
                    contentType(ContentType.Application.Json)
                    setBody(Request(90))
                }
                assertEquals(BpmReading(value = 90, sequenceNumber = 3), awaitItem())
            }
        }
    }

    @Test
    fun `When POST request is made with BPM, it is echoed back in response`() = runTest {
        testApplication {
            val bpmFlow = MutableSharedFlow<BpmReading>()
            val serverManager = createServerManager()

            application {
                serverManager.configureApplication(this) { bpm ->
                    bpmFlow.emit(BpmReading(value = bpm, sequenceNumber = 1))
                }
            }

            val client = createClient {
                install(ContentNegotiation) {
                    json()
                }
            }

            val response: HttpResponse = client.post("/") {
                contentType(ContentType.Application.Json)
                setBody(Request(75))
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.body<Request>()
            assertEquals(75, body.bpm)
        }
    }

    private fun createServerManager() = KtorServerManager(
        config = KtorServerConfig(),
        logger = logger
    )
}
