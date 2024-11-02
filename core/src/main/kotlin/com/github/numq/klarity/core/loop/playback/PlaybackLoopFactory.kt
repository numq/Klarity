package com.github.numq.klarity.core.loop.playback

import com.github.numq.klarity.core.factory.Factory
import com.github.numq.klarity.core.loop.buffer.BufferLoop
import com.github.numq.klarity.core.pipeline.Pipeline

class PlaybackLoopFactory : Factory<PlaybackLoopFactory.Parameters, PlaybackLoop> {
    data class Parameters(val bufferLoop: BufferLoop, val pipeline: Pipeline)

    override fun create(parameters: Parameters): Result<PlaybackLoop> = with(parameters) {
        PlaybackLoop.create(bufferLoop = bufferLoop, pipeline = pipeline)
    }
}