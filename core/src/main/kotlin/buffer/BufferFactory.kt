package buffer

import factory.Factory
import frame.Frame

object BufferFactory : Factory<BufferFactory.Parameters, Buffer<*>> {
    sealed interface Parameters {
        val capacity: Int

        data class Audio(override val capacity: Int) : Parameters

        data class Video(override val capacity: Int) : Parameters
    }

    override fun create(parameters: Parameters) = with(parameters) {
        when (this) {
            is Parameters.Audio -> Buffer.create<Frame.Audio>(capacity)

            is Parameters.Video -> Buffer.create<Frame.Video>(capacity)
        }
    }
}