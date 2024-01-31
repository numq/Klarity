package buffer

import frame.DecodedFrame
import io.mockk.every
import io.mockk.mockk
import media.Media
import media.MediaInfo
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FrameBufferTest {

    private lateinit var buffer: FrameBuffer

    private val media = mockk<Media>()

    @BeforeEach
    fun beforeEach() {
        buffer = FrameBuffer.create(media)

        every { media.hasAudio() } returns true

        every { media.hasVideo() } returns true
    }

    @Test
    fun `static creation`() {
        assertInstanceOf(FrameBuffer::class.java, FrameBuffer.create(Media.Local(0L, "", "", MediaInfo(0L))))
        assertInstanceOf(FrameBuffer::class.java, FrameBuffer.create(Media.Remote(0L, "", MediaInfo(0L))))
    }

    @Test
    fun `change duration`() {
        buffer.changeAudioBufferCapacity(1)

        buffer.changeVideoBufferCapacity(1)

        assertEquals(1, buffer.audioBufferCapacity())

        assertEquals(1, buffer.videoBufferCapacity())
    }

    @Test
    fun `insert and extract frame`() {
        buffer.changeAudioBufferCapacity(1)

        buffer.changeVideoBufferCapacity(1)

        val audioFrame = DecodedFrame.Audio(0L, byteArrayOf())

        buffer.insertAudioFrame(audioFrame)

        assertFalse(buffer.bufferIsEmpty())

        assertTrue(buffer.bufferIsFull())

        val videoFrame = DecodedFrame.Video(0L, byteArrayOf())

        buffer.insertVideoFrame(videoFrame)

        assertEquals(audioFrame, buffer.firstAudioFrame())

        assertEquals(audioFrame, buffer.extractAudioFrame())

        assertEquals(videoFrame, buffer.firstVideoFrame())

        assertEquals(videoFrame, buffer.extractVideoFrame())

        assertFalse(buffer.bufferIsFull())

        assertTrue(buffer.bufferIsEmpty())
    }
}