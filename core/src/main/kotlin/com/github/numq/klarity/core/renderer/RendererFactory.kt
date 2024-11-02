package com.github.numq.klarity.core.renderer

import com.github.numq.klarity.core.factory.Factory
import com.github.numq.klarity.core.frame.Frame

class RendererFactory : Factory<RendererFactory.Parameters, Renderer> {
    data class Parameters(val width: Int, val height: Int, val frameRate: Double, val preview: Frame.Video.Content?)

    override fun create(parameters: Parameters) = with(parameters) {
        Renderer.create(width = width, height = height, frameRate = frameRate, preview = preview)
    }
}