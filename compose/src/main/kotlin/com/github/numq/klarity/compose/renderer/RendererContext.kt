package com.github.numq.klarity.compose.renderer

import com.github.numq.klarity.core.renderer.Renderer
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Surface
import java.io.Closeable

internal interface RendererContext : Closeable {
    val generationId: StateFlow<Int>

    fun withSurface(callback: (Surface) -> Unit)

    companion object {
        fun create(renderer: Renderer): RendererContext {
            val imageInfo = ImageInfo(
                width = renderer.format.width,
                height = renderer.format.height,
                colorType = ColorType.RGBA_8888,
                alphaType = ColorAlphaType.UNPREMUL
            )

            val surface = Surface.makeRaster(imageInfo)

            return SkiaRendererContext(renderer = renderer, surface = surface)
        }
    }
}