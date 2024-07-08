package snapshot

import decoder.Decoder
import frame.Frame
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.random.Random

class SnapshotManagerTest {
    private val decoder = mockk<Decoder<Frame.Video>>()

    @Test
    fun `single snapshot`() = runTest {
        val frame = Frame.Video.Content(0L, Random(System.currentTimeMillis()).nextBytes(10), 100, 100, 30.0)

        coEvery { decoder.nextFrame() } returns Result.success(frame)

        every { decoder.close() } returns Unit

        mockkObject(Decoder.Companion)

        coEvery { Decoder.createVideoDecoder(any()) } returns Result.success(decoder)

        val result = SnapshotManager.snapshot("", 0L)

        assertEquals(Result.success(frame), result)

        coVerify { decoder.nextFrame() }

        verify { decoder.close() }

        unmockkObject(Decoder.Companion)
    }

    @Test
    fun `multiple snapshots`() = runTest {
        val frames = buildList {
            repeat(10) {
                add(Frame.Video.Content(0L, Random(System.currentTimeMillis()).nextBytes(10), 100, 100, 30.0))
            }
        }

        coEvery { decoder.nextFrame() } returnsMany frames.map { Result.success(it) }

        every { decoder.close() } returns Unit

        mockkObject(Decoder.Companion)

        every { Decoder.createVideoDecoder(any()) } returns Result.success(decoder)

        val result = SnapshotManager.snapshots("", frames.map(Frame::timestampMicros))

        assertEquals(Result.success(frames), result)

        coVerify(exactly = frames.size) { decoder.nextFrame() }

        verify { decoder.close() }

        unmockkObject(Decoder.Companion)
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            mockkObject(Decoder.Companion)
        }

        @JvmStatic
        @AfterAll
        fun afterAll() {
            unmockkObject(Decoder.Companion)
        }
    }
}