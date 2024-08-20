package loop.buffer

import pipeline.Pipeline
import timestamp.Timestamp
import java.util.concurrent.atomic.AtomicBoolean

interface BufferLoop {
    val isBuffering: AtomicBoolean
    val isWaiting: AtomicBoolean
    suspend fun start(
        onTimestamp: suspend (Timestamp) -> Unit,
        onWaiting: suspend () -> Unit,
        endOfMedia: suspend () -> Unit,
    ): Result<Unit>

    suspend fun stop(): Result<Unit>
    fun close()

    companion object {
        internal fun create(
            pipeline: Pipeline,
        ): Result<BufferLoop> = runCatching {
            DefaultBufferLoop(pipeline = pipeline)
        }
    }
}