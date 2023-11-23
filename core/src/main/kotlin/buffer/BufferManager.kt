package buffer

import decoder.DecodedFrame
import decoder.Decoder
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.util.*
import kotlin.math.ceil

/**
 * Interface representing a buffer manager for handling audio and video frames.
 */
interface BufferManager {

    /**
     * Gets the capacity of the audio buffer.
     */
    val audioBufferCapacity: Int

    /**
     * Gets the capacity of the video buffer.
     */
    val videoBufferCapacity: Int

    /**
     * Gets the current size of the audio buffer.
     */
    fun audioBufferSize(): Int

    /**
     * Gets the current size of the video buffer.
     */
    fun videoBufferSize(): Int

    /**
     * Checks if both the audio and video buffers are empty.
     * @return `true` if both audio and video buffers are empty, `false` otherwise.
     */
    fun bufferIsEmpty(): Boolean

    /**
     * Checks if both the audio and video buffers are full.
     * @return `true` if both audio and video buffers are full, `false` otherwise.
     */
    fun bufferIsFull(): Boolean

    /**
     * Retrieves the first audio frame in the buffer without removing it.
     * @return The first audio frame in the buffer, or `null` if the buffer is empty.
     */
    suspend fun firstAudioFrame(): DecodedFrame?

    /**
     * Retrieves the first video frame in the buffer without removing it.
     * @return The first video frame in the buffer, or `null` if the buffer is empty.
     */
    suspend fun firstVideoFrame(): DecodedFrame?

    /**
     * Extracts and removes the next audio frame from the buffer.
     * @return The next audio frame in the buffer, or `null` if the buffer is empty.
     */
    suspend fun extractAudioFrame(): DecodedFrame?

    /**
     * Extracts and removes the next video frame from the buffer.
     * @return The next video frame in the buffer, or `null` if the buffer is empty.
     */
    suspend fun extractVideoFrame(): DecodedFrame?

    /**
     * Starts buffering frames from the associated [Decoder].
     * @return A [Flow] emitting the timestamps of buffered frames.
     */
    suspend fun startBuffering(): Flow<Long>

    /**
     * Flushes the audio and video buffers, discarding all frames.
     */
    fun flush()

    /**
     * Companion object providing a factory method to create a [BufferManager] instance.
     */
    companion object {
        /**
         * Creates a [BufferManager] instance with the specified [decoder] and buffer duration.
         * @param decoder The [Decoder] used to decode frames.
         * @param bufferDurationMillis The duration, in milliseconds, for which the buffer should store frames.
         * @return A [BufferManager] instance.
         */
        fun create(
            decoder: Decoder,
            bufferDurationMillis: Long,
        ): BufferManager = Implementation(
            decoder = decoder,
            bufferDurationMillis = bufferDurationMillis
        )
    }

    private class Implementation(
        private val decoder: Decoder,
        bufferDurationMillis: Long,
    ) : BufferManager {

        override val audioBufferCapacity = ceil(bufferDurationMillis * decoder.media.audioFrameRate / 1_000L).toInt()
            .also { println("audio buffer capacity: $it") }

        override val videoBufferCapacity = ceil(bufferDurationMillis * decoder.media.videoFrameRate / 1_000L).toInt()
            .also { println("video buffer capacity: $it") }

        private val audioBuffer = LinkedList<DecodedFrame>()

        private val videoBuffer = LinkedList<DecodedFrame>()

        override fun audioBufferSize() = audioBuffer.size

        override fun videoBufferSize() = videoBuffer.size

        override fun bufferIsEmpty() =
            (audioBufferCapacity > 0 && audioBuffer.isEmpty()) && (videoBufferCapacity > 0 && videoBuffer.isEmpty())

        override fun bufferIsFull() =
            (audioBufferCapacity > 0 && audioBuffer.size >= audioBufferCapacity) && (videoBufferCapacity > 0 && videoBuffer.size >= videoBufferCapacity)

        override suspend fun firstAudioFrame(): DecodedFrame? = audioBuffer.peek()

        override suspend fun firstVideoFrame(): DecodedFrame? = videoBuffer.peek()

        override suspend fun extractAudioFrame(): DecodedFrame? = audioBuffer.poll()

        override suspend fun extractVideoFrame(): DecodedFrame? = videoBuffer.poll()

        override suspend fun startBuffering() = flow {
            coroutineScope {
                while (isActive) {
                    if (audioBuffer.size < audioBufferCapacity || videoBuffer.size < videoBufferCapacity) {
                        decoder.nextFrame()?.let { frame ->

                            emit(frame.timestampNanos)

                            when (frame) {
                                is DecodedFrame.Audio -> audioBuffer.add(frame)

                                is DecodedFrame.Video -> videoBuffer.add(frame)

                                is DecodedFrame.End -> {
                                    audioBuffer.add(frame)
                                    videoBuffer.add(frame)
                                    return@coroutineScope println("Buffering has been completed")
                                }
                            }
                        }
                    }
                }
            }
        }

        override fun flush() {
            audioBuffer.clear()
            videoBuffer.clear()

            println("Buffer has been flushed")
        }
    }
}