package decoder

import frame.Frame
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import media.Media
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random

class AudioDecoderTest {
    private lateinit var decoder: Decoder<Frame.Audio>

    private val nativeDecoder = mockk<NativeDecoder>(relaxUnitFun = true)

    private val media = mockk<Media>()

    @BeforeEach
    fun beforeEach() {
        clearMocks(nativeDecoder, media)

        coEvery { nativeDecoder.format } coAnswers {
            NativeFormat(
                "", 1000L, 44100, 2, 0, 0, 0.0
            )
        }

        decoder = AudioDecoder(decoder = nativeDecoder, media = media)
    }

    @AfterEach
    fun afterEach() {
        decoder.close()
    }

    @Test
    fun nextFrame() = runTest {
        val frame = NativeFrame(
            NativeFrame.Type.AUDIO.ordinal, 0L, Random(System.currentTimeMillis()).nextBytes(10)
        )

        coEvery { nativeDecoder.nextFrame() } coAnswers { frame }

        assertEquals(
            Frame.Audio.Content(0L, frame.bytes, 2, 44100), decoder.nextFrame().getOrThrow()
        )

        coVerify { nativeDecoder.format }

        coVerify { nativeDecoder.nextFrame() }
    }

    @Test
    fun seekTo() = runTest {
        coEvery { nativeDecoder.seekTo(any()) } returns Unit

        assertTrue(decoder.seekTo(0L).isSuccess)

        coVerify { nativeDecoder.seekTo(any()) }
    }

    @Test
    fun reset() = runTest {
        coEvery { nativeDecoder.reset() } returns Unit

        assertTrue(decoder.reset().isSuccess)

        coVerify { nativeDecoder.reset() }
    }
}