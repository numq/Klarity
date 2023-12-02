package buffer

import decoder.DecodedFrame
import decoder.Decoder
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URL
import kotlin.time.Duration.Companion.ZERO

class BufferManagerTest {
    companion object {
        private val fileUrls = ClassLoader.getSystemResources("files")
            .nextElement()
            .let(URL::getFile)
            .let(::File)
            .listFiles()
            ?.filter(File::exists)
            ?.map(File::getAbsolutePath)!!

        private val mediaUrl = File(ClassLoader.getSystemResource("files/audio_video.mp4").file).absolutePath

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            require(fileUrls.isNotEmpty())
            require(File(mediaUrl).exists())
        }
    }

    @Test
    fun `static creation`() {
        fileUrls.forEach { url ->
            assertNotNull(
                Decoder.create(url).let { decoder ->
                    BufferManager.create(
                        decoder = decoder,
                        bufferDurationMillis = 1_000L
                    )
                }
            )
        }
    }

    @Test
    fun `buffering frames`() = runTest(timeout = ZERO) {
        Decoder.create(mediaUrl).let { decoder ->
            BufferManager.create(
                decoder = decoder,
                bufferDurationMillis = 10_000L
            ).run {

                val timestamps = startBuffering().toList()

                assertTrue(timestamps.isNotEmpty())

                assertEquals(decoder.media.durationNanos, timestamps.last())

                assertFalse(bufferIsEmpty(), "buffer must not be empty")

                val audioFrames = mutableListOf<DecodedFrame>()

                val videoFrames = mutableListOf<DecodedFrame>()

                while (!bufferIsEmpty()) {
                    extractAudioFrame()?.let(audioFrames::add)
                    extractVideoFrame()?.let(videoFrames::add)
                }

                assertTrue(audioFrames.isNotEmpty(), "audio buffer should not be empty")

                assertTrue(videoFrames.isNotEmpty(), "video buffer should not be empty")

                assertTrue(
                    audioFrames.filterIsInstance<DecodedFrame.Video>().isEmpty(),
                    "audio buffer should not contain video frames"
                )

                assertTrue(
                    videoFrames.filterIsInstance<DecodedFrame.Audio>().isEmpty(),
                    "video buffer should not contain audio frames"
                )

                val endFrame = DecodedFrame.End(decoder.media.durationNanos)

                assertEquals(endFrame, audioFrames.last())

                assertEquals(endFrame, videoFrames.last())

                flush()

                assertTrue(bufferIsEmpty(), "both buffers must be empty")

                assertEquals(0, audioBufferSize())

                assertEquals(0, videoBufferSize())
            }
        }
    }
}