package com.github.numq.klarity.core.renderer

import com.github.numq.klarity.core.factory.Factory
import com.github.numq.klarity.core.format.VideoFormat
import com.github.numq.klarity.core.frame.Frame

class RendererFactory : Factory<RendererFactory.Parameters, Renderer> {
    data class Parameters(val format: VideoFormat, val preview: Frame.Video.Content?)

    override fun create(parameters: Parameters) = with(parameters) {
        Renderer.create(format = format, preview = preview)
    }
}