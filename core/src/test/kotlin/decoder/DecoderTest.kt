package decoder

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URL
import javax.sound.sampled.AudioFormat
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

class DecoderTest {
    companion object {
        private val fileUrls = ClassLoader.getSystemResources("files")
            .nextElement()
            .let(URL::getFile)
            .let(::File)
            .listFiles()
            ?.filter(File::exists)
            ?.map(File::getAbsolutePath)!!

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            require(fileUrls.isNotEmpty())
        }
    }

    @Test
    fun `static creation`() {
        fileUrls.forEach { url ->
            assertNotNull(Decoder.create(url))
        }
    }

    @Test
    fun `decoding process`() = runTest(timeout = ZERO) {
        fileUrls.forEach { file ->
            Decoder.create(file).let { decoder ->

                with(decoder.media) {

                    assertEquals(file, url)

                    assertEquals(File(file).name, name)

                    if (file.contains("audio")) {
                        assertEquals(
                            AudioFormat(
                                44100F,
                                16,
                                2,
                                true,
                                false
                            ).toString(),
                            audioFormat.toString()
                        )
                    }

                    assertEquals(5.seconds.inWholeNanoseconds, durationNanos)

                    assertEquals(audioFrameRate, audioFrameRate)

                    assertEquals(videoFrameRate, videoFrameRate)

                    if (file.contains("video")) assertEquals(500 to 500, size)
                }

                val audioFrames = mutableListOf<DecodedFrame.Audio>()

                val videoFrames = mutableListOf<DecodedFrame.Video>()

                var endFrame: DecodedFrame.End? = null

                while (endFrame == null) {
                    decoder.nextFrame()?.let { frame ->
                        when (frame) {
                            is DecodedFrame.Audio -> audioFrames.add(frame)
                            is DecodedFrame.Video -> videoFrames.add(frame)
                            is DecodedFrame.End -> {
                                endFrame = frame
                            }
                        }
                    }
                }

                if (file.contains("audio")) assertTrue(audioFrames.isNotEmpty())

                if (file.contains("video")) assertTrue(videoFrames.isNotEmpty())

                assertEquals(DecodedFrame.End(5.seconds.inWholeNanoseconds), endFrame)

                var previousTimestamp: Long? = null

                repeat(5) {
                    val randomTimestampMicros =
                        (0L..decoder.media.durationNanos).random().nanoseconds.inWholeMicroseconds

                    assertNotEquals(previousTimestamp, decoder.seekTo(randomTimestampMicros))

                    previousTimestamp = randomTimestampMicros
                }
            }
        }
    }
}