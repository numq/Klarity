package io.github.numq.klarity.loop.playback

import io.github.numq.klarity.pipeline.Pipeline
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Duration

internal interface PlaybackLoop {
    suspend fun start(
        coroutineScope: CoroutineScope,
        onException: suspend (PlaybackLoopException) -> Unit,
        onTimestamp: suspend (Duration) -> Unit,
        onEndOfMedia: suspend () -> Unit,
    ): Result<Unit>

    suspend fun stop(): Result<Unit>

    suspend fun close(): Result<Unit>

    companion object {
        fun create(
            pipeline: Pipeline,
            getVolume: () -> Float,
            getPlaybackSpeedFactor: () -> Float
        ): Result<PlaybackLoop> = runCatching {
            DefaultPlaybackLoop(
                pipeline = pipeline,
                getVolume = getVolume,
                getPlaybackSpeedFactor = getPlaybackSpeedFactor
            )
        }
    }
}