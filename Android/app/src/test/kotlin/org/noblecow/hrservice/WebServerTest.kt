package org.noblecow.hrservice

import app.cash.turbine.test
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.noblecow.hrservice.data.WebServer
import org.noblecow.hrservice.data.WebServerState
import org.noblecow.hrservice.ui.FAKE_BPM_START
import org.noblecow.hrservice.ui.PORT_LISTEN
import org.noblecow.hrservice.ui.Request

class WebServerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var webServer: WebServer

    @Before
    fun before() {
        webServer = WebServer()
    }

    @After
    fun after() {
        webServer.stop()
    }

    @Test
    fun `When web server is started, a new state is emitted`() = runTest {
        webServer.start()

        webServer.webServerState.test {
            Assert.assertEquals(
                WebServerState(bpm = 0, error = null, running = true),
                awaitItem()
            )
        }
    }

    @Test
    fun `When bpm is received, a new state is emitted`() = runTest {
        webServer.start()

        sendBpm(FAKE_BPM_START)

        webServer.webServerState.test {
            Assert.assertEquals(
                WebServerState(bpm = 60, error = null, running = true),
                awaitItem()
            )
        }
    }

    @Test
    fun `When web server is stopped, a new state is emitted`() = runTest {
        webServer.start()
        webServer.webServerState.test {
            Assert.assertEquals(
                WebServerState(bpm = 0, error = null, running = true),
                awaitItem()
            )
        }
        sendBpm(FAKE_BPM_START + 1)

        webServer.stop()

        webServer.webServerState.test {
            Assert.assertEquals(
                WebServerState(bpm = 0, error = null, running = false),
                awaitItem()
            )
        }
    }

    private suspend fun sendBpm(bpm: Int) {
        val client = HttpClient(Android) {
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
