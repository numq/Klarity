package com.github.numq.klarity.compose.renderer

import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.renderer.Renderer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.skia.ContentChangeMode
import org.jetbrains.skia.Pixmap
import org.jetbrains.skia.Surface

internal class SkiaRendererContext(
    renderer: Renderer?,
    private val surface: Surface,
) : RendererContext {
    init {
        renderer?.onRender(::render)
    }

    private val lock = Any()

    private val minByteSize = surface.imageInfo.computeMinByteSize()

    private val _generationId = MutableStateFlow(-1)

    override val generationId = _generationId.asStateFlow()

    private fun render(frame: Frame.Video.Content) = synchronized(lock) {
        when {
            surface.isClosed || frame.isClosed || frame.bufferSize < minByteSize -> return

            else -> Pixmap.make(
                info = surface.imageInfo, addr = frame.bufferHandle, rowBytes = surface.imageInfo.minRowBytes
            ).use { pixmap ->
                surface.notifyContentWillChange(ContentChangeMode.DISCARD)

                surface.writePixels(pixmap, 0, 0)

                surface.flushAndSubmit()

                _generationId.value = surface.generationId
            }
        }
    }

    override fun withSurface(callback: (Surface) -> Unit) = synchronized(lock) {
        if (!surface.isClosed) {
            callback(surface)
        }
    }

    override fun close() = synchronized(lock) {
        if (!surface.isClosed) {
            surface.close()
        }
    }
}