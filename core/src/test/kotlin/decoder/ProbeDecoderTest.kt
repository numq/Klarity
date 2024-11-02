package decoder

import com.github.numq.klarity.core.decoder.Decoder
import com.github.numq.klarity.core.format.AudioFormat
import com.github.numq.klarity.core.format.VideoFormat
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.loader.Klarity
import com.github.numq.klarity.core.media.Location
import com.github.numq.klarity.core.media.Media
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URL
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.seconds

class ProbeDecoderTest {
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

    private val file = files?.find { file -> file.nameWithoutExtension == "audio_video" }!!

    private val location = file.absolutePath

    private lateinit var decoder: Decoder<Media, Frame.Probe>

    @BeforeEach
    fun beforeEach() {
        decoder = Decoder.createProbeDecoder(
            location = location,
            findAudioStream = true,
            findVideoStream = true
        ).getOrThrow()
    }

    @AfterEach
    fun afterEach() {
        decoder.close()
    }

    @Test
    fun `create decoder and ensure that media is correct`() = runTest {
        val media = decoder.media

        assertNotNull(media)

        assertTrue(media is Media.AudioVideo)

        assertEquals(Location.Local(fileName = file.name, path = location), media.location)

        assertEquals(5.seconds, media.durationMicros.microseconds)

        with(media as Media.AudioVideo) {
            assertEquals(AudioFormat(sampleRate = 44100, channels = 2), media.audioFormat)
            assertEquals(VideoFormat(width = 500, height = 500, frameRate = 25.0), media.videoFormat)
        }
    }
}