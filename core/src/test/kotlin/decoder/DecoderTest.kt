package decoder

import JNITest
import com.github.numq.klarity.core.decoder.Decoder
import com.github.numq.klarity.core.decoder.HardwareAcceleration
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DecoderTest : JNITest() {
    @Test
    fun `should throw an exception on empty location`() = runTest {
        assertThrows<IllegalStateException> {
            Decoder.createProbeDecoder(
                location = "",
                findAudioStream = true,
                findVideoStream = true,
                hardwareAcceleration = HardwareAcceleration.NONE
            ).getOrThrow()
        }
        assertThrows<IllegalStateException> {
            Decoder.createAudioDecoder(location = "").getOrThrow()
        }
        assertThrows<IllegalStateException> {
            Decoder.createVideoDecoder(location = "", hardwareAcceleration = HardwareAcceleration.NONE).getOrThrow()
        }
    }
}