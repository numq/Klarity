package io.github.numq.klarity.preview

import io.github.numq.klarity.decoder.Decoder
import io.github.numq.klarity.frame.Frame
import io.github.numq.klarity.media.Media
import io.github.numq.klarity.pool.Pool
import io.github.numq.klarity.renderer.Renderer
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.skia.Data
import kotlin.time.Duration

internal class DefaultPreviewManager(
    private val decoder: Decoder<Media.Video>,
    private val pool: Pool<Data>,
) : PreviewManager {
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var previewJob: Job? = null

    override val format = decoder.media.videoFormat

    private val renderableMutex = Mutex()

    private val renderers = mutableSetOf<Renderer>()

    override suspend fun getRenderers() = renderableMutex.withLock {
        runCatching {
            renderers.toList()
        }
    }

    override suspend fun attachRenderer(renderer: Renderer) = renderableMutex.withLock {
        runCatching {
            renderers.add(renderer)

            Unit
        }
    }

    override suspend fun detachRenderer(renderer: Renderer) = renderableMutex.withLock {
        runCatching {
            renderers.remove(renderer)

            Unit
        }
    }

    override suspend fun detachRenderers() = renderableMutex.withLock {
        runCatching {
            renderers.clear()
        }
    }

    override suspend fun preview(
        timestamp: Duration,
        debounceTime: Duration,
        keyframesOnly: Boolean,
    ) = runCatching {
        previewJob?.cancelAndJoin()
        previewJob = coroutineScope.launch {
            delay(debounceTime)

            check(timestamp in Duration.ZERO..decoder.media.duration) { "Preview timestamp is out of range" }

            if (timestamp.isPositive()) {
                decoder.seekTo(
                    timestamp = timestamp.coerceIn(Duration.ZERO..decoder.media.duration),
                    keyframesOnly = keyframesOnly
                ).getOrThrow()
            }

            val data = pool.acquire().getOrThrow()

            try {
                (decoder.decodeVideo(data = data).getOrThrow() as? Frame.Content.Video)?.let { frame ->
                    getRenderers().getOrThrow().map { renderer ->
                        async {
                            renderer.render(frame)
                        }
                    }.awaitAll().fold(Result.success(Unit)) { acc, result ->
                        acc.fold(onSuccess = { result }, onFailure = { acc })
                    }.getOrThrow()
                }
            } finally {
                pool.release(item = data).getOrThrow()
            }
        }
    }.recoverCatching { t ->
        if (t !is CancellationException) {
            throw PreviewManagerException(t)
        }
    }

    override suspend fun close() = runCatching {
        coroutineScope.cancel()

        decoder.close().getOrThrow()

        pool.close().getOrThrow()
    }
}