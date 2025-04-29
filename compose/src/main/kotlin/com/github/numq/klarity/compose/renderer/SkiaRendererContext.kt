package com.github.numq.klarity.compose.renderer

import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.renderer.Renderer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.skia.ContentChangeMode
import org.jetbrains.skia.Pixmap
import org.jetbrains.skia.Surface
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.nanoseconds

internal class SkiaRendererContext(
    renderer: Renderer,
    private val surface: Surface,
) : RendererContext {
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    private val coroutineScope = CoroutineScope(dispatcher + SupervisorJob())

    init {
        coroutineScope.launch {
            renderer.frame.collect(::render)
        }
    }

    private var isClosed = AtomicBoolean(false)

    private val minByteSize = surface.imageInfo.computeMinByteSize()

    private val _generationId = MutableStateFlow(-1)

    override val generationId = _generationId.asStateFlow()

    private fun render(frame: Frame.Content.Video) = when {
        isClosed.get() || surface.isClosed || frame.buffer < 0 || frame.size < minByteSize -> Unit

        else -> {
            frame.onRenderStart?.invoke()

            val startTime = System.nanoTime().nanoseconds

            Pixmap.make(
                info = surface.imageInfo, addr = frame.buffer, rowBytes = surface.imageInfo.minRowBytes
            ).use { pixmap ->
                surface.notifyContentWillChange(ContentChangeMode.DISCARD)

                surface.writePixels(pixmap, 0, 0)

                surface.flushAndSubmit()

                _generationId.value = surface.generationId
            }

            val renderTime = System.nanoTime().nanoseconds - startTime

            frame.onRenderComplete?.invoke(renderTime)
        }
    }

    override fun withSurface(callback: (Surface) -> Unit) {
        if (!isClosed.get() && !surface.isClosed) {
            callback(surface)
        }
    }

    override fun close() {
        if (!isClosed.get()) {
            coroutineScope.cancel()

            dispatcher.close()

            if (!surface.isClosed) {
                surface.close()
            }

            isClosed.set(true)
        }
    }
}