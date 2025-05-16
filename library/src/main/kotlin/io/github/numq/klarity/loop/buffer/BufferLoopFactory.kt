package io.github.numq.klarity.loop.buffer

import io.github.numq.klarity.factory.Factory
import io.github.numq.klarity.pipeline.Pipeline

internal class BufferLoopFactory : Factory<BufferLoopFactory.Parameters, BufferLoop> {
    data class Parameters(val pipeline: Pipeline)

    override fun create(parameters: Parameters) = with(parameters) {
        BufferLoop.create(pipeline = pipeline)
    }
}