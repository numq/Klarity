package loop

import com.github.numq.klarity.core.buffer.Buffer
import com.github.numq.klarity.core.decoder.Decoder
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.loop.buffer.DefaultBufferLoop
import com.github.numq.klarity.core.media.Media
import com.github.numq.klarity.core.pipeline.Pipeline
import com.github.numq.klarity.core.timestamp.Timestamp
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BufferLoopTest {
    private lateinit var pipeline: Pipeline

    @BeforeEach
    fun beforeEach() {
        pipeline = mockk<Pipeline>(relaxed = true)
    }

    @AfterEach
    fun afterEach() {
        unmockkAll()
    }

    @Test
    fun `test start and stop lifecycle`() = runTest {
        val bufferLoop = DefaultBufferLoop(pipeline)

        val onTimestamp: suspend (Timestamp) -> Unit = {}
        val onWaiting: suspend () -> Unit = {}
        val endOfMedia: suspend () -> Unit = {}

        assertTrue(bufferLoop.start(onTimestamp, onWaiting, endOfMedia).isSuccess)

        delay(1_000L)

        assertTrue(bufferLoop.isBuffering.get())

        assertTrue(bufferLoop.stop().isSuccess)

        assertFalse(bufferLoop.isBuffering.get())
    }

    @Test
    fun `test isWaiting state changes`() = runTest {
        val pipeline = mockk<Pipeline.Audio>(relaxed = true)
        val decoder = mockk<Decoder<Media.Audio, Frame.Audio>>(relaxed = true)
        val buffer = mockk<Buffer<Frame.Audio>>(relaxed = true)

        every { pipeline.decoder } returns decoder
        every { pipeline.buffer } returns buffer
        coEvery { decoder.nextFrame(any(), any()) } returns Result.success(
            Frame.Audio.Content(
                0,
                byteArrayOf(),
                2,
                44100
            )
        )

        val bufferLoop = DefaultBufferLoop(pipeline)

        val onTimestamp: suspend (Timestamp) -> Unit = {}
        val onWaiting: suspend () -> Unit = {}
        val endOfMedia: suspend () -> Unit = {}

        assertTrue(bufferLoop.start(onTimestamp, onWaiting, endOfMedia).isSuccess)

        assertTrue(bufferLoop.stop().isSuccess)
    }

    @Test
    fun `test timestamp flow`() = runTest {
        val pipeline = mockk<Pipeline.Audio>(relaxed = true)
        val decoder = mockk<Decoder<Media.Audio, Frame.Audio>>(relaxed = true)
        val buffer = mockk<Buffer<Frame.Audio>>(relaxed = true)

        every { pipeline.decoder } returns decoder
        every { pipeline.buffer } returns buffer
        coEvery { decoder.nextFrame(any(), any()) } returns Result.success(
            Frame.Audio.Content(
                0,
                byteArrayOf(),
                2,
                44100
            )
        )

        val bufferLoop = DefaultBufferLoop(pipeline)

        val onTimestamp: suspend (Timestamp) -> Unit = {}
        val onWaiting: suspend () -> Unit = {}
        val endOfMedia: suspend () -> Unit = {}

        assertTrue(bufferLoop.start(onTimestamp, onWaiting, endOfMedia).isSuccess)

        assertTrue(bufferLoop.stop().isSuccess)
    }
}