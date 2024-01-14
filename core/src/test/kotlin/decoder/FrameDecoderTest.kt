package decoder

import frame.DecodedFrame
import kotlinx.coroutines.test.runTest
import media.Media
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URL
import javax.sound.sampled.AudioFormat
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

class FrameDecoderTest {
    companion object {
        private lateinit var decoder: Decoder

        private val filePaths = ClassLoader.getSystemResources("files")
            .nextElement()
            .let(URL::getFile)
            .let(::File)
            .listFiles()
            ?.filter(File::exists)
            ?.mapNotNull(File::getAbsolutePath)!!

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            require(filePaths.isNotEmpty())
        }
    }

    @BeforeEach
    fun beforeEach() {
        decoder = Decoder.create()
    }

    @AfterEach
    fun afterEach() {
        decoder.close()
    }

    @Test
    fun `static creation`() {
        assertInstanceOf(Decoder::class.java, Decoder.create())
    }

    @Test
    fun `create a media`() = runTest {
        filePaths.forEach { filePath ->
            val media = Decoder.createMedia(filePath)

            println(media)

            assertNotNull(media)

            with(media as Media.Local) {

                assertEquals(filePath, path)

                assertEquals(File(filePath).name, name)

                if (filePath.contains("audio")) {
                    assertEquals(
                        AudioFormat(
                            44100F,
                            16,
                            2,
                            true,
                            false
                        ).toString(),
                        info.audioFormat!!.toString()
                    )
                }

                assertEquals(5.seconds.inWholeNanoseconds, info.durationNanos)

                assertEquals(media.info.frameRate, info.frameRate)

                if (filePath.contains("sink")) assertEquals(500 to 500, info.size)
            }
        }
    }

    @Test
    fun `take a snapshot`() = runTest {
        decoder.initialize(Media.create(filePaths.first { it.contains("sink") })!!)

        assertTrue(decoder.snapshot(0L, null)?.bytes?.isNotEmpty()!!)
    }

    @Test
    fun `initialize and dispose`() = runTest {
        filePaths.forEach { filePath ->
            decoder.initialize(Media.create(filePath)!!)

            assertEquals(filePath, (decoder.media as Media.Local).path)

            decoder.dispose()

            assertNull(decoder.media)
        }
    }

    @Test
    fun `decode media`() = runTest {

        val filePath = filePaths.random()

        decoder.initialize(Media.create(filePath)!!)

        val audioFrames = mutableListOf<DecodedFrame.Audio>()

        val videoFrames = mutableListOf<DecodedFrame.Video>()

        var endFrame: DecodedFrame.End? = null

        while (endFrame == null) {
            decoder.readFrame()?.let { frame ->
                when (frame) {
                    is DecodedFrame.Audio -> audioFrames.add(frame)
                    is DecodedFrame.Video -> videoFrames.add(frame)
                    is DecodedFrame.End -> {
                        endFrame = frame
                    }

                    else -> Unit
                }
            }
        }

        if (filePath.contains("audio")) assertTrue(audioFrames.isNotEmpty())

        if (filePath.contains("sink")) assertTrue(videoFrames.isNotEmpty())

        assertEquals(DecodedFrame.End(5.seconds.inWholeNanoseconds), endFrame)

        decoder.dispose()
    }

    @Test
    fun `seek to desired timestamp and return actual position`() = runTest {
        decoder.initialize(Media.create(filePaths.random())!!)

        var previousTimestamp: Long? = null

        repeat(5) {
            val randomTimestampMicros =
                (0L..decoder.media!!.info.durationNanos).random().nanoseconds.inWholeMicroseconds

            assertNotEquals(previousTimestamp, decoder.seekTo(randomTimestampMicros))

            previousTimestamp = randomTimestampMicros
        }
    }
}