package com.github.numq.klarity.core.buffer

import com.github.numq.klarity.core.factory.Factory
import com.github.numq.klarity.core.frame.Frame

class AudioBufferFactory : Factory<AudioBufferFactory.Parameters, Buffer<Frame.Audio>> {
    data class Parameters(val capacity: Int)

    override fun create(parameters: Parameters) = Buffer.create<Frame.Audio>(parameters.capacity)
}