package loop.buffer

import kotlinx.coroutines.flow.StateFlow
import pipeline.Pipeline
import timestamp.Timestamp
import java.util.concurrent.atomic.AtomicBoolean

interface BufferLoop {
    val isBuffering: AtomicBoolean
    val isWaiting: AtomicBoolean
    val timestamp: StateFlow<Timestamp>
    suspend fun start(onWaiting: suspend () -> Unit, endOfMedia: suspend () -> Unit): Result<Unit>
    suspend fun stop(resetTime: Boolean): Result<Unit>
    fun close()

    companion object {
        internal fun create(
            pipeline: Pipeline,
        ): Result<BufferLoop> = runCatching {
            DefaultBufferLoop(pipeline = pipeline)
        }
    }
}