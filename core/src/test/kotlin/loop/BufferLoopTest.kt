package loop

import buffer.Buffer
import decoder.Decoder
import frame.Frame
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import loop.buffer.DefaultBufferLoop
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pipeline.Pipeline
import timestamp.Timestamp

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

        val onWaiting: suspend () -> Unit = {}
        val endOfMedia: suspend () -> Unit = {}

        assertTrue(bufferLoop.start(onWaiting, endOfMedia).isSuccess)

        delay(1_000L)

        assertTrue(bufferLoop.isBuffering.get())

        assertTrue(bufferLoop.stop(resetTime = true).isSuccess)

        assertEquals(Timestamp.ZERO, bufferLoop.timestamp.value)

        assertFalse(bufferLoop.isBuffering.get())
    }

    @Test
    fun `test isWaiting state changes`() = runTest {
        val pipeline = mockk<Pipeline.Audio>(relaxed = true)
        val decoder = mockk<Decoder<Frame.Audio>>(relaxed = true)
        val buffer = mockk<Buffer<Frame.Audio>>(relaxed = true)

        every { pipeline.decoder } returns decoder
        every { pipeline.buffer } returns buffer
        coEvery { decoder.nextFrame() } returns Result.success(Frame.Audio.Content(0, byteArrayOf(), 2, 44100))

        val bufferLoop = DefaultBufferLoop(pipeline)

        val onWaiting: suspend () -> Unit = {}
        val endOfMedia: suspend () -> Unit = {}

        assertTrue(bufferLoop.start(onWaiting, endOfMedia).isSuccess)

        bufferLoop.timestamp.first()

        assertTrue(bufferLoop.stop(resetTime = true).isSuccess)

        assertEquals(Timestamp.ZERO, bufferLoop.timestamp.value)
    }

    @Test
    fun `test timestamp flow`() = runTest {
        val pipeline = mockk<Pipeline.Audio>(relaxed = true)
        val decoder = mockk<Decoder<Frame.Audio>>(relaxed = true)
        val buffer = mockk<Buffer<Frame.Audio>>(relaxed = true)

        every { pipeline.decoder } returns decoder
        every { pipeline.buffer } returns buffer
        coEvery { decoder.nextFrame() } returns Result.success(Frame.Audio.Content(0, byteArrayOf(), 2, 44100))

        val bufferLoop = DefaultBufferLoop(pipeline)

        val onWaiting: suspend () -> Unit = {}
        val endOfMedia: suspend () -> Unit = {}

        assertTrue(bufferLoop.start(onWaiting, endOfMedia).isSuccess)

        assertEquals(Timestamp.ZERO, bufferLoop.timestamp.first())

        assertTrue(bufferLoop.stop(resetTime = true).isSuccess)

        assertEquals(Timestamp.ZERO, bufferLoop.timestamp.value)
    }
}