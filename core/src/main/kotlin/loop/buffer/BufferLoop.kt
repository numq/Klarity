package loop.buffer

import kotlinx.coroutines.flow.StateFlow
import pipeline.Pipeline
import java.util.concurrent.atomic.AtomicBoolean

interface BufferLoop {
    val isBuffering: AtomicBoolean
    val isWaiting: AtomicBoolean
    val timestamp: StateFlow<Long>
    suspend fun start(onWaiting: suspend () -> Unit, endOfMedia: suspend () -> Unit): Result<Unit>
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