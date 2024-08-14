package loop

import buffer.Buffer
import frame.Frame
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import loop.buffer.BufferLoop
import loop.playback.DefaultPlaybackLoop
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pipeline.Pipeline
import timestamp.Timestamp

class PlaybackLoopTest {
    private lateinit var bufferLoop: BufferLoop
    private lateinit var pipeline: Pipeline

    @BeforeEach
    fun beforeEach() {
        bufferLoop = mockk(relaxed = true)
        pipeline = mockk<Pipeline.Media>(relaxed = true)
    }

    @AfterEach
    fun afterEach() {
        unmockkAll()
    }

    @Test
    fun `test start and stop lifecycle`() = runTest {
        val playbackLoop = DefaultPlaybackLoop(bufferLoop, pipeline)

        val endOfMedia: suspend () -> Unit = {}

        assertTrue(playbackLoop.start(endOfMedia).isSuccess)

        assertTrue(playbackLoop.stop(resetTime = true).isSuccess)

        assertEquals(Timestamp.ZERO, playbackLoop.timestamp.value)
    }

    @Test
    fun `test waitForTimestamp with media pipeline`() = runTest {
        val audioBuffer = mockk<Buffer<Frame.Audio>>(relaxed = true)
        val videoBuffer = mockk<Buffer<Frame.Video>>(relaxed = true)
        val audioFrame = Frame.Audio.Content(1000, byteArrayOf(), 2, 44100)
        val videoFrame = Frame.Video.Content(2000, byteArrayOf(), 100, 100, 30.0)

        every { (pipeline as Pipeline.Media).audioBuffer } returns audioBuffer
        every { (pipeline as Pipeline.Media).videoBuffer } returns videoBuffer

        coEvery { audioBuffer.peek() } returns Result.success(audioFrame)
        coEvery { videoBuffer.peek() } returns Result.success(videoFrame)

        val playbackLoop = DefaultPlaybackLoop(bufferLoop, pipeline)

        val endOfMedia: suspend () -> Unit = {}

        assertTrue(playbackLoop.start(endOfMedia).isSuccess)

        assertTrue(playbackLoop.stop(resetTime = true).isSuccess)

        assertEquals(Timestamp.ZERO, playbackLoop.timestamp.value)
    }

    @Test
    fun `test timestamp flow`() = runTest {
        val pipeline = mockk<Pipeline.Audio>(relaxed = true)
        val buffer = mockk<Buffer<Frame.Audio>>(relaxed = true)
        val frame = Frame.Audio.Content(1000, byteArrayOf(), 2, 44100)

        every { pipeline.buffer } returns buffer
        coEvery { buffer.peek() } returns Result.success(frame)

        val playbackLoop = DefaultPlaybackLoop(bufferLoop, pipeline)

        val endOfMedia: suspend () -> Unit = {}

        assertTrue(playbackLoop.start(endOfMedia).isSuccess)

        assertEquals(Timestamp.ZERO, playbackLoop.timestamp.first())

        assertTrue(playbackLoop.stop(resetTime = true).isSuccess)

        assertEquals(Timestamp.ZERO, playbackLoop.timestamp.value)
    }
}
