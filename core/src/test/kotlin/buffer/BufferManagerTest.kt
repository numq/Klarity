package buffer

import decoder.DecodedFrame
import decoder.Decoder
import io.mockk.*
import kotlinx.coroutines.test.runTest
import media.Media
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.sound.sampled.AudioFormat
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class BufferManagerTest {

    companion object {

        private val decoder = mockk<Decoder>()

        @Test
        fun `static creation`() {
            assertNotNull(
                BufferManager.create(
                    decoder = decoder, bufferDurationSeconds = 10
                )
            )
        }

        @JvmStatic
        @AfterAll
        fun afterAll() {
            unmockkAll()
        }
    }

    private val decoder = mockkClass(Decoder::class)

    @AfterEach
    fun afterEach() {
        clearAllMocks()
    }

    @Test
    fun `buffering frames`() = runTest {

        val audioFormat = AudioFormat(44100F, 8, 2, true, false)

        val audioFrameRate = 4.0
        val videoFrameRate = 2.0

        val durationSeconds = 2

        val durationNanos = durationSeconds.seconds.inWholeNanoseconds

        every {
            decoder.media
        } returns Media(
            url = "url",
            name = "name",
            durationNanos = durationNanos,
            audioFormat = audioFormat,
            audioFrameRate = audioFrameRate,
            videoFrameRate = videoFrameRate
        )

        BufferManager.Implementation(
            decoder = decoder, bufferDurationSeconds = 2
        ).run {
            val inputFrames = buildList {
                repeat(durationSeconds) { second ->
                    repeat(audioFrameRate.toInt()) { frameNumber ->
                        add(
                            DecodedFrame.Audio(
                                ((1_000L / audioFrameRate) * frameNumber + (second * 1_000L)).milliseconds.inWholeNanoseconds,
                                byteArrayOf()
                            )
                        )
                    }
                    repeat(videoFrameRate.toInt()) { frameNumber ->
                        add(
                            DecodedFrame.Video(
                                ((1_000L / videoFrameRate) * frameNumber + (second * 1_000L)).milliseconds.inWholeNanoseconds,
                                byteArrayOf()
                            )
                        )
                    }
                }
                add(DecodedFrame.End(durationNanos))
            }

            coEvery {
                decoder.nextFrame()
            } returnsMany inputFrames

            val bufferedTimestamps = mutableListOf<Long?>()

            val outputFrames = mutableListOf<DecodedFrame>()

            startBuffering().collect { bufferTimestampNanos ->
                bufferedTimestamps.add(bufferTimestampNanos)
            }

            while (true) {
                extractAudioFrame()?.let(outputFrames::add) ?: break
            }

            while (true) {
                extractVideoFrame()?.let(outputFrames::add) ?: break
            }

            assertEquals(
                inputFrames.map { it.timestampNanos }, bufferedTimestamps
            )

            assertEquals(inputFrames, outputFrames.filterNot { it is DecodedFrame.End })

            assertFalse(bufferIsEmpty())

            assertEquals(
                audioBufferCapacity, outputFrames.filterIsInstance<DecodedFrame.Audio>().size
            )

            assertEquals(
                videoBufferCapacity, outputFrames.filterIsInstance<DecodedFrame.Video>().size
            )

            flush()

            assertTrue(audioBufferSize() == 0)
            assertTrue(videoBufferSize() == 0)

            assertTrue(bufferIsEmpty())
        }
    }
}