package buffer

import frame.DecodedFrame
import media.Media

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
     * Retrieves the first audio frame from the audio buffer without removing it.
     */
    fun firstAudioFrame(): DecodedFrame.Audio?

    /**
     * Retrieves the first video frame from the video buffer without removing it.
     */
    fun firstVideoFrame(): DecodedFrame.Video?

    /**
     * Removes and retrieves an audio frame from the audio buffer.
     */
    fun extractAudioFrame(): DecodedFrame.Audio?

    /**
     * Removes and retrieves a video frame from the video buffer.
     */
    fun extractVideoFrame(): DecodedFrame.Video?

    /**
     * Adds an audio frame to the end of audio buffer.
     */
    fun insertAudioFrame(frame: DecodedFrame.Audio)

    /**
     * Adds a video frame to the end of video buffer.
     */
    fun insertVideoFrame(frame: DecodedFrame.Video)

    /**
     * Clears both audio and video buffers.
     */
    fun flush()

    /**
     * Companion object containing default values and a factory method to create a [BufferManager] instance.
     */
    companion object {
        /**
         * Creates a [BufferManager] instance.
         *
         * @return A [BufferManager] instance for [media].
         */
        fun create(media: Media): BufferManager = DefaultBufferManager(media)
    }
}