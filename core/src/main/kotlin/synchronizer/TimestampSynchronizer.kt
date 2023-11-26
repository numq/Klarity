package synchronizer

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Interface representing a synchronizer for managing the synchronization of video frame timestamps.
 */
interface TimestampSynchronizer {

    /**
     * Updates the audio timestamp to the specified value.
     * @param nanos The new audio timestamp in nanoseconds.
     */
    suspend fun updateAudioTimestamp(nanos: Long)

    /**
     * Updates the video timestamp to the specified value.
     * @param nanos The new video timestamp in nanoseconds.
     */
    suspend fun updateVideoTimestamp(nanos: Long)

    /**
     * Synchronizes the current video timestamp with the current audio timestamp,
     * introducing a delay if necessary for synchronization.
     */
    suspend fun syncWithAudio()

    /**
     * Synchronizes the current video timestamp with the last processed video timestamp,
     * introducing a delay if necessary for synchronization.
     */
    suspend fun syncWithVideo()

    /**
     * Companion object providing a factory method to create a [TimestampSynchronizer] instance.
     */
    companion object {
        /**
         * Creates a [TimestampSynchronizer] instance.
         * @return A [TimestampSynchronizer] instance.
         */
        fun create(): TimestampSynchronizer = Implementation()
    }

    class Implementation : TimestampSynchronizer {

        private val mutex = Mutex()

        private var audioTimestampNanos: Long? = null

        private var previousVideoTimestampNanos: Long? = null

        private var videoTimestampNanos: Long? = null

        override suspend fun updateAudioTimestamp(nanos: Long) = mutex.withLock {
            audioTimestampNanos = nanos
        }

        override suspend fun updateVideoTimestamp(nanos: Long) = mutex.withLock {
            previousVideoTimestampNanos = videoTimestampNanos
            videoTimestampNanos = nanos
        }

        override suspend fun syncWithAudio() {
            videoTimestampNanos?.let { currentTimestampNanos ->
                audioTimestampNanos?.let { previousTimestampNanos ->
                    currentTimestampNanos - previousTimestampNanos
                }?.nanoseconds?.takeIf { it.isPositive() }?.let { delay(it) }
            }
        }

        override suspend fun syncWithVideo() {
            videoTimestampNanos?.let { currentTimestampNanos ->
                previousVideoTimestampNanos?.let { previousTimestampNanos ->
                    currentTimestampNanos - previousTimestampNanos
                }?.nanoseconds?.takeIf { it.isPositive() }?.let { delay(it.inWholeMilliseconds) }
            }
        }
    }
}