package io.github.numq.klarity.renderer

import io.github.numq.klarity.frame.Frame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.skia.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.nanoseconds

internal class SkiaRenderer(override val width: Int, override val height: Int) : Renderer {
    private companion object {
        const val DRAWS_NOTHING_ID = 0
    }

    init {
        require(width > 0 && height > 0) { "Width and height must be positive" }
    }

    private val renderMutex = Mutex()

    private val isClosed = AtomicBoolean(false)

    private val isValid = AtomicBoolean(true)

    private val isRendering = AtomicBoolean(false)

    private val hasContent = AtomicBoolean(false)

    private val imageInfo = ImageInfo(
        width = width, height = height, colorType = ColorType.BGRA_8888, alphaType = ColorAlphaType.UNPREMUL
    )

    private val surface: Surface by lazy { Surface.makeRaster(imageInfo = imageInfo) }

    private val pixmap: Pixmap by lazy { Pixmap.make(info = imageInfo, addr = 0L, rowBytes = imageInfo.minRowBytes) }

    private val minByteSize by lazy { imageInfo.computeMinByteSize() }

    private val _generationId = MutableStateFlow(DRAWS_NOTHING_ID)

    override val generationId = _generationId.asStateFlow()

    private val _drawsNothing = MutableStateFlow(true)

    override val drawsNothing = _drawsNothing.asStateFlow()

    private fun withStateLock(block: () -> Unit) {
        if (isClosed.get() || !isValid.get() || surface.isClosed || pixmap.isClosed) return

        block()
    }

    private fun updateState(id: Int, markInvalid: Boolean = false) {
        if (isClosed.get()) return

        if (markInvalid) {
            isValid.set(false)
        }

        _generationId.value = id

        val drawsNothingValue = id == DRAWS_NOTHING_ID

        _drawsNothing.value = drawsNothingValue

        hasContent.set(!drawsNothingValue)
    }

    override fun onRender(
        canvas: Canvas,
        backgroundRect: Rect,
        backgroundColorPaint: Paint?,
        backgroundBlurPaint: Paint?,
        foregroundRect: Rect
    ) {
        if (isRendering.get() || !hasContent.get()) return

        isRendering.set(true)

        try {
            withStateLock {
                surface.makeImageSnapshot().use { image ->
                    if (backgroundColorPaint == null && backgroundBlurPaint == null) {
                        canvas.drawImageRect(image = image, dst = foregroundRect)

                        return@withStateLock
                    }

                    backgroundColorPaint?.let { paint ->
                        canvas.drawPaint(paint = paint)
                    }

                    backgroundBlurPaint?.let { paint ->
                        canvas.drawImageRect(image = image, dst = backgroundRect, paint)
                    }

                    canvas.drawImageRect(image = image, dst = foregroundRect)
                }
            }
        } catch (_: Throwable) {
            markInvalid()
        } finally {
            isRendering.set(false)
        }
    }

    private fun markInvalid() = updateState(DRAWS_NOTHING_ID, markInvalid = true)

    override suspend fun render(frame: Frame.Content.Video) = renderMutex.withLock {
        runCatching {
            if (frame.data.isClosed || frame.data.size < minByteSize) return@runCatching

            require(frame.width == width && frame.height == height) {
                "Invalid frame dimensions: expected ${width}x$height, got ${frame.width}x${frame.height}"
            }

            withStateLock {
                frame.onRenderStart?.invoke()

                val startTime = System.nanoTime()

                pixmap.reset(info = imageInfo, buffer = frame.data, rowBytes = imageInfo.minRowBytes)

                surface.writePixels(pixmap, 0, 0)

                val renderTime = (System.nanoTime() - startTime).nanoseconds

                frame.onRenderComplete?.invoke(renderTime)

                updateState(surface.generationId)
            }
        }.onFailure { markInvalid() }
    }

    override suspend fun flush() = renderMutex.withLock {
        runCatching {
            withStateLock {
                surface.flush()

                updateState(DRAWS_NOTHING_ID)
            }
        }.onFailure { markInvalid() }
    }

    override suspend fun close() = renderMutex.withLock {
        runCatching {
            if (isClosed.get()) return@runCatching

            isClosed.set(true)

            try {
                if (!surface.isClosed) {
                    surface.flush()

                    surface.close()
                }

                if (!pixmap.isClosed) {
                    pixmap.close()
                }
            } catch (t: Throwable) {
                if (!pixmap.isClosed) {
                    pixmap.close()
                }

                throw t
            } finally {
                updateState(DRAWS_NOTHING_ID)
            }
        }
    }
}