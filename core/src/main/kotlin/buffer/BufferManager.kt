package buffer

import frame.DecodedFrame
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
 * Interface defining the contract for managing audio and video buffers.
 */
interface BufferManager {

    /**
     * Gets the duration of the buffer in milliseconds.
     */
    val bufferDurationMillis: Long

    /**
     * Changes the duration of the buffer. If [durationMillis] is non-null, the buffer duration is set to
     * the provided value; otherwise, it is set to the default value [DEFAULT_BUFFER_DURATION_MILLIS].
     *
     * @param durationMillis New duration in milliseconds.
     */
    fun changeDuration(durationMillis: Long?)

    /**
     * Retrieves the current capacity (limit) of the audio buffer.
     */
    fun audioBufferCapacity(): Int

    /**
     * Retrieves the current capacity (limit) of the video buffer.
     */
    fun videoBufferCapacity(): Int

    /**
     * Retrieves the current size of the audio buffer.
     */
    suspend fun audioBufferSize(): Int

    /**
     * Retrieves the current size of the video buffer.
     */
    suspend fun videoBufferSize(): Int

    /**
     * Checks if both audio and video buffers are empty.
     */
    suspend fun bufferIsEmpty(): Boolean

    /**
     * Checks whether either the audio or video buffer is full.
     */
    suspend fun bufferIsFull(): Boolean

    /**
     * Retrieves the first audio frame from the buffer without removing it.
     */
    suspend fun firstAudioFrame(): DecodedFrame?

    /**
     * Retrieves the first video frame from the buffer without removing it.
     */
    suspend fun firstVideoFrame(): DecodedFrame?

    /**
     * Removes and retrieves an audio frame from the buffer.
     */
    suspend fun extractAudioFrame(): DecodedFrame?

    /**
     * Removes and retrieves a video frame from the buffer.
     */
    suspend fun extractVideoFrame(): DecodedFrame?

    /**
     * Starts buffering frames into the audio and video buffers and emits timestamps of buffered frames.
     *
     * @return A flow emitting timestamps of buffered frames.
     */
    suspend fun startBuffering(): Flow<Long>

    /**
     * Clears both audio and video buffers.
     */
    suspend fun flush()

    /**
     * Companion object containing default values and a factory method to create a [BufferManager] instance.
     */
    companion object {
        private const val DEFAULT_AUDIO_FRAME_RATE = 43.0
        private const val DEFAULT_BUFFER_DURATION_MILLIS = 1_000L

        /**
         * Creates a [BufferManager] instance using the provided [Decoder].
         *
         * @param decoder The [Decoder] providing frames for buffering.
         * @return A new instance of [BufferManager].
         */
        fun create(decoder: Decoder): BufferManager = Implementation(decoder)
    }

    private class Implementation(private val decoder: Decoder) : BufferManager {

        private val bufferMutex = Mutex()

        override var bufferDurationMillis = DEFAULT_BUFFER_DURATION_MILLIS
            private set

        override fun changeDuration(durationMillis: Long?) {
            bufferDurationMillis = durationMillis ?: DEFAULT_BUFFER_DURATION_MILLIS
        }

        override fun audioBufferCapacity() =
            (DEFAULT_AUDIO_FRAME_RATE.takeIf { decoder.media?.audioFormat != null } ?: 0.0)
                .times(bufferDurationMillis)
                .div(1_000L)
                .let(::ceil)
                .toInt()

        override fun videoBufferCapacity() =
            (decoder.media?.frameRate ?: 0.0)
                .times(bufferDurationMillis)
                .div(1_000L)
                .let(::ceil)
                .toInt()

        private val audioBuffer = LinkedList<DecodedFrame>()

        private val videoBuffer = LinkedList<DecodedFrame>()

        override suspend fun audioBufferSize() = bufferMutex.withLock { audioBuffer.size }

        override suspend fun videoBufferSize() = bufferMutex.withLock { videoBuffer.size }

        override suspend fun bufferIsEmpty() = bufferMutex.withLock {
            (audioBufferCapacity() > 0 && audioBuffer.isEmpty()) && (videoBufferCapacity() > 0 && videoBuffer.isEmpty())
        }

        override suspend fun bufferIsFull() = bufferMutex.withLock {
            (audioBufferCapacity() > 0 && audioBuffer.size >= audioBufferCapacity()) || (videoBufferCapacity() > 0 && videoBuffer.size >= videoBufferCapacity())
        }

        override suspend fun firstAudioFrame(): DecodedFrame? = bufferMutex.withLock { audioBuffer.peek() }

        override suspend fun firstVideoFrame(): DecodedFrame? = bufferMutex.withLock { videoBuffer.peek() }

        override suspend fun extractAudioFrame(): DecodedFrame? = bufferMutex.withLock { audioBuffer.poll() }

        override suspend fun extractVideoFrame(): DecodedFrame? = bufferMutex.withLock { videoBuffer.poll() }

        override suspend fun startBuffering() = flow {
            while (currentCoroutineContext().isActive) {
                if (audioBuffer.size < audioBufferCapacity() || videoBuffer.size < videoBufferCapacity()) {
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