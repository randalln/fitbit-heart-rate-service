package org.noblecow.hrservice.data.source.local

import kotlin.experimental.ExperimentalNativeApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

/**
 * Tests for AtomicRef expect/actual implementation.
 *
 * This test class contains both unit tests and integration tests:
 * - Simple tests use standard test assertions
 * - Concurrency tests use runBlocking(Dispatchers.Default) to verify real thread-safety
 *   under actual parallel execution (not virtual time)
 */
@OptIn(ExperimentalNativeApi::class)
class AtomicRefTest {

    // Unit Tests - Simple value operations

    @Test
    fun getReturnsInitialValue() {
        val atomicRef = AtomicRef("initial")
        assertEquals("initial", atomicRef.get())
    }

    @Test
    fun getReturnsNullForNullInitialValue() {
        val atomicRef = AtomicRef<String?>(null)
        assertNull(atomicRef.get())
    }

    @Test
    fun setUpdatesValue() {
        val atomicRef = AtomicRef("initial")
        atomicRef.set("updated")
        assertEquals("updated", atomicRef.get())
    }

    @Test
    fun setCanUpdateToNull() {
        val atomicRef = AtomicRef<String?>("initial")
        atomicRef.set(null)
        assertNull(atomicRef.get())
    }

    // Integration Tests - Real concurrency verification
    // These tests use runBlocking(Dispatchers.Default) intentionally to test actual
    // thread-safety under real parallel execution, not virtual time.

    /**
     * Integration test: Verifies AtomicRef handles concurrent writes without crashing.
     *
     * Uses real multithreading (Dispatchers.Default) to catch visibility and race condition
     * issues that wouldn't be caught with virtual time. This test is inherently non-deterministic
     * but validates that concurrent access doesn't cause crashes or data corruption.
     */
    @Test
    fun concurrentWritesAreThreadSafe() = runBlocking(Dispatchers.Default) {
        val atomicRef = AtomicRef(0)
        val numIterations = 1000
        val numCoroutines = 10

        // Launch multiple coroutines that each increment the counter
        val jobs = (1..numCoroutines).map {
            async {
                repeat(numIterations) {
                    val current = atomicRef.get()
                    atomicRef.set(current + 1)
                }
            }
        }

        jobs.awaitAll()

        // Due to race conditions with non-atomic increment,
        // the final value will be <= numCoroutines * numIterations
        // But the important thing is that it doesn't crash and reads/writes are visible
        val finalValue = atomicRef.get()
        assertTrue(finalValue > 0, "Expected some increments to succeed, got $finalValue")
        assertTrue(
            finalValue <= numCoroutines * numIterations,
            "Expected at most ${numCoroutines * numIterations} increments, got $finalValue"
        )
    }

    /**
     * Integration test: Verifies AtomicRef handles concurrent reads and writes safely.
     *
     * Uses real multithreading (Dispatchers.Default) to validate that readers never crash
     * or see corrupted data while writers are actively updating the value. Tests memory
     * visibility and thread-safety guarantees.
     */
    @Test
    fun concurrentReadsAndWritesAreThreadSafe() = runBlocking(Dispatchers.Default) {
        val atomicRef = AtomicRef("initial")
        val numIterations = 100

        // Launch readers and writers concurrently
        val readers = (1..10).map {
            async {
                repeat(numIterations) {
                    val value = atomicRef.get()
                    // Just verify we can read without crashing
                    assertNotNull(value)
                }
            }
        }

        val writers = (1..10).map { coroutineId ->
            async {
                repeat(numIterations) {
                    atomicRef.set("writer-$coroutineId")
                }
            }
        }

        (readers + writers).awaitAll()

        // Verify final state is valid
        val finalValue = atomicRef.get()
        assertTrue(
            finalValue.startsWith("writer-"),
            "Expected final value to start with 'writer-', got $finalValue"
        )
    }

    // Unit Tests - Instance independence and complex objects

    @Test
    fun multipleAtomicRefInstancesAreIndependent() {
        val ref1 = AtomicRef("value1")
        val ref2 = AtomicRef("value2")

        ref1.set("updated1")
        ref2.set("updated2")

        assertEquals("updated1", ref1.get())
        assertEquals("updated2", ref2.get())
    }

    @Test
    fun worksWithComplexObjects() {
        data class TestData(
            val id: Int,
            val name: String
        )

        val atomicRef = AtomicRef(TestData(1, "test"))
        assertEquals(TestData(1, "test"), atomicRef.get())

        atomicRef.set(TestData(2, "updated"))
        assertEquals(TestData(2, "updated"), atomicRef.get())
    }
}
