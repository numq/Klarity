package renderer

import factory.Factory
import frame.Frame

object RendererFactory : Factory<RendererFactory.Parameters, Renderer> {
    data class Parameters(val width: Int, val height: Int, val frameRate: Double, val preview: Frame.Video.Content?)

    override fun create(parameters: Parameters) = with(parameters) {
        Renderer.create(width = width, height = height, frameRate = frameRate, preview = preview)
    }
}