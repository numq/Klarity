package synchronizer

import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Interface representing a synchronizer for managing the synchronization of video frame timestamps.
 */
interface TimestampSynchronizer {

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
     */
    suspend fun syncWithAudio(videoFrameRate: Double)

    /**
     * Synchronizes the current video timestamp with the last processed video timestamp,
     * introducing a delay if necessary for synchronization.
     *
     * @param videoFrameRate Frame rate of the video.
     */
    suspend fun syncWithVideo(videoFrameRate: Double)

    /**
     * Resets synchronization timestamps.
     */
    fun reset()

    /**
     * Companion object providing a factory method to create a [TimestampSynchronizer] instance.
     */
    companion object {
        /**
         * Creates a [TimestampSynchronizer] instance.
         *
         * @return A [TimestampSynchronizer] instance.
         */
        fun create(): TimestampSynchronizer = Implementation()
    }

    private class Implementation : TimestampSynchronizer {

        private var audioTimestampNanos: Long? = null

        private var previousVideoTimestampNanos: Long? = null

        private var videoTimestampNanos: Long? = null

        private suspend fun syncWithTimestamp(currentNanos: Long?, previousNanos: Long?, videoFrameRate: Double) {
            currentNanos?.let { current ->
                previousNanos?.let { previous ->
                    current - previous
                }?.nanoseconds?.let { duration ->
                    delay(duration.coerceIn(Duration.ZERO, (1_000_000_000 / videoFrameRate).nanoseconds))
                }
            }
        }

        override suspend fun updateAudioTimestamp(nanos: Long) {
            audioTimestampNanos = nanos
        }

        override suspend fun updateVideoTimestamp(nanos: Long) {
            previousVideoTimestampNanos = videoTimestampNanos
            videoTimestampNanos = nanos
        }

        override suspend fun syncWithAudio(videoFrameRate: Double) =
            syncWithTimestamp(videoTimestampNanos, audioTimestampNanos, videoFrameRate)

        override suspend fun syncWithVideo(videoFrameRate: Double) =
            syncWithTimestamp(videoTimestampNanos, previousVideoTimestampNanos, videoFrameRate)

        override fun reset() {
            audioTimestampNanos = null
            previousVideoTimestampNanos = null
            videoTimestampNanos = null
        }
    }
}