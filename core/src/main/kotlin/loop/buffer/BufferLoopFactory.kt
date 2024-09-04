package loop.buffer

import factory.Factory
import pipeline.Pipeline

class BufferLoopFactory : Factory<BufferLoopFactory.Parameters, BufferLoop> {
    data class Parameters(val pipeline: Pipeline)

    override fun create(parameters: Parameters) = with(parameters) {
        BufferLoop.create(pipeline = pipeline)
    }
}