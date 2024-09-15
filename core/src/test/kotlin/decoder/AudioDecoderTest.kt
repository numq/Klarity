package decoder

import format.AudioFormat
import frame.Frame
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import library.Klarity
import media.Location
import media.Media
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URL
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.seconds

class AudioDecoderTest {
    init {
        File(ClassLoader.getSystemResources("bin/decoder").nextElement().let(URL::getFile)).listFiles()?.run {
            Klarity.loadDecoder(
                ffmpegPath = find { file -> file.path.endsWith("ffmpeg") }!!.path,
                klarityPath = find { file -> file.path.endsWith("klarity") }!!.path,
                jniPath = find { file -> file.path.endsWith("jni") }!!.path
            ).getOrThrow()
        }
    }

    private val files = File(ClassLoader.getSystemResources("files").nextElement().let(URL::getFile)).listFiles()

    private val file = files?.find { file -> file.nameWithoutExtension == "audio_only" }!!

    private val location = file.absolutePath

    private lateinit var decoder: Decoder<Media.Audio, Frame.Audio>

    @BeforeEach
    fun beforeEach() {
        decoder = Decoder.createAudioDecoder(location = location).getOrThrow()
    }

    @AfterEach
    fun afterEach() {
        decoder.close()
    }

    @Test
    fun `create decoder and ensure that media is correct`() = runTest {
        val media = decoder.media

        assertNotNull(media)

        assertEquals(Location.Local(fileName = file.name, path = location), media.location)

        assertEquals(5.seconds, media.durationMicros.microseconds)

        assertEquals(AudioFormat(sampleRate = 44100, channels = 2), (media as Media.Audio).format)
    }

    @Test
    fun `return next frame until the end of media`() = runTest {
        var frames = 0

        while (isActive) {
            when (val frame = decoder.nextFrame(width = null, height = null).getOrThrow()) {
                is Frame.Audio.Content -> {
                    assertEquals(44100, frame.sampleRate)
                    assertEquals(2, frame.channels)
                    assertTrue(frame.timestampMicros < decoder.media.durationMicros)
                    frames += 1
                }

                is Frame.Audio.EndOfStream -> break
            }
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