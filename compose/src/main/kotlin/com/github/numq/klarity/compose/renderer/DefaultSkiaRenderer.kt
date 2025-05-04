package com.github.numq.klarity.compose.renderer

import com.github.numq.klarity.core.format.VideoFormat
import com.github.numq.klarity.core.frame.Frame
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
        const val DRAWS_NOTHING_ID = -1
    }

    private val isClosed = AtomicBoolean(false)

    private val renderMutex = Mutex()

    private val cacheMutex = Mutex()

    private val cache = mutableListOf<CachedFrame>()

    private val imageInfo = ImageInfo(
        width = format.width,
        height = format.height,
        colorType = ColorType.BGRA_8888,
        alphaType = ColorAlphaType.UNPREMUL
    )

    private val minByteSize by lazy { imageInfo.computeMinByteSize() }

    private val surface = Surface.makeRaster(imageInfo)

    private val targetPixmap = Data.makeEmpty().use { buffer ->
        Pixmap.make(
            info = imageInfo, buffer = buffer, rowBytes = imageInfo.minRowBytes
        )
    }

    private fun isRenderable(cachedFrame: CachedFrame) =
        !isClosed.get() && !cachedFrame.pixmap.isClosed

    private fun isRenderable(frame: Frame.Content.Video) =
        !isClosed.get() && !frame.isClosed() && frame.remaining >= minByteSize

    private val _generationId = MutableStateFlow(DRAWS_NOTHING_ID)

    override val generationId = _generationId.asStateFlow()

    override fun drawsNothing() = _generationId.value == DRAWS_NOTHING_ID

    override fun draw(callback: (Surface) -> Unit) {
        if (isClosed.get()) {
            return
        }

        callback(surface)
    }

    override suspend fun withCache(callback: suspend (List<CachedFrame>) -> Unit) = cacheMutex.withLock {
        if (isClosed.get()) {
            return
        }

        callback(cache)
    }

    override suspend fun render(cachedFrame: CachedFrame) = renderMutex.withLock {
        runCatching {
            if (!isRenderable(cachedFrame)) {
                return@runCatching
            }

            surface.writePixels(cachedFrame.pixmap, 0, 0)
        }.onFailure {
            _generationId.value = DRAWS_NOTHING_ID
        }.onSuccess {
            _generationId.value = surface.generationId
        }
    }

    override suspend fun render(frame: Frame.Content.Video) = renderMutex.withLock {
        runCatching {
            if (isClosed.get() || frame.isClosed() || frame.remaining < minByteSize) {
                return@runCatching
            }

            frame.onRenderStart?.invoke()

            val startTime = System.nanoTime().nanoseconds

            targetPixmap.reset(info = imageInfo, addr = frame.data.pointer, rowBytes = imageInfo.minRowBytes)

            surface.writePixels(targetPixmap, 0, 0)

            val renderTime = System.nanoTime().nanoseconds - startTime

            frame.onRenderComplete?.invoke(renderTime)
        }.onFailure {
            _generationId.value = DRAWS_NOTHING_ID
        }.onSuccess {
            _generationId.value = surface.generationId
        }
    }

    override suspend fun save(frame: Frame.Content.Video) = cacheMutex.withLock {
        runCatching {
            if (isRenderable(frame)) {
                return@runCatching
            }

            val cachedPixmap = Pixmap.make(
                info = imageInfo, addr = frame.data.pointer, rowBytes = imageInfo.minRowBytes
            ).use { pixmap ->
                Pixmap.make(
                    info = imageInfo, buffer = Data.makeFromBytes(pixmap.buffer.bytes), rowBytes = imageInfo.minRowBytes
                )
            }

            cache.add(CachedFrame(frame = frame, pixmap = cachedPixmap))
        }
    }

    override suspend fun flush(color: Int) = renderMutex.withLock {
        runCatching {
            if (isClosed.get()) {
                return@runCatching
            }

            targetPixmap.reset()

            surface.canvas.clear(color)
        }.onFailure {
            _generationId.value = DRAWS_NOTHING_ID
        }.onSuccess {
            _generationId.value = surface.generationId
        }
    }

    override suspend fun close() = cacheMutex.withLock {
        runCatching {
            if (isClosed.compareAndSet(false, true)) {
                if (!surface.isClosed) {
                    surface.close()
                }

                if (!targetPixmap.isClosed) {
                    targetPixmap.close()
                }

                val iterator = cache.iterator()

                while (iterator.hasNext()) {
                    val cachedFrame = iterator.next()

                    if (!cachedFrame.pixmap.isClosed) {
                        cachedFrame.pixmap.close()
                    }

                    iterator.remove()
                }

                _generationId.value = DRAWS_NOTHING_ID

                isClosed.set(true)
            }
        }
    }
}