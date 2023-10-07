package player

import decoder.DecoderException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class PlayerControllerTest {

    companion object {
        private const val EMPTY_URL = ""
        private const val MEDIA_URL =
            "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
    }

    @Test
    fun `media player creation`() {
        assertThrows<DecoderException.UnableToCreate> {
            PlayerController.create(EMPTY_URL, decodeVideo = true, decodeAudio = true).close()
        }
        assertDoesNotThrow {
            PlayerController.create(MEDIA_URL, decodeVideo = true, decodeAudio = true).close()
        }
    }

    @Test
    fun `media player playback`() {
        runBlocking {
            PlayerController.create(MEDIA_URL, decodeVideo = true, decodeAudio = true).use { player ->
                assertEquals(PlaybackStatus.STOPPED, player.status.value)
                player.play()
                delay(1_000L)
                assertEquals(PlaybackStatus.PLAYING, player.status.value)
                player.pause()
                delay(1_000L)
                assertEquals(PlaybackStatus.PAUSED, player.status.value)
                player.stop()
                delay(1_000L)
                assertEquals(PlaybackStatus.STOPPED, player.status.value)
            }
        }
    }
}