package com.github.numq.klarity.core.loop.playback

import com.github.numq.klarity.core.pipeline.Pipeline
import com.github.numq.klarity.core.renderer.Renderer
import com.github.numq.klarity.core.timestamp.Timestamp

interface PlaybackLoop {
    suspend fun start(
        onException: suspend (PlaybackLoopException) -> Unit,
        onTimestamp: suspend (Timestamp) -> Unit,
        endOfMedia: suspend () -> Unit,
    ): Result<Unit>

    suspend fun stop(): Result<Unit>

    suspend fun close(): Result<Unit>

    companion object {
        internal fun create(
            pipeline: Pipeline,
            getPlaybackSpeedFactor: () -> Double,
            getRenderer: () -> Renderer?
        ): Result<PlaybackLoop> = runCatching {
            DefaultPlaybackLoop(
                pipeline = pipeline, getPlaybackSpeedFactor = getPlaybackSpeedFactor, getRenderer = getRenderer
            )
        }
    }
}