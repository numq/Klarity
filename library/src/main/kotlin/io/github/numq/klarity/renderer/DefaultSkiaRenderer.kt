package io.github.numq.klarity.renderer

import io.github.numq.klarity.format.VideoFormat
import io.github.numq.klarity.frame.Frame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.skia.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.nanoseconds

internal class DefaultSkiaRenderer(
    override val format: VideoFormat
) : SkiaRenderer {
    companion object {
        const val DRAWS_NOTHING_ID = 0
    }

    private val isClosed = AtomicBoolean(false)

    private val renderMutex = Mutex()

    private val cacheMutex = Mutex()

    private val cache = mutableListOf<Frame.Content.Video>()

    private val imageInfo = ImageInfo(
        width = format.width,
        height = format.height,
        colorType = ColorType.BGRA_8888,
        alphaType = ColorAlphaType.UNPREMUL
    )

    private val pixmap = Pixmap.make(info = imageInfo, addr = 0L, rowBytes = imageInfo.minRowBytes)

    private val surface = Surface.makeRaster(imageInfo)

    private val minByteSize by lazy { imageInfo.computeMinByteSize() }

    private fun isRenderable(frame: Frame.Content.Video) =
        !isClosed.get() && !surface.isClosed && !pixmap.isClosed && !frame.data.isClosed && frame.data.size >= minByteSize

    private fun isSaveable(frame: Frame.Content.Video) =
        !isClosed.get() && !frame.data.isClosed && frame.data.size >= minByteSize

    private fun isFlushable() = !isClosed.get() && !surface.isClosed && !pixmap.isClosed

    private val _generationId = MutableStateFlow(DRAWS_NOTHING_ID)

    override val generationId = _generationId.asStateFlow()

    override fun drawsNothing() = _generationId.value == DRAWS_NOTHING_ID

    override fun draw(callback: (Surface) -> Unit) {
        if (isClosed.get() || surface.isClosed || pixmap.isClosed) {
            return
        }

        callback(surface)
    }

    override suspend fun <T> withCache(callback: suspend (List<Frame.Content.Video>) -> T) = cacheMutex.withLock {
        callback(cache.toList())
    }

    override suspend fun render(frame: Frame.Content.Video) = renderMutex.withLock {
        runCatching {
            if (!isRenderable(frame)) {
                return@runCatching
            }

            frame.onRenderStart?.invoke()

            val startTime = System.nanoTime().nanoseconds

            pixmap.reset(info = imageInfo, buffer = frame.data, rowBytes = imageInfo.minRowBytes)

            surface.writePixels(pixmap, 0, 0)

            val renderTime = System.nanoTime().nanoseconds - startTime

            frame.onRenderComplete?.invoke(renderTime)
        }.onFailure {
            _generationId.value = DRAWS_NOTHING_ID
        }.onSuccess {
            if (!surface.isClosed) {
                _generationId.value = surface.generationId
            }
        }
    }

    override suspend fun save(frame: Frame.Content.Video) = cacheMutex.withLock {
        runCatching {
            if (!isSaveable(frame)) {
                return@runCatching
            }

            cache.add(frame.copy(data = frame.data.makeCopy()))
        }
    }

    override suspend fun flush() = renderMutex.withLock {
        runCatching {
            if (isFlushable()) {
                return@runCatching
            }

            pixmap.reset()

            surface.flush()
        }.onSuccess {
            _generationId.value = DRAWS_NOTHING_ID
        }
    }

    override suspend fun close() = cacheMutex.withLock {
        renderMutex.withLock {
            runCatching {
                if (!isClosed.compareAndSet(false, true)) {
                    return@runCatching
                }

                if (!surface.isClosed) {
                    surface.close()
                }

                if (!pixmap.isClosed) {
                    pixmap.close()
                }

                cache.forEach { cachedFrame ->
                    if (!cachedFrame.data.isClosed) {
                        cachedFrame.data.close()
                    }
                }

                cache.clear()
            }.onSuccess {
                _generationId.value = DRAWS_NOTHING_ID
            }
        }
    }
}