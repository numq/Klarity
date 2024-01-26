package synchronizer

import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

internal class DefaultPlaybackSynchronizer : PlaybackSynchronizer {

    private var lastAudioTimestampNanos: Long? = null

    private var lastVideoTimestampNanos: Long? = null

    private var currentTimestampNanos: Long? = null

    override suspend fun updateLastAudioTimestamp(nanos: Long) {
        lastAudioTimestampNanos = nanos
    }

    override suspend fun updateLastVideoTimestamp(nanos: Long) {
        lastVideoTimestampNanos = nanos
    }

    override suspend fun synchronize(nanos: Long) {
        (lastAudioTimestampNanos ?: lastVideoTimestampNanos)?.let { lastTimestampNanos ->
            (nanos - lastTimestampNanos).nanoseconds.takeIf(Duration::isPositive)?.let { duration -> delay(duration) }
        }
    }

    override fun reset() {
        lastAudioTimestampNanos = null
        lastVideoTimestampNanos = null
        currentTimestampNanos = null
    }
}