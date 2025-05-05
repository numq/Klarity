package data

import JNITest
import com.github.numq.klarity.core.data.NativeData
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NativeDataTest : JNITest() {
    @Test
    fun `should allocate native handle`() {
        val capacity = 1024
        val nativeData = NativeData.allocate(capacity)

        assertFalse(nativeData.isClosed())
        assertTrue(nativeData.getBuffer() != -1L)

        nativeData.close()
        assertTrue(nativeData.isClosed())
    }

    @Test
    fun `should throw when accessing after close`() {
        val nativeData = NativeData.allocate(512)
        nativeData.close()

        val exception = assertThrows(IllegalStateException::class.java) {
            nativeData.getBuffer()
        }

        assertEquals("Native data is closed", exception.message)
    }
}