package buffer

import decoder.Decoder
import frame.DecodedFrame
import kotlinx.coroutines.flow.Flow

/**
 * Interface defining the contract for managing audio and video buffers.
 */
interface BufferManager {
    /**
     * Changes the capacity of the audio buffer.
     *
     * @param value New capacity.
     */
    fun changeAudioBufferCapacity(value: Int)

    /**
     * Changes the capacity of the video buffer.
     *
     * @param value New capacity.
     */
    fun changeVideoBufferCapacity(value: Int)

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
        /**
         * Creates a [BufferManager] instance using the provided [Decoder].
         *
         * @param decoder The [Decoder] providing frames for buffering.
         * @return A new instance of [BufferManager].
         */
        fun create(decoder: Decoder): BufferManager = DefaultBufferManager(decoder)
    }
}