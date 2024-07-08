package decoder

import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import media.Media
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ProbeDecoderTest {
    private lateinit var decoder: Decoder<Nothing>

    private val nativeDecoder = mockk<NativeDecoder>()

    private val media = mockk<Media>()

    @BeforeEach
    fun beforeEach() {
        clearMocks(nativeDecoder, media)
        decoder = ProbeDecoder(decoder = nativeDecoder, media = media)
    }

    @AfterEach
    fun afterEach() {
        decoder.close()
    }

    @Test
    fun nextFrame() = runTest {
        coEvery { nativeDecoder.nextFrame() } coAnswers { null }

        assertNull(decoder.nextFrame().getOrThrow())
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