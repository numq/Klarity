package decoder

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import javax.sound.sampled.AudioFormat
import kotlin.time.Duration.Companion.microseconds

class DecoderTest {
    companion object {
        @Test
        fun `static creation`() {
            val grabber: FFmpegFrameGrabber = mockk<FFmpegFrameGrabber>(relaxUnitFun = true)

            assertNotNull(Decoder.Implementation("any media url", grabber))
        }

        @JvmStatic
        @AfterAll
        fun afterAll() {
            unmockkAll()
        }
    }

    private val grabber = mockkClass(FFmpegFrameGrabber::class)

    @AfterEach
    fun afterEach() {
        clearAllMocks()
    }

    @Test
    fun `decoding process`() = runTest {
        every { grabber.setTimestamp(any()) } returns Unit

        every { grabber.setTimestamp(any(), any()) } returns Unit

        every { grabber.start() } returns Unit

        every { grabber.stop() } returns Unit

        every { grabber.restart() } returns Unit

        every { grabber.close() } returns Unit

        every { grabber.timestamp } returns (0..100).random().toLong()

        every { grabber.lengthInTime } returns 0L

        every { grabber.hasAudio() } returns true

        every { grabber.hasVideo() } returns true

        every { grabber.audioFrameRate } returns (0..60).random().toDouble()

        every { grabber.videoFrameRate } returns (0..60).random().toDouble()

        every { grabber.sampleRate } returns (0..100).random()

        every { grabber.sampleFormat } returns 0

        every { grabber.audioChannels } returns (0..100).random()

        every { grabber.imageWidth } returns (0..100).random()

        every { grabber.imageHeight } returns (0..100).random()

        every { grabber.grabFrame(any(), any(), any(), any(), any()) } returns null

        val format = AudioFormat(
            grabber.sampleRate.toFloat(),
            8,
            grabber.audioChannels,
            true,
            false
        )

        val url = "test"

        Decoder.Implementation(url, grabber).use { decoder ->
            with(decoder.media) {
                assertEquals(url, url)
                assertEquals(grabber.lengthInTime.microseconds.inWholeNanoseconds, durationNanos)
                assertEquals(audioFrameRate, grabber.audioFrameRate)
                assertEquals(videoFrameRate, grabber.videoFrameRate)
                assertEquals(format, format)
                assertEquals(grabber.imageWidth to grabber.imageHeight, size)
            }

            assertEquals(null, decoder.snapshot())

            assertEquals(null, decoder.snapshot())

            assertEquals(DecodedFrame.End(grabber.lengthInTime.microseconds.inWholeNanoseconds), decoder.nextFrame()!!)

            assertDoesNotThrow { decoder.seekTo(0L) }

            assertDoesNotThrow { decoder.restart() }
        }
    }
}