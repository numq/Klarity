package com.github.numq.klarity.core.loop.buffer

import com.github.numq.klarity.core.closeable.SuspendAutoCloseable
import com.github.numq.klarity.core.pipeline.Pipeline
import com.github.numq.klarity.core.timestamp.Timestamp
import java.util.concurrent.atomic.AtomicBoolean

interface BufferLoop : SuspendAutoCloseable {
    val isBuffering: AtomicBoolean
    val isWaiting: AtomicBoolean
    suspend fun start(
        onTimestamp: suspend (Timestamp) -> Unit,
        onWaiting: suspend () -> Unit,
        endOfMedia: suspend () -> Unit,
    ): Result<Unit>

    suspend fun stop(): Result<Unit>

    companion object {
        internal fun create(
            pipeline: Pipeline,
        ): Result<BufferLoop> = runCatching {
            DefaultBufferLoop(pipeline = pipeline)
        }
    }
}