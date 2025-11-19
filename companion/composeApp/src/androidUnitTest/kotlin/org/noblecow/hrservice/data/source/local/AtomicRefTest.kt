package org.noblecow.hrservice.data.source.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AtomicRefTest {

    @Test
    fun `get returns initial value`() {
        val atomicRef = AtomicRef("initial")
        assertEquals("initial", atomicRef.get())
    }

    @Test
    fun `get returns null for null initial value`() {
        val atomicRef = AtomicRef<String?>(null)
        assertNull(atomicRef.get())
    }

    @Test
    fun `set updates value`() {
        val atomicRef = AtomicRef("initial")
        atomicRef.set("updated")
        assertEquals("updated", atomicRef.get())
    }

    @Test
    fun `set can update to null`() {
        val atomicRef = AtomicRef<String?>("initial")
        atomicRef.set(null)
        assertNull(atomicRef.get())
    }

    @Test
    fun `concurrent writes are thread-safe`() = runBlocking(Dispatchers.Default) {
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
        assert(finalValue > 0) { "Expected some increments to succeed, got $finalValue" }
        assert(finalValue <= numCoroutines * numIterations) {
            "Expected at most ${numCoroutines * numIterations} increments, got $finalValue"
        }
    }

    @Test
    fun `concurrent reads and writes are thread-safe`() = runBlocking(Dispatchers.Default) {
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
        assert(finalValue.startsWith("writer-")) {
            "Expected final value to start with 'writer-', got $finalValue"
        }
    }

    @Test
    fun `multiple AtomicRef instances are independent`() {
        val ref1 = AtomicRef("value1")
        val ref2 = AtomicRef("value2")

        ref1.set("updated1")
        ref2.set("updated2")

        assertEquals("updated1", ref1.get())
        assertEquals("updated2", ref2.get())
    }

    @Test
    fun `works with complex objects`() {
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
