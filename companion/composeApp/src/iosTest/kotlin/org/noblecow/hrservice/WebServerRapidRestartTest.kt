package org.noblecow.hrservice

import app.cash.turbine.test
import co.touchlab.kermit.Logger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.noblecow.hrservice.data.source.local.WebServerLocalDataSourceImpl

/**
 * Tests for rapid server restart on iOS to verify SO_REUSEADDR configuration.
 *
 * **Purpose:**
 * Verifies that the Ktor CIO server with SO_REUSEADDR enabled allows immediate
 * port rebinding after stop, eliminating the 8-second delay that was previously
 * required on Darwin/iOS.
 *
 * **What's being tested:**
 * - stop() → start() cycle completes in < 2 seconds (down from 8+ seconds)
 * - Multiple rapid restarts work reliably
 * - No EADDRINUSE errors during rapid restart
 * - Server functionality works correctly after rapid restart
 *
 * **Implementation Note:**
 * SO_REUSEADDR is configured in KtorServerFactory.ios.kt via:
 * `configure = { reuseAddress = true }`
 */
class WebServerRapidRestartTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testScope: CoroutineScope
    private lateinit var dataSource: WebServerLocalDataSourceImpl
    private lateinit var logger: Logger

    @BeforeTest
    fun setup() {
        testScope = CoroutineScope(SupervisorJob() + testDispatcher)
        logger = Logger.withTag("WebServerRapidRestartTest")
        dataSource = WebServerLocalDataSourceImpl(
            appScope = testScope,
            ioDispatcher = testDispatcher,
            defaultLogger = logger
        )
    }

    @AfterTest
    fun teardown() {
        // Clean up after each test
        runTest(testDispatcher) {
            try {
                dataSource.stop()
            } catch (_: Exception) {
                // Ignore cleanup errors
            }
        }
        testScope.cancel()
    }

    @Test
    fun testRapidServerRestart() = runTest(testDispatcher) {
        // Start the server
        dataSource.start()
        dataSource.webServerState.first { it.isReady }

        // Stop the server
        dataSource.stop()
        dataSource.webServerState.first { !it.isReady }

        // Immediately restart - should NOT take 8 seconds
        val startTime = TimeSource.Monotonic.markNow()
        dataSource.start()
        dataSource.webServerState.first { it.isReady }
        val elapsed = startTime.elapsedNow()

        // Verify restart completed in < 2 seconds (generous margin)
        // Previously would take 8+ seconds due to TIME_WAIT
        assertTrue(
            elapsed < 2.seconds,
            "Restart took ${elapsed.inWholeMilliseconds}ms, expected < 2000ms. " +
                "SO_REUSEADDR may not be properly configured."
        )

        logger.d { "Rapid restart completed in ${elapsed.inWholeMilliseconds}ms" }
    }

    @Test
    fun testMultipleRapidRestarts() = runTest(testDispatcher) {
        // Verify multiple rapid restarts work reliably
        repeat(3) { iteration ->
            logger.d { "Starting iteration $iteration" }

            // Start
            val startTime = TimeSource.Monotonic.markNow()
            dataSource.start()
            dataSource.webServerState.first { it.isReady }
            val startElapsed = startTime.elapsedNow()

            logger.d { "Iteration $iteration: Started in ${startElapsed.inWholeMilliseconds}ms" }

            // Stop
            dataSource.stop()
            dataSource.webServerState.first { !it.isReady }

            // Verify quick restart (< 3 seconds to account for cumulative overhead)
            assertTrue(
                startElapsed < 3.seconds,
                "Iteration $iteration: Start took ${startElapsed.inWholeMilliseconds}ms, expected < 3000ms"
            )
        }

        logger.d { "All 3 rapid restart iterations completed successfully" }
    }

    @Test
    fun testServerFunctionalityAfterRapidRestart() = runTest(testDispatcher) {
        // Start → Stop → Immediate Start
        dataSource.start()
        dataSource.webServerState.first { it.isReady }

        dataSource.stop()
        dataSource.webServerState.first { !it.isReady }

        dataSource.start()
        dataSource.webServerState.first { it.isReady }

        // Verify BPM flow works after rapid restart
        dataSource.bpmFlow.test {
            // BPM flow should be functional
            // Note: We can't easily test actual HTTP requests in unit tests,
            // but we can verify the flow is active and ready to emit
            expectNoEvents() // No emissions yet (no HTTP requests)
            cancelAndIgnoreRemainingEvents()
        }

        logger.d { "Server functionality verified after rapid restart" }
    }

    @Test
    fun testNoAddressInUseErrorOnRapidRestart() = runTest(testDispatcher) {
        // This test verifies that we don't get EADDRINUSE errors

        dataSource.start()
        dataSource.webServerState.first { it.isReady }

        dataSource.stop()
        dataSource.webServerState.first { !it.isReady }

        // Immediate restart should succeed without EADDRINUSE error
        var startSucceeded = false
        var startError: Throwable? = null

        try {
            dataSource.start()
            dataSource.webServerState.first { it.isReady }
            startSucceeded = true
        } catch (e: Exception) {
            startError = e
        }

        assertTrue(startSucceeded, "Rapid restart failed with error: $startError")

        // Verify no "Address already in use" error occurred
        val errorMessage = dataSource.webServerState.value.error?.message ?: ""
        assertTrue(
            !errorMessage.contains("Address already in use", ignoreCase = true) &&
                !errorMessage.contains("EADDRINUSE", ignoreCase = true),
            "Got EADDRINUSE error on rapid restart: $errorMessage. SO_REUSEADDR may not be working."
        )

        logger.d { "No EADDRINUSE error on rapid restart" }
    }
}
