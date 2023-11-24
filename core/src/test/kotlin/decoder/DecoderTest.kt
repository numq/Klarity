package decoder

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URL
import javax.sound.sampled.AudioFormat
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.nanoseconds

class DecoderTest {

    private val fileUrls = ClassLoader.getSystemResources("/files")
        .toList()
        .map(URL::getFile)
        .map(::File)
        .filter(File::exists)
        .map(File::getAbsolutePath)

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
                Decoder.create(file).use { decoder ->
                    with(decoder.media) {
                        assertEquals(url, url)
                        assertEquals("test.mp4", name)
                        assertEquals(
                            AudioFormat(
                                48000F,
                                16,
                                2,
                                true,
                                false
                            ).toString(),
                            audioFormat.toString()
                        )

                        grabber.restart()

                        assertEquals(grabber.lengthInTime.microseconds.inWholeNanoseconds, durationNanos)
                        assertEquals(grabber.audioFrameRate, audioFrameRate)
                        assertEquals(grabber.videoFrameRate, videoFrameRate)
                        assertEquals(grabber.imageWidth to grabber.imageHeight, size)
                    }

                    val audioFrames = mutableListOf<DecodedFrame.Audio>()

                    val videoFrames = mutableListOf<DecodedFrame.Video>()

                    var endFrame: DecodedFrame.End? = null

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

                    assertEquals(grabber.lengthInAudioFrames, audioFrames.size)
                    assertEquals(grabber.lengthInVideoFrames, videoFrames.size)
                    assertEquals(DecodedFrame.End(grabber.lengthInTime.microseconds.inWholeNanoseconds), endFrame!!)

                    repeat(5) {
                        val randomTimestampMicros =
                            (0L..decoder.media.durationNanos).random().nanoseconds.inWholeMicroseconds

                        assertEquals(grabber.timestamp, decoder.seekTo(randomTimestampMicros))
                    }

                    assertDoesNotThrow {
                        runBlocking {
                            decoder.restart()
                        }
                    }

                    assertEquals(grabber.timestamp, 0L)
                }
            }
        }
    }
}