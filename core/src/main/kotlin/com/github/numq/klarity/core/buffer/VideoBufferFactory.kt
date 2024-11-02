package com.github.numq.klarity.core.buffer

import com.github.numq.klarity.core.factory.Factory
import com.github.numq.klarity.core.frame.Frame

class VideoBufferFactory : Factory<VideoBufferFactory.Parameters, Buffer<Frame.Video>> {
    data class Parameters(val capacity: Int)

    override fun create(parameters: Parameters) = Buffer.create<Frame.Video>(parameters.capacity)
}