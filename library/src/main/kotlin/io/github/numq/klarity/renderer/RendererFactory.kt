package io.github.numq.klarity.renderer

import io.github.numq.klarity.factory.Factory
import io.github.numq.klarity.format.VideoFormat

internal class RendererFactory : Factory<RendererFactory.Parameters, Renderer> {
    data class Parameters(val format: VideoFormat)

    override fun create(parameters: Parameters) = Renderer.create(format = parameters.format)
}