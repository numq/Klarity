package synchronizer

/**
 * Interface representing a synchronizer for managing the synchronization of video frame timestamps.
 */
interface PlaybackSynchronizer {
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

    /**
     * Updates the audio timestamp to the specified value.
     *
     * @param nanos The last audio timestamp in nanoseconds.
     */
    suspend fun updateLastAudioTimestamp(nanos: Long)

    /**
     * Updates the video timestamp to the specified value.
     *
     * @param nanos The last video timestamp in nanoseconds.
     */
    suspend fun updateLastVideoTimestamp(nanos: Long)

    /**
     * Synchronizes the current video timestamp with the last audio or video timestamp,
     * introducing a delay if necessary for synchronization.
     *
     * @param nanos The current video timestamp in nanoseconds.
     */
    suspend fun synchronize(nanos: Long)

    /**
     * Resets synchronization timestamps.
     */
    fun reset()
}