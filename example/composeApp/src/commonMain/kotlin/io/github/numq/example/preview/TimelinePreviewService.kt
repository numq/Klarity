package io.github.numq.example.preview

import io.github.numq.example.item.Item
import io.github.numq.example.renderer.RendererRegistry
import io.github.numq.klarity.preview.PreviewManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration

interface TimelinePreviewService {
    suspend fun prepare(item: Item.Loaded): Result<Unit>

    suspend fun getPreview(timestamp: Duration, rendererId: String): Result<Unit>

    suspend fun release(): Result<Unit>

    suspend fun close(): Result<Unit>

    class Implementation(private val rendererRegistry: RendererRegistry) : TimelinePreviewService {
        private val mutex = Mutex()

        private val coroutineScope = CoroutineScope(Dispatchers.Default)

        private var previewManager: PreviewManager? = null

        private var timestamps: Channel<Pair<Duration, String>>? = null

        private var job: Job? = null

        override suspend fun prepare(item: Item.Loaded) = mutex.withLock {
            runCatching {
                check(job == null) { "Could not prepare preview" }

                previewManager = PreviewManager.create(location = item.location).getOrNull()

                timestamps = Channel<Pair<Duration, String>>(Channel.CONFLATED).apply {
                    job = coroutineScope.launch {
                        consumeEach { (timestamp, rendererId) ->
                            val renderer = rendererRegistry.get(id = rendererId).getOrThrow()

                            if (renderer != null) {
                                previewManager?.preview(renderer = renderer, timestamp = timestamp)?.getOrNull()
                            }
                        }
                    }
                }
            }
        }

        override suspend fun getPreview(timestamp: Duration, rendererId: String) = runCatching {
            timestamps?.send(Pair(timestamp, rendererId)) ?: Unit
        }

        override suspend fun release() = mutex.withLock {
            runCatching {
                job?.cancelAndJoin()

                job = null

                timestamps?.close()

                timestamps = null

                previewManager?.close()?.getOrThrow()

                previewManager = null
            }
        }

        override suspend fun close() = runCatching {
            coroutineScope.cancel()

            release().getOrThrow()
        }
    }
}