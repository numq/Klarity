package loop.playback

import clock.Clock
import factory.Factory
import loop.buffer.BufferLoop
import pipeline.Pipeline

object PlaybackLoopFactory : Factory<PlaybackLoopFactory.Parameters, PlaybackLoop> {
    data class Parameters(val clock: Clock, val bufferLoop: BufferLoop, val pipeline: Pipeline)

    override fun create(parameters: Parameters): Result<PlaybackLoop> = with(parameters) {
        PlaybackLoop.create(clock = clock, bufferLoop = bufferLoop, pipeline = pipeline)
    }
}