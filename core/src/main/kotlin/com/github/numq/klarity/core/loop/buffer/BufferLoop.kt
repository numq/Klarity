package com.github.numq.klarity.core.loop.buffer

import com.github.numq.klarity.core.pipeline.Pipeline
import com.github.numq.klarity.core.timestamp.Timestamp

interface BufferLoop {
    val isBuffering: Boolean

    suspend fun start(
        onException: suspend (BufferLoopException) -> Unit,
        onTimestamp: suspend (Timestamp) -> Unit,
        onEndOfMedia: suspend () -> Unit
    ): Result<Unit>

    suspend fun stop(): Result<Unit>

    suspend fun close(): Result<Unit>

    companion object {
        internal fun create(
            pipeline: Pipeline,
        ): Result<BufferLoop> = runCatching {
            DefaultBufferLoop(pipeline = pipeline)
        }
    }
}