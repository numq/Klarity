package com.github.numq.klarity.compose.renderer

import com.github.numq.klarity.core.format.VideoFormat
import com.github.numq.klarity.core.frame.Frame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.skia.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Duration.Companion.nanoseconds

internal class DefaultSkiaRenderer(
    override val format: VideoFormat,
    private val imageInfo: ImageInfo,
    private val surface: Surface,
) : SkiaRenderer {
    companion object {
        const val DRAWS_NOTHING_ID = -1
    }

    private val isClosed = AtomicBoolean(false)

    private val renderMutex = Mutex()

    private val cacheMutex = Mutex()

    private val minByteSize by lazy { imageInfo.computeMinByteSize() }

    private val renderPixmap = Pixmap.make(
        info = imageInfo,
        buffer = Data.makeEmpty(),
        rowBytes = imageInfo.minRowBytes
    )

    private val cache = mutableListOf<CachedFrame>()

    private val _generationId = MutableStateFlow(DRAWS_NOTHING_ID)

    override val generationId = _generationId.asStateFlow()

    override fun drawsNothing() = _generationId.value == DRAWS_NOTHING_ID

    override fun draw(callback: (Surface) -> Unit) {
        if (!isClosed.get()) {
            callback(surface)
        }
    }

    override suspend fun withCache(callback: suspend (List<CachedFrame>) -> Unit) = cacheMutex.withLock {
        if (!isClosed.get()) {
            callback(cache)
        }
    }

    override suspend fun render(cachedFrame: CachedFrame) = renderMutex.withLock {
        runCatching {
            suspendCoroutine { continuation ->
                runCatching {
                    require(!isClosed.get() && !cachedFrame.pixmap.isClosed)

                    surface.notifyContentWillChange(ContentChangeMode.DISCARD)

                    surface.writePixels(cachedFrame.pixmap, 0, 0)

                    surface.flushAndSubmit()

                    _generationId.value = surface.generationId
                }.onFailure {
                    _generationId.value = DRAWS_NOTHING_ID
                }.onFailure(continuation::resumeWithException).onSuccess(continuation::resume).getOrThrow()
            }
        }
    }

    override suspend fun render(frame: Frame.Content.Video) = renderMutex.withLock {
        runCatching {
            suspendCoroutine { continuation ->
                runCatching {
                    require(!isClosed.get() && !frame.isClosed() && frame.size >= minByteSize)

                    frame.onRenderStart?.invoke()

                    val startTime = System.nanoTime().nanoseconds

                    renderPixmap.reset(info = imageInfo, addr = frame.buffer, rowBytes = imageInfo.minRowBytes)

                    surface.notifyContentWillChange(ContentChangeMode.DISCARD)

                    surface.writePixels(renderPixmap, 0, 0)

                    surface.flushAndSubmit()

                    _generationId.value = surface.generationId

                    val renderTime = System.nanoTime().nanoseconds - startTime

                    frame.onRenderComplete?.invoke(renderTime)

                    Unit
                }.onFailure {
                    _generationId.value = DRAWS_NOTHING_ID
                }.onFailure(continuation::resumeWithException).onSuccess(continuation::resume).getOrThrow()
            }
        }
    }

    override suspend fun save(frame: Frame.Content.Video) = cacheMutex.withLock {
        runCatching {
            require(!isClosed.get() && !frame.isClosed() && frame.size >= minByteSize)

            val cachedPixmap = Pixmap.make(
                info = imageInfo,
                addr = frame.buffer,
                rowBytes = imageInfo.minRowBytes
            ).use { pixmap ->
                Pixmap.make(
                    info = imageInfo,
                    buffer = Data.makeFromBytes(pixmap.buffer.bytes),
                    rowBytes = imageInfo.minRowBytes
                )
            }

            val cachedFrame = CachedFrame(frame = frame, pixmap = cachedPixmap)

            check(cache.add(cachedFrame)) { "Unable to save frame in cache" }
        }
    }

    override suspend fun flush() = renderMutex.withLock {
        runCatching {
            suspendCoroutine { continuation ->
                runCatching {
                    require(!isClosed.get())

                    surface.notifyContentWillChange(ContentChangeMode.DISCARD)

                    surface.canvas.clear(Color.BLACK)

                    surface.flushAndSubmit()

                    _generationId.value = surface.generationId
                }.onFailure(continuation::resumeWithException).onSuccess {
                    _generationId.value = DRAWS_NOTHING_ID
                }.onSuccess(continuation::resume).getOrThrow()
            }
        }
    }

    override suspend fun close() = cacheMutex.withLock {
        runCatching {
            if (!isClosed.get()) {
                if (!renderPixmap.isClosed) {
                    renderPixmap.close()
                }

                if (!surface.isClosed) {
                    surface.close()
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