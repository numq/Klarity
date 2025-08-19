package io.github.numq.klarity.renderer

import io.github.numq.klarity.factory.Factory

internal class RendererFactory : Factory<RendererFactory.Parameters, Renderer> {
    data class Parameters(val width: Int, val height: Int)

    override fun create(parameters: Parameters): Result<Renderer> = with(parameters) {
        Renderer.create(width = width, height = height)
    }
}