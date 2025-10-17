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
import org.noblecow.hrservice.data.source.local.Request
import org.noblecow.hrservice.data.source.local.WebServerLocalDataSource
import org.noblecow.hrservice.data.source.local.WebServerState
import org.noblecow.hrservice.data.util.FAKE_BPM_START
import org.noblecow.hrservice.data.util.PORT_LISTEN

class WebServerLocalDataSourceTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var webServerLocalDataSource: WebServerLocalDataSource

    @Before
    fun before() {
        webServerLocalDataSource = WebServerLocalDataSource()
    }

    @After
    fun after() {
        webServerLocalDataSource.stop()
    }

    @Test
    fun `When web server is started, a new state is emitted`() = runTest {
        webServerLocalDataSource.start()

        webServerLocalDataSource.webServerState.test {
            Assert.assertEquals(
                WebServerState(error = null, isRunning = true),
                awaitItem()
            )
        }
    }

    @Test
    fun `When bpm is received, a new state is emitted`() = runTest {
        webServerLocalDataSource.start()

        sendBpm(FAKE_BPM_START)

        webServerLocalDataSource.webServerState.test {
            Assert.assertEquals(
                WebServerState(error = null, isRunning = true),
                awaitItem()
            )
        }
    }

    @Test
    fun `When web server is stopped, a new state is emitted`() = runTest {
        webServerLocalDataSource.start()
        webServerLocalDataSource.webServerState.test {
            Assert.assertEquals(
                WebServerState(error = null, isRunning = true),
                awaitItem()
            )
        }
        sendBpm(FAKE_BPM_START + 1)

        webServerLocalDataSource.stop()

        webServerLocalDataSource.webServerState.test {
            Assert.assertEquals(
                WebServerState(error = null, isRunning = false),
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
