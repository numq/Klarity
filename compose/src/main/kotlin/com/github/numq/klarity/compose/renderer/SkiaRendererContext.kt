package com.github.numq.klarity.compose.renderer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ContentChangeMode
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Surface

internal data class SkiaRendererContext(
    private val imageInfo: ImageInfo,
    private val bitmap: Bitmap,
    private val surface: Surface,
) : RendererContext {
    private val _generationId = MutableStateFlow(0)

    override val generationId = _generationId.asStateFlow()

    override fun withSurface(callback: (Surface) -> Unit) = callback(surface)

    override fun draw(pixels: ByteArray) {
        if (bitmap.isClosed || surface.isClosed) {
            return
        }

        if (bitmap.installPixels(imageInfo, pixels, imageInfo.minRowBytes)) {
            surface.notifyContentWillChange(ContentChangeMode.DISCARD)

            surface.writePixels(bitmap, 0, 0)

            surface.flushAndSubmit()

            _generationId.value = surface.generationId
        }
    }

    override fun reset() {
        if (bitmap.isClosed || surface.isClosed) {
            return
        }

        surface.flush()
    }

    override fun close() {
        bitmap.close()

        surface.close()
    }
}