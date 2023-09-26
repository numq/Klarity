package buffer

import decoder.Decoder
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BufferManagerTest {

    companion object {
        private const val MEDIA_URL =
            "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
    }

    @Test
    fun `return error if there is no audio and video`() {
        Decoder.create(MEDIA_URL, decodeVideo = false, decodeAudio = false).use { decoder ->
            assertThrows<BufferException.FailedToCreate> {
                BufferManager.createAudioVideoBuffer(decoder, 0, 0)
            }
        }
    }

    @Test
    fun `audio video buffer creation`() {
        Decoder.create(MEDIA_URL, decodeVideo = true, decodeAudio = true).use { decoder ->
            val audioBufferCapacity = 20
            val videoBufferCapacity = 10
            val buffer = BufferManager.createAudioVideoBuffer(
                decoder,
                videoBufferCapacity = videoBufferCapacity,
                audioBufferCapacity = audioBufferCapacity
            )

            assertEquals(videoBufferCapacity, buffer.videoBufferCapacity)
            assertEquals(audioBufferCapacity, buffer.audioBufferCapacity)
        }
    }

    @Test
    fun `audio buffer creation`() {
        Decoder.create(MEDIA_URL, decodeVideo = false, decodeAudio = true).use { decoder ->
            val audioBufferCapacity = 10
            val buffer = BufferManager.createAudioOnlyBuffer(decoder, audioBufferCapacity)

            assertEquals(0, buffer.videoBufferCapacity)
            assertEquals(audioBufferCapacity, buffer.audioBufferCapacity)
        }
    }

    @Test
    fun `video buffer creation`() {
        Decoder.create(MEDIA_URL, decodeVideo = true, decodeAudio = false).use { decoder ->
            val videoBufferCapacity = 10
            val buffer = BufferManager.createVideoOnlyBuffer(decoder, videoBufferCapacity)

            assertEquals(videoBufferCapacity, buffer.videoBufferCapacity)
            assertEquals(0, buffer.audioBufferCapacity)
        }
    }

    @Test
    fun `test buffering`() {
        Decoder.create(MEDIA_URL, decodeVideo = true, decodeAudio = true).use { decoder ->
            val buffer = BufferManager.createAudioVideoBuffer(decoder, 2, 2)
            runBlocking {
                val job = CoroutineScope(currentCoroutineContext()).launch { buffer.startBuffering() }

                delay(1_000L)

                runCatching { job.cancel() }

                assertTrue(buffer.videoBufferIsNotEmpty())
                assertTrue(buffer.audioBufferIsNotEmpty())

                buffer.flush()

                assertFalse(buffer.videoBufferIsNotEmpty())
                assertFalse(buffer.audioBufferIsNotEmpty())
            }
        }
    }
}