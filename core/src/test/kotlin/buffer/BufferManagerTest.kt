package buffer

import audio.AudioSampler
import decoder.Decoder
import fake.FakeDecoder
import frame.DecodedFrame
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import media.Media
import media.MediaInfo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration

class BufferManagerTest {
    private lateinit var decoder: Decoder
    private lateinit var bufferManager: BufferManager

    @BeforeEach
    fun beforeEach() {
        decoder = FakeDecoder().apply {
            runBlocking {
                initialize(
                    Media.Local(
                        path = "url",
                        name = "name",
                        info = MediaInfo(
                            durationNanos = 10_000L,
                            audioFormat = AudioSampler.AUDIO_FORMAT,
                            frameRate = 30.0,
                            size = 500 to 500,
                            previewFrame = DecodedFrame.Video(0L, false, byteArrayOf())
                        )
                    )
                )
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
        assertEquals(1, bufferManager.audioBufferCapacity())
        assertEquals(1, bufferManager.videoBufferCapacity())

        bufferManager.changeDuration(1_000L)

        assertEquals(1_000L, bufferManager.bufferDurationMillis)
        assertEquals(43, bufferManager.audioBufferCapacity())
        assertEquals(30, bufferManager.videoBufferCapacity())
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