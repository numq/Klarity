package buffer

import factory.Factory
import frame.Frame

class AudioBufferFactory : Factory<AudioBufferFactory.Parameters, Buffer<Frame.Audio>> {
    data class Parameters(val capacity: Int)

    override fun create(parameters: Parameters) = Buffer.create<Frame.Audio>(parameters.capacity)
}