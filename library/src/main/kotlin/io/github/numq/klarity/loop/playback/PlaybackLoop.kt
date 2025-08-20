package io.github.numq.klarity.loop.playback

import io.github.numq.klarity.media.Media
import io.github.numq.klarity.pipeline.Pipeline
import io.github.numq.klarity.renderer.Renderer
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
            media: Media,
            pipeline: Pipeline,
            syncThreshold: Duration,
            getVolume: () -> Float,
            getPlaybackSpeedFactor: () -> Float,
            getRenderer: () -> Renderer?
        ): Result<PlaybackLoop> = runCatching {
            DefaultPlaybackLoop(
                media = media,
                pipeline = pipeline,
                syncThreshold = syncThreshold,
                getVolume = getVolume,
                getPlaybackSpeedFactor = getPlaybackSpeedFactor,
                getRenderer = getRenderer
            )
        }
    }
}