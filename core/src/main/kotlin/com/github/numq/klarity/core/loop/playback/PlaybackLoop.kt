package com.github.numq.klarity.core.loop.playback

import com.github.numq.klarity.core.loop.buffer.BufferLoop
import com.github.numq.klarity.core.pipeline.Pipeline
import com.github.numq.klarity.core.timestamp.Timestamp

interface PlaybackLoop {
    suspend fun start(
        onTimestamp: suspend (Timestamp) -> Unit,
        endOfMedia: suspend () -> Unit,
    ): Result<Unit>

    suspend fun stop(): Result<Unit>

    suspend fun close(): Result<Unit>

    companion object {
        internal fun create(bufferLoop: BufferLoop, pipeline: Pipeline): Result<PlaybackLoop> = runCatching {
            DefaultPlaybackLoop(bufferLoop = bufferLoop, pipeline = pipeline)
        }
    }
}