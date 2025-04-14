package com.github.numq.klarity.compose.renderer

import org.jetbrains.skia.*
import java.io.Closeable

interface RendererContext : Closeable {
    val surface: Surface

    fun draw(pixels: ByteArray, onSuccess: (generationId: Int) -> Unit)

    companion object {
        fun create(foreground: Foreground): RendererContext {
            require(foreground !is Foreground.Empty) { "Could not create empty renderer context" }

            val width = when (foreground) {
                is Foreground.Source -> foreground.renderer.format.width

                is Foreground.Frame -> foreground.frame.width

                is Foreground.Image -> foreground.width

                else -> 0
            }

            val height = when (foreground) {
                is Foreground.Source -> foreground.renderer.format.height

                is Foreground.Frame -> foreground.frame.height

                is Foreground.Image -> foreground.height

                else -> 0
            }

            val colorType = when (foreground) {
                is Foreground.Image -> foreground.colorType

                else -> ColorType.RGBA_8888
            }

            val alphaType = when (foreground) {
                is Foreground.Image -> foreground.alphaType

                else -> ColorAlphaType.UNPREMUL
            }

            val imageInfo = ImageInfo(
                width = width, height = height, colorType = colorType, alphaType = alphaType
            )

            val bitmap = Bitmap()

            if (!bitmap.allocPixels(imageInfo)) {
                bitmap.close()

                error("Could not allocate bitmap pixels")
            }

            val surface = Surface.makeRaster(imageInfo)

            return SkiaRendererContext(imageInfo = imageInfo, bitmap = bitmap, surface = surface)
        }
    }
}