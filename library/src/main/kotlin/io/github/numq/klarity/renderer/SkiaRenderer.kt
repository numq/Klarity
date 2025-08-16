package io.github.numq.klarity.renderer

import io.github.numq.klarity.frame.Frame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.skia.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.nanoseconds

internal class SkiaRenderer(
    override val width: Int,
    override val height: Int,
) : Renderer {
    private companion object {
        const val DRAWS_NOTHING_ID = 0
    }

    init {
        require(width > 0 && height > 0) {
            "Width and height must be positive"
        }
    }

    private val operationLock = Any()

    private val renderMutex = Mutex()

    private val isClosed = AtomicBoolean(false)

    private val isValid = AtomicBoolean(true)

    private val imageInfo = ImageInfo(
        width = width, height = height, colorType = ColorType.BGRA_8888, alphaType = ColorAlphaType.UNPREMUL
    )

    private val surface: Surface by lazy { Surface.makeRaster(imageInfo) }

    private val pixmap: Pixmap by lazy { Pixmap.make(info = imageInfo, addr = 0L, rowBytes = imageInfo.minRowBytes) }

    private val minByteSize by lazy { imageInfo.computeMinByteSize() }

    private val _generationId = MutableStateFlow(DRAWS_NOTHING_ID)

    override val generationId = _generationId.asStateFlow()

    private fun checkValid(): Boolean = !isClosed.get() && isValid.get() && !surface.isClosed && !pixmap.isClosed

    override fun drawsNothing() = _generationId.value == DRAWS_NOTHING_ID

    override fun draw(callback: (Surface) -> Unit) = synchronized(operationLock) {
        if (checkValid()) {
            try {
                callback(surface)
            } catch (_: Exception) {
                markInvalid()
            }
        }
    }

    override suspend fun render(frame: Frame.Content.Video) = renderMutex.withLock {
        runCatching {
            require(frame.width == width && frame.height == height) { "Invalid frame dimensions" }

            if (isClosed.get() || frame.data.isClosed || frame.data.size < minByteSize) {
                return@runCatching
            }

            synchronized(operationLock) {
                if (!checkValid()) {
                    return@synchronized
                }

                try {
                    frame.onRenderStart?.invoke()

                    val startTime = System.nanoTime().nanoseconds

                    pixmap.reset(info = imageInfo, buffer = frame.data, rowBytes = imageInfo.minRowBytes)

                    surface.writePixels(pixmap, 0, 0)

                    val renderTime = System.nanoTime().nanoseconds - startTime

                    frame.onRenderComplete?.invoke(renderTime)

                    _generationId.value = surface.generationId
                } catch (_: Exception) {
                    markInvalid()
                }
            }
        }
    }

    override suspend fun flush() = renderMutex.withLock {
        runCatching {
            synchronized(operationLock) {
                if (!checkValid()) {
                    return@synchronized
                }

                try {
                    pixmap.reset()

                    surface.flush()

                    _generationId.value = DRAWS_NOTHING_ID
                } catch (_: Exception) {
                    markInvalid()
                }
            }
        }
    }

    override suspend fun close() = renderMutex.withLock {
        runCatching {
            if (!isClosed.compareAndSet(false, true)) {
                return@runCatching
            }

            synchronized(operationLock) {
                try {
                    if (!surface.isClosed) {
                        surface.flush()
                        surface.close()
                    }

                    if (!pixmap.isClosed) {
                        pixmap.close()
                    }

                    _generationId.value = DRAWS_NOTHING_ID
                } catch (_: Exception) {
                    try {
                        surface.close()
                    } catch (_: Exception) {
                    }

                    try {
                        pixmap.close()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private fun markInvalid() {
        isValid.set(false)

        _generationId.value = DRAWS_NOTHING_ID
    }
}