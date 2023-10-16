package buffer

import decoder.Decoder
import io.mockk.*
import media.Media
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BufferManagerTest {

    private val decoder = mockk<Decoder>()

    @BeforeEach
    fun beforeEach() {
        every {
            decoder.media
        } returns Media("name", 1_000L)

        mockkObject(Decoder) {
            every {
                Decoder.create(any())
            } returns decoder
        }
    }

    @AfterEach
    fun afterEach() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `return error if there is no audio and video`() {
        every {
            decoder.hasAudio()
        } returns false

        every {
            decoder.hasVideo()
        } returns false

        assertThrows<BufferException.FailedToCreate> {
            BufferManager.createAudioVideoBuffer(decoder, 0, 0)
        }
    }

    @Test
    fun `audio video buffer creation`() {
        every {
            decoder.hasAudio()
        } returns true

        every {
            decoder.hasVideo()
        } returns true

        val audioBufferCapacity = (1..100).random()
        val videoBufferCapacity = (1..100).random()

        val buffer = BufferManager.createAudioVideoBuffer(
            decoder, audioBufferCapacity = audioBufferCapacity, videoBufferCapacity = videoBufferCapacity
        )

        assertEquals(audioBufferCapacity, buffer.audioBufferCapacity)
        assertEquals(videoBufferCapacity, buffer.videoBufferCapacity)
    }

    @Test
    fun `audio buffer creation`() {
        every {
            decoder.hasAudio()
        } returns true

        every {
            decoder.hasVideo()
        } returns false

        val audioBufferCapacity = 10
        val buffer = BufferManager.createAudioOnlyBuffer(decoder, audioBufferCapacity)

        assertEquals(0, buffer.videoBufferCapacity)
        assertEquals(audioBufferCapacity, buffer.audioBufferCapacity)
    }

    @Test
    fun `video buffer creation`() {
        every {
            decoder.hasAudio()
        } returns false

        every {
            decoder.hasVideo()
        } returns true

        val videoBufferCapacity = 10
        val buffer = BufferManager.createVideoOnlyBuffer(decoder, videoBufferCapacity)

        assertEquals(videoBufferCapacity, buffer.videoBufferCapacity)
        assertEquals(0, buffer.audioBufferCapacity)
    }
}