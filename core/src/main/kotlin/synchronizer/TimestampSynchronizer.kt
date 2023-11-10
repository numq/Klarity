package synchronizer

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.nanoseconds

class TimestampSynchronizer {

    private val mutex = Mutex()

    private var audioTimestampNanos: Long? = null

    private var previousVideoTimestampNanos: Long? = null

    private var videoTimestampNanos: Long? = null

    suspend fun updateAudioTimestamp(nanos: Long) = mutex.withLock {
        audioTimestampNanos = nanos
    }

    suspend fun updateVideoTimestamp(nanos: Long) = mutex.withLock {
        previousVideoTimestampNanos = videoTimestampNanos
        videoTimestampNanos = nanos
    }

    suspend fun syncWithAudio() {
        videoTimestampNanos?.let { currentTimestampNanos ->
            audioTimestampNanos?.let { previousTimestampNanos ->
                currentTimestampNanos - previousTimestampNanos
            }?.nanoseconds?.takeIf { it.isPositive() }?.let { delay(it) }
        }
    }

    suspend fun syncWithVideo() {
        videoTimestampNanos?.let { currentTimestampNanos ->
            previousVideoTimestampNanos?.let { previousTimestampNanos ->
                currentTimestampNanos - previousTimestampNanos
            }?.nanoseconds?.takeIf { it.isPositive() }?.let { delay(it.inWholeMilliseconds) }
        }
    }
}