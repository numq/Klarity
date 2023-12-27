package playback

/**
 * Interface representing a synchronizer for managing the synchronization of video frame timestamps.
 */
interface PlaybackSynchronizer {

    /**
     * Updates the audio timestamp to the specified value.
     *
     * @param nanos The new audio timestamp in nanoseconds.
     */
    suspend fun updateAudioTimestamp(nanos: Long)

    /**
     * Updates the video timestamp to the specified value.
     *
     * @param nanos The new video timestamp in nanoseconds.
     */
    suspend fun updateVideoTimestamp(nanos: Long)

    /**
     * Synchronizes the current video timestamp with the current audio timestamp,
     * introducing a delay if necessary for synchronization.
     *
     * @param videoFrameRate Frame rate of the video.
     * @return Delay nanos.
     */
    suspend fun syncWithAudio(videoFrameRate: Double): Long

    /**
     * Synchronizes the current video timestamp with the last processed video timestamp,
     * introducing a delay if necessary for synchronization.
     *
     * @param videoFrameRate Frame rate of the video.
     * @return Delay nanos.
     */
    suspend fun syncWithVideo(videoFrameRate: Double): Long

    /**
     * Resets synchronization timestamps.
     */
    fun reset()

    /**
     * Companion object providing a factory method to create a [PlaybackSynchronizer] instance.
     */
    companion object {
        /**
         * Creates a [PlaybackSynchronizer] instance.
         *
         * @return A [PlaybackSynchronizer] instance.
         */
        fun create(): PlaybackSynchronizer = DefaultPlaybackSynchronizer()
    }
}