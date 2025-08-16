package io.github.numq.klarity.loop.playback

import io.github.numq.klarity.factory.Factory
import io.github.numq.klarity.pipeline.Pipeline
import io.github.numq.klarity.renderer.Renderer
import kotlin.time.Duration

internal class PlaybackLoopFactory : Factory<PlaybackLoopFactory.Parameters, PlaybackLoop> {
    data class Parameters(
        val pipeline: Pipeline,
        val syncThreshold: Duration,
        val getVolume: () -> Float,
        val getPlaybackSpeedFactor: () -> Float,
        val getRenderer: suspend () -> Renderer?,
    )

    override fun create(parameters: Parameters): Result<PlaybackLoop> = with(parameters) {
        PlaybackLoop.create(
            pipeline = pipeline,
            syncThreshold = syncThreshold,
            getVolume = getVolume,
            getPlaybackSpeedFactor = getPlaybackSpeedFactor,
            getRenderer = getRenderer
        )
    }
}