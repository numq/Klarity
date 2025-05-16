package io.github.numq.klarity.buffer

import io.github.numq.klarity.factory.Factory
import io.github.numq.klarity.frame.Frame

class BufferFactory : Factory<BufferFactory.Parameters, Buffer<Frame>> {
    data class Parameters(val capacity: Int)

    override fun create(parameters: Parameters) = Buffer.create<Frame>(parameters.capacity)
}