package com.github.numq.klarity.compose.renderer

import com.github.numq.klarity.core.renderer.Renderer
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.skia.*
import java.io.Closeable

internal interface RendererContext : Closeable {
    val generationId: StateFlow<Int>

    fun withSurface(callback: (Surface) -> Unit)

    fun draw(pixels: ByteArray)

    companion object {
        fun create(renderer: Renderer): RendererContext {
            val imageInfo = ImageInfo(
                width = renderer.format.width,
                height = renderer.format.height,
                colorType = ColorType.RGBA_8888,
                alphaType = ColorAlphaType.UNPREMUL
            )

            val bitmap = Bitmap()

            if (!bitmap.allocPixels(imageInfo)) {
                bitmap.close()

                error("Could not allocate bitmap pixels")
            }

            val surface = Surface.makeRaster(bitmap.imageInfo)

            return SkiaRendererContext(
                renderer = renderer,
                imageInfo = imageInfo,
                bitmap = bitmap,
                surface = surface
            )
        }
    }
}