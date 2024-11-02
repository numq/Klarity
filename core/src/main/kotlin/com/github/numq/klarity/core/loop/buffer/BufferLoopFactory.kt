package com.github.numq.klarity.core.loop.buffer

import com.github.numq.klarity.core.factory.Factory
import com.github.numq.klarity.core.pipeline.Pipeline

class BufferLoopFactory : Factory<BufferLoopFactory.Parameters, BufferLoop> {
    data class Parameters(val pipeline: Pipeline)

    override fun create(parameters: Parameters) = with(parameters) {
        BufferLoop.create(pipeline = pipeline)
    }
}