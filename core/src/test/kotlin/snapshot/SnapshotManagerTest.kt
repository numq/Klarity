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
    private val decoder = mockk<Decoder<Frame.Video>>(relaxUnitFun = true)

    @Test
    fun `single snapshot`() = runTest {
        val frame = Frame.Video.Content(0L, Random(System.currentTimeMillis()).nextBytes(10), 100, 100, 30.0)

        every { decoder.media.durationMicros } returns 0L

        every { decoder.seekTo(any()) } returns Result.success(Unit)

        coEvery { decoder.nextFrame() } returns Result.success(frame)

        every { Decoder.createVideoDecoder(any()) } returns Result.success(decoder)

        val result = SnapshotManager.snapshot("", timestampMillis = { 0L })

        assertEquals(Result.success(frame), result)
    }

    @Test
    fun `multiple snapshots`() = runTest {
        val frames = buildList {
            repeat(10) {
                add(Frame.Video.Content(0L, Random(System.currentTimeMillis()).nextBytes(10), 100, 100, 30.0))
            }
        }

        every { decoder.media.durationMicros } returns 0L

        every { decoder.seekTo(any()) } returns Result.success(Unit)

        coEvery { decoder.nextFrame() } returnsMany frames.map { Result.success(it) }

        every { Decoder.createVideoDecoder(any()) } returns Result.success(decoder)

        val result = SnapshotManager.snapshots(
            "",
            timestampsMillis = { frames.map(Frame.Video.Content::timestampMicros) }
        )

        assertEquals(Result.success(frames), result)
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