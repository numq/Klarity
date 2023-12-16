package synchronizer

import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

internal class DefaultTimestampSynchronizer : TimestampSynchronizer {

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