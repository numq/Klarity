package io.github.numq.klarity.loop.buffer

import io.github.numq.klarity.pipeline.Pipeline
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Duration

interface BufferLoop {
    val isBuffering: Boolean

    suspend fun start(
        coroutineScope: CoroutineScope,
        onException: suspend (BufferLoopException) -> Unit,
        onTimestamp: suspend (Duration) -> Unit,
        onEndOfMedia: suspend () -> Unit
    ): Result<Unit>

    suspend fun stop(): Result<Unit>

    suspend fun close(): Result<Unit>

    companion object {
        internal fun create(pipeline: Pipeline): Result<BufferLoop> = runCatching {
            DefaultBufferLoop(pipeline = pipeline)
        }
    }
}