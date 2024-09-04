package buffer

import factory.Factory
import frame.Frame

class VideoBufferFactory : Factory<VideoBufferFactory.Parameters, Buffer<Frame.Video>> {
    data class Parameters(val capacity: Int)

    override fun create(parameters: Parameters) = Buffer.create<Frame.Video>(parameters.capacity)
}