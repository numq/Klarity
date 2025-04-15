package com.github.numq.klarity.compose.renderer

import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Surface
import java.io.Closeable

interface RendererContext : Closeable {
    val generationId: StateFlow<Int>

    fun withSurface(callback: (Surface) -> Unit)

    fun draw(pixels: ByteArray)

    fun reset()

    companion object {
        fun create(foreground: Foreground): RendererContext {
            require(foreground !is Foreground.Empty) { "Could not create empty renderer context" }

            val imageInfo = ImageInfo(
                width = foreground.width,
                height = foreground.height,
                colorType = foreground.colorType,
                alphaType = foreground.alphaType
            )

            val bitmap = Bitmap()

            if (!bitmap.allocPixels(imageInfo)) {
                bitmap.close()

                error("Could not allocate bitmap pixels")
            }

            val surface = Surface.makeRaster(bitmap.imageInfo)

            return SkiaRendererContext(imageInfo = imageInfo, bitmap = bitmap, surface = surface)
        }
    }
}