package io.github.numq.klarity.loop.playback

import io.github.numq.klarity.factory.Factory
import io.github.numq.klarity.pipeline.Pipeline
import io.github.numq.klarity.renderer.Renderer

internal class PlaybackLoopFactory : Factory<PlaybackLoopFactory.Parameters, PlaybackLoop> {
    data class Parameters(
        val pipeline: Pipeline,
        val getPlaybackSpeedFactor: () -> Double,
        val getRenderer: () -> Renderer?
    )

    override fun create(parameters: Parameters): Result<PlaybackLoop> = with(parameters) {
        PlaybackLoop.create(
            pipeline = pipeline,
            getPlaybackSpeedFactor = getPlaybackSpeedFactor,
            getRenderer = getRenderer
        )
    }
}