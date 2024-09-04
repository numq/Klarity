package loop.playback

import factory.Factory
import loop.buffer.BufferLoop
import pipeline.Pipeline

class PlaybackLoopFactory : Factory<PlaybackLoopFactory.Parameters, PlaybackLoop> {
    data class Parameters(val bufferLoop: BufferLoop, val pipeline: Pipeline)

    override fun create(parameters: Parameters): Result<PlaybackLoop> = with(parameters) {
        PlaybackLoop.create(bufferLoop = bufferLoop, pipeline = pipeline)
    }
}