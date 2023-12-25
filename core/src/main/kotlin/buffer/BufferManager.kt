package buffer

import decoder.Decoder
import frame.DecodedFrame
import kotlinx.coroutines.flow.Flow

/**
 * Interface defining the contract for managing audio and video buffers.
 */
interface BufferManager {

    /**
     * Gets the duration of the buffer in milliseconds.
     */
    val bufferDurationMillis: Long

    /**
     * Changes the capacity of the buffer, represented as a duration. If [durationMillis] is non-null, the buffer duration is set to
     * the provided value; otherwise, it is set to the default value [DEFAULT_BUFFER_DURATION_MILLIS].
     * If the specified duration is equal to or less than zero, the size of each buffer will be one frame,
     * since playback is not possible with an empty buffer.
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
    fun audioBufferSize(): Int

    /**
     * Retrieves the current size of the video buffer.
     */
    fun videoBufferSize(): Int

    /**
     * Checks if both audio and video buffers are empty.
     */
    fun bufferIsEmpty(): Boolean

    /**
     * Checks whether either the audio or video buffer is full.
     */
    fun bufferIsFull(): Boolean

    /**
     * Retrieves the first audio frame from the buffer without removing it.
     */
    fun firstAudioFrame(): DecodedFrame?

    /**
     * Retrieves the first video frame from the buffer without removing it.
     */
    fun firstVideoFrame(): DecodedFrame?

    /**
     * Removes and retrieves an audio frame from the buffer.
     */
    fun extractAudioFrame(): DecodedFrame?

    /**
     * Removes and retrieves a video frame from the buffer.
     */
    fun extractVideoFrame(): DecodedFrame?

    /**
     * Starts buffering frames into the audio and video buffers and emits timestamps of buffered frames.
     *
     * @return A flow emitting timestamps of buffered frames.
     */
    fun startBuffering(): Flow<Long>

    /**
     * Clears both audio and video buffers.
     */
    fun flush()

    /**
     * Companion object containing default values and a factory method to create a [BufferManager] instance.
     */
    companion object {
        const val DEFAULT_AUDIO_FRAME_RATE = 43.0
        const val DEFAULT_BUFFER_DURATION_MILLIS = 1_000L

        /**
         * Creates a [BufferManager] instance using the provided [Decoder].
         *
         * @param decoder The [Decoder] providing frames for buffering.
         * @return A new instance of [BufferManager].
         */
        fun create(decoder: Decoder): BufferManager = DefaultBufferManager(decoder)
    }
}