package loop

import com.github.numq.klarity.core.buffer.Buffer
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.loop.buffer.BufferLoop
import com.github.numq.klarity.core.loop.playback.DefaultPlaybackLoop
import com.github.numq.klarity.core.pipeline.Pipeline
import com.github.numq.klarity.core.timestamp.Timestamp
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PlaybackLoopTest {
    private lateinit var bufferLoop: BufferLoop
    private lateinit var pipeline: Pipeline

    @BeforeEach
    fun beforeEach() {
        bufferLoop = mockk(relaxed = true)
        pipeline = mockk<Pipeline.AudioVideo>(relaxed = true)
    }

    @AfterEach
    fun afterEach() {
        unmockkAll()
    }

    @Test
    fun `test start and stop lifecycle`() = runTest {
        val playbackLoop = DefaultPlaybackLoop(bufferLoop, pipeline)

        val onTimestamp: suspend (Timestamp) -> Unit = {}
        val endOfMedia: suspend () -> Unit = {}

        assertTrue(playbackLoop.start(onTimestamp, endOfMedia).isSuccess)

        assertTrue(playbackLoop.stop().isSuccess)
    }

    @Test
    fun `test waitForTimestamp with media pipeline`() = runTest {
        val audioBuffer = mockk<Buffer<Frame.Audio>>(relaxed = true)
        val videoBuffer = mockk<Buffer<Frame.Video>>(relaxed = true)
        val audioFrame = Frame.Audio.Content(1000, byteArrayOf(), 2, 44100)
        val videoFrame = Frame.Video.Content(2000, byteArrayOf(), 100, 100, 30.0)

        every { (pipeline as Pipeline.AudioVideo).audioBuffer } returns audioBuffer
        every { (pipeline as Pipeline.AudioVideo).videoBuffer } returns videoBuffer

        coEvery { audioBuffer.peek() } returns Result.success(audioFrame)
        coEvery { videoBuffer.peek() } returns Result.success(videoFrame)

        val playbackLoop = DefaultPlaybackLoop(bufferLoop, pipeline)

        val onTimestamp: suspend (Timestamp) -> Unit = {}
        val endOfMedia: suspend () -> Unit = {}

        assertTrue(playbackLoop.start(onTimestamp, endOfMedia).isSuccess)

        assertTrue(playbackLoop.stop().isSuccess)
    }

    @Test
    fun `test timestamp flow`() = runTest {
        val pipeline = mockk<Pipeline.Audio>(relaxed = true)
        val buffer = mockk<Buffer<Frame.Audio>>(relaxed = true)
        val frame = Frame.Audio.Content(1000, byteArrayOf(), 2, 44100)

        every { pipeline.buffer } returns buffer
        coEvery { buffer.peek() } returns Result.success(frame)

        val playbackLoop = DefaultPlaybackLoop(bufferLoop, pipeline)

        val onTimestamp: suspend (Timestamp) -> Unit = {}
        val endOfMedia: suspend () -> Unit = {}

        assertTrue(playbackLoop.start(onTimestamp, endOfMedia).isSuccess)

        assertTrue(playbackLoop.stop().isSuccess)
    }
}
