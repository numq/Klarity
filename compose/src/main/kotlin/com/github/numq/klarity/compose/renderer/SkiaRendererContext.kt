package com.github.numq.klarity.compose.renderer

import com.github.numq.klarity.core.renderer.Renderer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ContentChangeMode
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Surface

internal class SkiaRendererContext(
    renderer: Renderer,
    private val imageInfo: ImageInfo,
    private val bitmap: Bitmap,
    private val surface: Surface,
) : RendererContext {
    init {
        renderer.onRender { frame ->
            draw(frame.bytes)
        }
    }

    private val lock = Any()

    private val _generationId = MutableStateFlow(0)

    override val generationId = _generationId.asStateFlow()

    override fun withSurface(callback: (Surface) -> Unit) = synchronized(lock) {
        if (!surface.isClosed) {
            callback(surface)
        }
    }

    override fun draw(pixels: ByteArray) = synchronized(lock) {
        when {
            surface.isClosed || bitmap.isClosed || pixels.size < bitmap.computeByteSize() -> return

            bitmap.installPixels(imageInfo, pixels, imageInfo.minRowBytes) -> {
                surface.notifyContentWillChange(ContentChangeMode.DISCARD)

                surface.writePixels(bitmap, 0, 0)

                surface.flushAndSubmit()

                _generationId.value = surface.generationId
            }
        }
    }

    override fun close() = synchronized(lock) {
        if (!bitmap.isClosed) {
            bitmap.close()
        }

        if (!surface.isClosed) {
            surface.close()
        }
    }
}