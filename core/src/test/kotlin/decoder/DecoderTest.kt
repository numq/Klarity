package decoder

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URL
import javax.sound.sampled.AudioFormat
import kotlin.math.ceil
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.nanoseconds

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
        fileUrls.forEach { file ->
            assertNotNull(Decoder.create(file))
        }
    }

    @Test
    fun `decoding process`() = runTest {
        fileUrls.forEach { file ->
            FFmpegFrameGrabber(file).use { grabber ->

                grabber.start()

                Decoder.create(file).use { decoder ->
                    with(decoder.media) {
                        assertEquals(url, url)

                        assertEquals(File(file).name, name)

                        if (grabber.hasAudio()) {
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

                        with(grabber) {
                            assertEquals(lengthInTime.microseconds.inWholeNanoseconds, durationNanos)

                            assertEquals(audioFrameRate, audioFrameRate)

                            assertEquals(videoFrameRate, videoFrameRate)

                            assertEquals((imageWidth to imageHeight).takeIf { it.first > 0 && it.second > 0 }, size)
                        }
                    }

                    val audioFrames = mutableListOf<DecodedFrame.Audio>()

                    val videoFrames = mutableListOf<DecodedFrame.Video>()

                    var endFrame: DecodedFrame.End? = null

                    assertDoesNotThrow {
                        runBlocking {
                            decoder.restart()
                        }
                    }

                    while (true) {
                        when (val frame = decoder.nextFrame()) {
                            is DecodedFrame.Audio -> audioFrames.add(frame)
                            is DecodedFrame.Video -> videoFrames.add(frame)
                            is DecodedFrame.End -> {
                                endFrame = frame
                                break
                            }

                            else -> break
                        }
                    }

                    with(grabber) {
                        assertEquals(ceil(lengthInTime * audioFrameRate / 1_000_000L).toInt(), audioFrames.size)
                        assertEquals(ceil(lengthInTime * videoFrameRate / 1_000_000L).toInt(), videoFrames.size)
                        assertEquals(DecodedFrame.End(lengthInTime.microseconds.inWholeNanoseconds), endFrame)
                    }

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
}