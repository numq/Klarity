package decoder

import JNITest
import com.github.numq.klarity.core.decoder.Decoder
import com.github.numq.klarity.core.format.AudioFormat
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.media.Location
import com.github.numq.klarity.core.media.Media
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URL
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.seconds

class AudioDecoderTest : JNITest() {
    private val files = File(ClassLoader.getSystemResources("files").nextElement().let(URL::getFile)).listFiles()

    private val file = files?.find { file -> file.nameWithoutExtension == "audio_only" }!!

    private val location = file.absolutePath

    private lateinit var decoder: Decoder<Media.Audio, Frame.Audio>

    @BeforeEach
    fun beforeEach() {
        decoder = runBlocking {
            Decoder.createAudioDecoder(location = location).getOrThrow()
        }
    }

    @AfterEach
    fun afterEach() = runBlocking {
        decoder.close()
    }

    @Test
    fun `create decoder and ensure that media is correct`() = runTest {
        val media = decoder.media

        assertNotNull(media)

        assertEquals(Location.Local(path = location, name = file.name), media.location)

        assertEquals(5.seconds, media.durationMicros.microseconds)

        assertEquals(AudioFormat(sampleRate = 44100, channels = 2), media.format)
    }

    @Test
    fun `return next frame until the end of media`() = runTest {
        val expectedTimestamps = buildList {
            val sampleRate = decoder.media.format.sampleRate
            val frameSize = 1024
            val totalFrames = decoder.media.durationMicros.microseconds.inWholeSeconds * sampleRate / frameSize

            repeat(totalFrames.toInt()) { index ->
                add(((index * frameSize * 1_000_000.0 + sampleRate / 2) / sampleRate).toLong())
            }
        }

        val actualTimestamps = buildList {
            while (isActive) {
                when (val frame = decoder.nextFrame(width = null, height = null).getOrThrow()) {
                    is Frame.Audio.Content -> {
                        assertEquals(44100, frame.sampleRate)
                        assertEquals(2, frame.channels)
                        assertTrue(frame.timestampMicros < decoder.media.durationMicros)
                        add(frame.timestampMicros)
                    }

                    is Frame.Audio.EndOfStream -> break
                }
            }
        }

        expectedTimestamps.zip(actualTimestamps).forEach { (expected, actual) ->
            assertEquals(expected, actual)
        }
    }

    @Test
    fun `seek to random timestamp`() = runTest {
        repeat(100) {
            decoder.seekTo(micros = (0L..decoder.media.durationMicros).random(), keyframesOnly = false).getOrThrow()
        }
    }

    @Test
    fun `seek to random timestamp and get next frame`() = runTest {
        repeat(100) {
            decoder.seekTo(micros = (0L..decoder.media.durationMicros).random(), keyframesOnly = false).getOrThrow()
            decoder.nextFrame(width = null, height = null).getOrThrow()
        }
    }

    @Test
    fun `seek to random timestamp and reset`() = runTest {
        repeat(100) {
            decoder.seekTo(micros = (0L..decoder.media.durationMicros).random(), keyframesOnly = false).getOrThrow()
            decoder.reset().getOrThrow()
        }
    }
}