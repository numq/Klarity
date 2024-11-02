package buffer

import com.github.numq.klarity.core.buffer.Buffer
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BufferTest {
    private lateinit var buffer: Buffer<String>

    @Test
    fun resize() = runTest {
        assertFalse(Buffer.create<String>(0).isSuccess)

        val bufferResult = Buffer.create<String>(1)

        assertTrue(bufferResult.isSuccess)

        buffer = bufferResult.getOrThrow()

        assertFalse(Buffer.create<String>(0).isSuccess)

        assertTrue(buffer.resize(1).isSuccess)

        assertEquals(1, buffer.capacity)
    }

    @Test
    fun functionality() = runTest {
        buffer = Buffer.create<String>(1).getOrThrow()

        assertNull(buffer.peek().getOrThrow())

        val item = "test"

        assertTrue(buffer.push(item).isSuccess)

        assertEquals(item, buffer.peek().getOrThrow())

        assertEquals(item, buffer.poll().getOrThrow())

        assertTrue(buffer.flush().isSuccess)

        assertNull(buffer.peek().getOrThrow())
    }
}