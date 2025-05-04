package buffer

import com.github.numq.klarity.core.buffer.Buffer
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BufferTest {
    @Test
    fun `create should fail with zero capacity`() = runTest {
        val result = Buffer.create<String>(0)
        assertTrue(result.isFailure)
        assertEquals("Unable to create buffer with 0 capacity", result.exceptionOrNull()?.message)
    }

    @Test
    fun `create should fail with negative capacity`() = runTest {
        val result = Buffer.create<String>(-1)
        assertTrue(result.isFailure)
        assertEquals("Unable to create buffer with -1 capacity", result.exceptionOrNull()?.message)
    }

    @Test
    fun `create should succeed with positive capacity`() = runTest {
        val result = Buffer.create<String>(1)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `capacity should return correct value`() = runTest {
        val buffer = Buffer.create<String>(5).getOrThrow()
        assertEquals(5, buffer.capacity)
    }

    @Test
    fun `take on empty buffer should block until item is available`() = runTest {
        val buffer = Buffer.create<String>(1).getOrThrow()

        val deferredTake = async {
            buffer.take().getOrThrow()
        }

        delay(100)

        buffer.put("test").getOrThrow()

        assertEquals("test", deferredTake.await())
    }

    @Test
    fun `put should add item to buffer`() = runTest {
        val buffer = Buffer.create<String>(1).getOrThrow()
        val result = buffer.put("test")
        assertTrue(result.isSuccess)

        assertEquals("test", buffer.take().getOrNull())
    }

    @Test
    fun `put on full buffer should block until space is available`() = runTest {
        val buffer = Buffer.create<String>(1).getOrThrow()
        buffer.put("test1").getOrThrow()

        val deferredPut = async {
            buffer.put("test2").getOrThrow()
        }

        delay(100)

        val takeResult = buffer.take()
        assertTrue(takeResult.isSuccess)
        assertEquals("test1", takeResult.getOrNull())

        deferredPut.await()

        assertEquals("test2", buffer.take().getOrNull())
    }

    @Test
    fun `clear should remove all items from buffer`() = runTest {
        val buffer = Buffer.create<String>(2).getOrThrow()
        buffer.put("test1").getOrThrow()
        buffer.put("test2").getOrThrow()

        val clearResult = buffer.clear()
        assertTrue(clearResult.isSuccess)

        val deferredTake = async {
            buffer.take().getOrThrow()
        }

        delay(100)
        assertTrue(!deferredTake.isCompleted)

        buffer.put("test3").getOrThrow()
        assertEquals("test3", deferredTake.await())
    }

    @Test
    fun `buffer should maintain FIFO order`() = runTest {
        val buffer = Buffer.create<Int>(3).getOrThrow()
        buffer.put(1).getOrThrow()
        buffer.put(2).getOrThrow()
        buffer.put(3).getOrThrow()

        assertEquals(1, buffer.take().getOrThrow())
        assertEquals(2, buffer.take().getOrThrow())
        assertEquals(3, buffer.take().getOrThrow())
    }

    @Test
    fun `buffer should handle multiple producers and consumers`() = runTest {
        val buffer = Buffer.create<Int>(10).getOrThrow()
        val count = 100

        val producers = List(5) { producerId ->
            async {
                repeat(count) { itemId ->
                    buffer.put(producerId * count + itemId).getOrThrow()
                }
            }
        }

        val consumer = async {
            val received = mutableSetOf<Int>()
            repeat(5 * count) {
                received.add(buffer.take().getOrThrow())
            }
            received
        }

        producers.forEach { it.await() }
        val receivedItems = consumer.await()

        assertEquals(5 * count, receivedItems.size)
    }
}