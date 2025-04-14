package com.github.numq.klarity.compose.renderer

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ContentChangeMode
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Surface

internal data class SkiaRendererContext(
    private val imageInfo: ImageInfo,
    private val bitmap: Bitmap,
    override val surface: Surface,
) : RendererContext {
    override fun draw(pixels: ByteArray, onSuccess: (generationId: Int) -> Unit) {
        if (bitmap.installPixels(imageInfo, pixels, imageInfo.minRowBytes)) {
            surface.notifyContentWillChange(ContentChangeMode.DISCARD)

            surface.canvas.writePixels(bitmap, 0, 0)

            surface.flushAndSubmit()

            onSuccess(surface.generationId)
        }
    }


    override fun close() {
        bitmap.close()

        surface.close()
    }
}