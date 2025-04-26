package com.github.numq.klarity.core.buffer

import com.github.numq.klarity.core.factory.Factory
import com.github.numq.klarity.core.frame.Frame

class BufferFactory : Factory<BufferFactory.Parameters, Buffer<Frame>> {
    data class Parameters(val capacity: Int)

    override fun create(parameters: Parameters) = Buffer.create<Frame>(parameters.capacity)
}