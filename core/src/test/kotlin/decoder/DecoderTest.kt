package decoder

import media.MediaSettings
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class DecoderTest {

    companion object {
        private const val EMPTY_URL = ""
        private const val MEDIA_URL =
            "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
    }

    @Test
    fun `creation with empty url`() {
        assertThrows<DecoderException.UnableToCreate> {
            Decoder.create(MediaSettings(EMPTY_URL, hasAudio = false, hasVideo = false)).close()
        }
    }

    @Test
    fun `creation with valid url`() {
        assertDoesNotThrow {
            Decoder.create(MediaSettings(MEDIA_URL, hasAudio = false, hasVideo = false)).close()
        }
    }
}