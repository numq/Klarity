package io.github.numq.klarity.loop.playback

import io.github.numq.klarity.factory.Factory
import io.github.numq.klarity.pipeline.Pipeline

internal class PlaybackLoopFactory : Factory<PlaybackLoopFactory.Parameters, PlaybackLoop> {
    data class Parameters(
        val pipeline: Pipeline,
        val getVolume: () -> Float,
        val getPlaybackSpeedFactor: () -> Float
    )

    override fun create(parameters: Parameters): Result<PlaybackLoop> = with(parameters) {
        PlaybackLoop.create(
            pipeline = pipeline,
            getVolume = getVolume,
            getPlaybackSpeedFactor = getPlaybackSpeedFactor
        )
    }
}