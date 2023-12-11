package synchronizer

import kotlinx.coroutines.delay
import kotlin.math.ceil
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

        override suspend fun updateAudioTimestamp(nanos: Long) {
            audioTimestampNanos = nanos
        }

        override suspend fun updateVideoTimestamp(nanos: Long) {
            previousVideoTimestampNanos = videoTimestampNanos
            videoTimestampNanos = nanos
        }

        override suspend fun syncWithAudio(videoFrameRate: Double) {
            videoTimestampNanos?.let { currentTimestampNanos ->
                audioTimestampNanos?.let { previousTimestampNanos ->
                    currentTimestampNanos - previousTimestampNanos
                }?.nanoseconds
                    ?.takeIf { it.isPositive() }
                    ?.let { delay(it.inWholeMilliseconds.coerceAtMost(ceil(1_000 / videoFrameRate).toLong())) }
            }
        }

        override suspend fun syncWithVideo(videoFrameRate: Double) {
            videoTimestampNanos?.let { currentTimestampNanos ->
                previousVideoTimestampNanos?.let { previousTimestampNanos ->
                    currentTimestampNanos - previousTimestampNanos
                }?.nanoseconds
                    ?.takeIf { it.isPositive() }
                    ?.let { delay(it.inWholeMilliseconds.coerceAtMost(ceil(1_000 / videoFrameRate).toLong())) }
            }
        }
    }
}