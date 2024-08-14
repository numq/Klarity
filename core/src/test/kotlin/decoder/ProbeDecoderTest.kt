package decoder

import io.mockk.clearMocks
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import media.Media
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ProbeDecoderTest {
    private lateinit var decoder: Decoder<Unit>

    private val nativeDecoder = mockk<NativeDecoder>(relaxUnitFun = true)

    private val media = mockk<Media>()

    @BeforeEach
    fun beforeEach() {
        decoder = ProbeDecoder(media = media)
    }

    @AfterEach
    fun afterEach() {
        decoder.close()
        clearMocks(nativeDecoder, media)
    }

    @Test
    fun nextFrame() = runTest {
        assertEquals(Unit, decoder.nextFrame().getOrThrow())
    }

    @Test
    fun seekTo() = runTest {
        assertTrue(decoder.seekTo(0L).isSuccess)
    }

    @Test
    fun reset() = runTest {
        assertTrue(decoder.reset().isSuccess)
    }
}