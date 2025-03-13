package decoder

import JNITest
import com.github.numq.klarity.core.decoder.Decoder
import com.github.numq.klarity.core.format.VideoFormat
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.media.Location
import com.github.numq.klarity.core.media.Media
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URL
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.seconds

class VideoDecoderTest : JNITest() {
    private val files = File(ClassLoader.getSystemResources("files").nextElement().let(URL::getFile)).listFiles()

    private val file = files?.find { file -> file.nameWithoutExtension == "video_only" }!!

    private val location = file.absolutePath

    private lateinit var decoder: Decoder<Media.Video, Frame.Video>

    @BeforeEach
    fun beforeEach() {
        decoder = Decoder.createVideoDecoder(location = location).getOrThrow()
    }

    @AfterEach
    fun afterEach() {
        decoder.close()
    }

    @Test
    fun `create decoder and ensure that media is correct`() = runTest {
        val media = decoder.media

        assertNotNull(media)

        assertEquals(Location.Local(path = location, name = file.name), media.location)

        assertEquals(5.seconds, media.durationMicros.microseconds)

        assertEquals(VideoFormat(width = 500, height = 500, frameRate = 25.0), media.format)
    }

    @Test
    fun `return next frame until the end of media`() = runTest {
        var frames = 0

        while (isActive) {
            when (val frame = decoder.nextFrame(width = null, height = null).getOrThrow()) {
                is Frame.Video.Content -> {
                    assertEquals(500, frame.width)
                    assertEquals(500, frame.height)
                    assertEquals(25.0, frame.frameRate)
                    assertTrue(frame.timestampMicros < decoder.media.durationMicros)
                    frames += 1
                }

                is Frame.Video.EndOfStream -> break
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