package buffer

import decoder.DecodedFrame
import decoder.Decoder
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.time.Duration.Companion.nanoseconds

class BufferManagerTest {
    companion object {

        private val mediaUrl = File(ClassLoader.getSystemResource("files/audio_video.mp4").file).absolutePath

        private var decoder: Decoder? = null

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            decoder = Decoder.create(mediaUrl)
        }

        @JvmStatic
        @AfterAll
        fun afterAll() {
            decoder?.close()
            decoder = null
        }
    }

    @BeforeEach
    fun beforeEach() {
        runBlocking {
            decoder?.restart()
        }
    }

    @Test
    fun `static creation`() {
        assertNotNull(
            BufferManager.create(
                decoder = decoder!!, bufferDurationMillis = 1_000L
            )
        )
    }

    @Test
    fun `buffering frames`() = runTest {
        BufferManager.create(
            decoder = decoder!!,
            bufferDurationMillis = decoder!!.media.durationNanos.nanoseconds.inWholeMilliseconds * 2
        ).run {

            val timestamps = startBuffering().toList()

            assertFalse(bufferIsEmpty(), "buffer must not be empty")

            assertEquals(0L, timestamps.first())
            assertEquals(decoder!!.media.durationNanos, timestamps.last())

            val audioFrames = mutableListOf<DecodedFrame>()
            val videoFrames = mutableListOf<DecodedFrame>()

            var endOfAudio = false
            var endOfVideo = false

            while (!bufferIsEmpty()) {
                extractAudioFrame()?.let { frame ->
                    when (frame) {
                        is DecodedFrame.Audio -> audioFrames.add(frame)
                        is DecodedFrame.Video -> throw Exception("video frame in audio buffer")
                        is DecodedFrame.End -> endOfAudio = true
                    }
                }

                extractVideoFrame()?.let { frame ->
                    when (frame) {
                        is DecodedFrame.Audio -> throw Exception("audio frame in video buffer")
                        is DecodedFrame.Video -> videoFrames.add(frame)
                        is DecodedFrame.End -> endOfVideo = true
                    }
                }
            }

            assertTrue(audioFrames.isNotEmpty())
            assertTrue(videoFrames.isNotEmpty())

            assertTrue(endOfAudio, "should add end frame in audio buffer")
            assertTrue(endOfVideo, "should add end frame in video buffer")

            assertDoesNotThrow { flush() }

            assertEquals(0, audioBufferSize())
            assertEquals(0, videoBufferSize())

            assertTrue(bufferIsEmpty(), "buffer must be empty")
        }
    }
}