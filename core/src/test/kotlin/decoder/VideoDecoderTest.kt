package decoder

import frame.Frame
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import media.Media
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random

class VideoDecoderTest {
    private lateinit var decoder: Decoder<Frame.Video>

    private val nativeDecoder = mockk<NativeDecoder>()

    private val media = mockk<Media>()

    @BeforeEach
    fun beforeEach() {
        clearMocks(nativeDecoder, media)

        coEvery { nativeDecoder.format } coAnswers {
            NativeFormat(
                "", 1000L, 0, 0, 100, 100, 30.0
            )
        }

        decoder = VideoDecoder(decoder = nativeDecoder, media = media)
    }

    @AfterEach
    fun afterEach() {
        decoder.close()
    }

    @Test
    fun nextFrame() = runTest {
        val frame = NativeFrame(
            NativeFrame.Type.VIDEO.ordinal, 0L, Random(System.currentTimeMillis()).nextBytes(10)
        )

        coEvery { nativeDecoder.nextFrame() } coAnswers { frame }

        assertEquals(
            Frame.Video.Content(0L, frame.bytes, 100, 100, 30.0), decoder.nextFrame().getOrThrow()
        )
    }

    @Test
    fun seekTo() = runTest {
        coEvery { nativeDecoder.seekTo(any()) } returns Unit

        assertTrue(decoder.seekTo(0L).isSuccess)
    }

    @Test
    fun reset() = runTest {
        coEvery { nativeDecoder.reset() } returns Unit

        assertTrue(decoder.reset().isSuccess)
    }
}