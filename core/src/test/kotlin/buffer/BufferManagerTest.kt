package buffer

import decoder.Decoder
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URL
import kotlin.time.Duration

class BufferManagerTest {
    companion object {
        private lateinit var decoder: Decoder
        private lateinit var bufferManager: BufferManager

        private val fileUrls = ClassLoader.getSystemResources("files")
            .nextElement()
            .let(URL::getFile)
            .let(::File)
            .listFiles()
            ?.filter(File::exists)
            ?.mapNotNull(File::getAbsolutePath)!!

        private val fileUrl = fileUrls.first { it.contains("audio") && it.contains("video") }

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            require(fileUrls.isNotEmpty())
        }
    }

    @BeforeEach
    fun beforeEach() {
        decoder = Decoder.create().apply {
            runBlocking {
                initialize(fileUrl)
            }
        }
        bufferManager = BufferManager.create(decoder)
    }

    @AfterEach
    fun afterEach() {
        decoder.close()
    }

    @Test
    fun `static creation`() {
        assertInstanceOf(BufferManager::class.java, BufferManager.create(decoder = decoder))
    }

    @Test
    fun `change duration`() {
        bufferManager.changeDuration(0L)

        assertEquals(0L, bufferManager.bufferDurationMillis)
        assertEquals(0, bufferManager.audioBufferCapacity())
        assertEquals(0, bufferManager.videoBufferCapacity())

        bufferManager.changeDuration(1_000L)

        assertEquals(1_000L, bufferManager.bufferDurationMillis)
        assertEquals(43, bufferManager.audioBufferCapacity())
        assertEquals(25, bufferManager.videoBufferCapacity())
    }

    @Test
    fun `frames buffering`() = runTest {
        val timestamps = mutableListOf<Long>()

        bufferManager.startBuffering()
            .takeWhile { bufferManager.bufferIsEmpty() }
            .onEach(timestamps::add)
            .collect()

        assertTrue(timestamps.isNotEmpty())

        assertFalse(bufferManager.bufferIsEmpty())
    }

    @Test
    fun `frames extraction`() = runTest(timeout = Duration.ZERO) {
        assertTrue(bufferManager.startBuffering().takeWhile { bufferManager.bufferIsEmpty() }.toList().isNotEmpty())
    }

    @Test
    fun `buffer flushing`() = runTest {
        bufferManager.startBuffering()
            .takeWhile { bufferManager.bufferIsEmpty() }
            .collect()

        assertFalse(bufferManager.bufferIsEmpty(), "buffer should not be empty")

        bufferManager.flush()

        assertTrue(bufferManager.bufferIsEmpty(), "buffer should be empty")

        assertEquals(0, bufferManager.audioBufferSize())

        assertEquals(0, bufferManager.videoBufferSize())
    }
}