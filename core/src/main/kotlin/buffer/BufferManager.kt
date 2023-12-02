package buffer

import decoder.DecodedFrame
import decoder.Decoder
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    suspend fun audioBufferSize(): Int

    /**
     * Gets the current size of the video buffer.
     */
    suspend fun videoBufferSize(): Int

    /**
     * Checks if both the audio and video buffers are empty.
     *
     * @return `true` if both audio and video buffers are empty, `false` otherwise.
     */
    suspend fun bufferIsEmpty(): Boolean

    /**
     * Checks if both the audio and video buffers are full.
     *
     * @return `true` if both audio and video buffers are full, `false` otherwise.
     */
    suspend fun bufferIsFull(): Boolean

    /**
     * Retrieves the first audio frame in the buffer without removing it.
     *
     * @return The first audio frame in the buffer, or `null` if the buffer is empty.
     */
    suspend fun firstAudioFrame(): DecodedFrame?

    /**
     * Retrieves the first video frame in the buffer without removing it.
     *
     * @return The first video frame in the buffer, or `null` if the buffer is empty.
     */
    suspend fun firstVideoFrame(): DecodedFrame?

    /**
     * Extracts and removes the next audio frame from the buffer.
     *
     * @return The next audio frame in the buffer, or `null` if the buffer is empty.
     */
    suspend fun extractAudioFrame(): DecodedFrame?

    /**
     * Extracts and removes the next video frame from the buffer.
     *
     * @return The next video frame in the buffer, or `null` if the buffer is empty.
     */
    suspend fun extractVideoFrame(): DecodedFrame?

    /**
     * Starts buffering frames from the associated [Decoder].
     *
     * @return A [Flow] emitting the timestamps of buffered frames.
     */
    suspend fun startBuffering(): Flow<Long>

    /**
     * Flushes the audio and video buffers, discarding all frames.
     */
    suspend fun flush()

    /**
     * Companion object providing a factory method to create a [BufferManager] instance.
     */
    companion object {
        /**
         * Creates a [BufferManager] instance with the specified [decoder] and buffer duration.
         *
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

        private val bufferMutex = Mutex()

        override val audioBufferCapacity = ceil(bufferDurationMillis * decoder.media.audioFrameRate / 1_000L).toInt()

        override val videoBufferCapacity = ceil(bufferDurationMillis * decoder.media.videoFrameRate / 1_000L).toInt()

        private val audioBuffer = LinkedList<DecodedFrame>()

        private val videoBuffer = LinkedList<DecodedFrame>()

        override suspend fun audioBufferSize() = bufferMutex.withLock { audioBuffer.size }

        override suspend fun videoBufferSize() = bufferMutex.withLock { videoBuffer.size }

        override suspend fun bufferIsEmpty() = bufferMutex.withLock {
            (audioBufferCapacity > 0 && audioBuffer.isEmpty()) && (videoBufferCapacity > 0 && videoBuffer.isEmpty())
        }

        override suspend fun bufferIsFull() = bufferMutex.withLock {
            (audioBufferCapacity > 0 && audioBuffer.size >= audioBufferCapacity) && (videoBufferCapacity > 0 && videoBuffer.size >= videoBufferCapacity)
        }

        override suspend fun firstAudioFrame(): DecodedFrame? = bufferMutex.withLock { audioBuffer.peek() }

        override suspend fun firstVideoFrame(): DecodedFrame? = bufferMutex.withLock { videoBuffer.peek() }

        override suspend fun extractAudioFrame(): DecodedFrame? = bufferMutex.withLock { audioBuffer.poll() }

        override suspend fun extractVideoFrame(): DecodedFrame? = bufferMutex.withLock { videoBuffer.poll() }

        override suspend fun startBuffering() = flow {
            while (currentCoroutineContext().isActive) {
                if (audioBuffer.size < audioBufferCapacity || videoBuffer.size < videoBufferCapacity) {
                    decoder.nextFrame()?.let { frame ->

                        emit(frame.timestampNanos)

                        bufferMutex.withLock {
                            when (frame) {
                                is DecodedFrame.Audio -> audioBuffer.add(frame)

                                is DecodedFrame.Video -> videoBuffer.add(frame)

                                is DecodedFrame.End -> {
                                    audioBuffer.add(frame)
                                    videoBuffer.add(frame)

                                    /**
                                     * Buffering has been completed
                                     */

                                    return@flow
                                }
                            }
                        }
                    }
                }
            }
        }

        override suspend fun flush() = bufferMutex.withLock {
            audioBuffer.clear()
            videoBuffer.clear()

            /**
             * Buffer has been flushed
             */
        }
    }
}