package com.github.numq.klarity.core.loop.playback

import com.github.numq.klarity.core.factory.Factory
import com.github.numq.klarity.core.pipeline.Pipeline
import com.github.numq.klarity.core.renderer.Renderer

class PlaybackLoopFactory : Factory<PlaybackLoopFactory.Parameters, PlaybackLoop> {
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