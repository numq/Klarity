package decoder

import JNITest
import com.github.numq.klarity.core.decoder.DecoderException
import com.github.numq.klarity.core.decoder.HardwareAcceleration
import com.github.numq.klarity.core.decoder.NativeDecoder
import com.github.numq.klarity.core.frame.NativeFrame
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URL

class NativeDecoderTest : JNITest() {
    private val files = File(ClassLoader.getSystemResources("files").nextElement().let(URL::getFile)).listFiles()

    private val audioFile = files?.find { file -> file.nameWithoutExtension == "audio_only" }?.absolutePath

    private val videoFile = files?.find { file -> file.nameWithoutExtension == "video_only" }?.absolutePath

    private val mediaFile = files?.find { file -> file.nameWithoutExtension == "audio_video" }?.absolutePath

    private lateinit var audioDecoder: NativeDecoder

    private lateinit var videoDecoder: NativeDecoder

    private lateinit var mediaDecoder: NativeDecoder

    @BeforeEach
    fun beforeEach() {
        audioDecoder = NativeDecoder(
            audioFile!!,
            findAudioStream = true,
            findVideoStream = false,
            hardwareAcceleration = HardwareAcceleration.NONE
        )

        videoDecoder = NativeDecoder(
            videoFile!!,
            findAudioStream = false,
            findVideoStream = true,
            hardwareAcceleration = HardwareAcceleration.NONE
        )

        mediaDecoder = NativeDecoder(
            mediaFile!!,
            findAudioStream = true,
            findVideoStream = true,
            hardwareAcceleration = HardwareAcceleration.NONE
        )
    }

    @AfterEach
    fun afterEach() {
        audioDecoder.close()
        videoDecoder.close()
        mediaDecoder.close()
    }

    @Test
    fun `should handle invalid location`() = runTest {
        assertThrows(DecoderException::class.java) {
            NativeDecoder(
                location = "some invalid media location",
                findAudioStream = true,
                findVideoStream = true,
                hardwareAcceleration = HardwareAcceleration.NONE
            ).close()
        }
    }

    @Test
    fun `should create and close`() = runTest {
        files!!.forEach { file ->
            assertDoesNotThrow {
                when (file.nameWithoutExtension) {
                    "audio_only" -> NativeDecoder(
                        location = file.absolutePath,
                        findAudioStream = true,
                        findVideoStream = false,
                        hardwareAcceleration = HardwareAcceleration.NONE
                    ).close()

                    "video_only" -> NativeDecoder(
                        location = file.absolutePath,
                        findAudioStream = false,
                        findVideoStream = true,
                        hardwareAcceleration = HardwareAcceleration.NONE
                    ).close()

                    else -> NativeDecoder(
                        location = file.absolutePath,
                        findAudioStream = true,
                        findVideoStream = true,
                        hardwareAcceleration = HardwareAcceleration.NONE
                    ).close()
                }
            }
        }
    }

    @Test
    fun `get format`() = runTest {
        with(audioDecoder.format) {
            assertEquals(5_000_000L, durationMicros)
            assertEquals(44100, sampleRate)
            assertEquals(2, channels)
            assertEquals(0, width)
            assertEquals(0, height)
            assertEquals(0.0, frameRate)
        }
        with(videoDecoder.format) {
            assertEquals(5_000_000L, durationMicros)
            assertEquals(0, sampleRate)
            assertEquals(0, channels)
            assertEquals(500, width)
            assertEquals(500, height)
            assertEquals(25.0, frameRate)
        }
        with(mediaDecoder.format) {
            assertEquals(5_000_000L, durationMicros)
            assertEquals(44100, sampleRate)
            assertEquals(2, channels)
            assertEquals(500, width)
            assertEquals(500, height)
            assertEquals(25.0, frameRate)
        }
    }

    @Test
    fun `get next frame`() = runTest {
        with(audioDecoder.nextFrame(0, 0)!!) {
            assertEquals(NativeFrame.Type.AUDIO.ordinal, type)
            assertEquals(0L, timestampMicros)
            assertTrue(bytes.isNotEmpty())
        }
        with(videoDecoder.nextFrame(100, 100)!!) {
            assertEquals(NativeFrame.Type.VIDEO.ordinal, type)
            assertEquals(0L, timestampMicros)
            assertTrue(bytes.isNotEmpty())
        }
        with(mediaDecoder.nextFrame(100, 100)!!) {
            assertTrue(type == NativeFrame.Type.AUDIO.ordinal || type == NativeFrame.Type.VIDEO.ordinal)
            assertEquals(0L, timestampMicros)
            assertTrue(bytes.isNotEmpty())
        }
    }

    @Test
    fun `seek media`() = runTest {
        audioDecoder.seekTo((0L..audioDecoder.format.durationMicros).random(), false)
        videoDecoder.seekTo((0L..videoDecoder.format.durationMicros).random(), false)
        mediaDecoder.seekTo((0L..mediaDecoder.format.durationMicros).random(), false)
    }

    @Test
    fun `reset media`() = runTest {
        audioDecoder.reset()
        videoDecoder.reset()
        mediaDecoder.reset()
    }
}