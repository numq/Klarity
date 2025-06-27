package preview

import io.github.numq.klarity.preview.PreviewManager
import item.Item
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import renderer.RendererRegistry
import kotlin.time.Duration

interface LoopPreviewService {
    suspend fun start(item: Item.Loaded, rendererId: String): Result<Unit>

    suspend fun stop(): Result<Unit>

    suspend fun close(): Result<Unit>

    class Implementation(private val rendererRegistry: RendererRegistry) : LoopPreviewService {
        private companion object {
            const val PREVIEW_DELAY = 500L

            const val PREVIEW_FRAMES = 5
        }

        private val mutex = Mutex()

        private val coroutineScope = CoroutineScope(Dispatchers.Default)

        private var previewManager: PreviewManager? = null

        private var job: Job? = null

        override suspend fun start(item: Item.Loaded, rendererId: String) = mutex.withLock {
            runCatching {
                check(job == null) { "Could not start preview" }

                previewManager = PreviewManager.create(location = item.location).getOrThrow()

                val renderer = rendererRegistry.get(id = rendererId).getOrThrow()

                if (renderer != null) {
                    job = coroutineScope.launch {
                        while (isActive) {
                            repeat(PREVIEW_FRAMES) { index ->
                                val timestamp = (item.duration * index) / PREVIEW_FRAMES

                                if (timestamp in Duration.ZERO..item.duration) {
                                    previewManager?.preview(renderer = renderer, timestamp = timestamp)?.getOrThrow()

                                    delay(PREVIEW_DELAY)
                                }
                            }
                        }
                    }
                }
            }
        }

        override suspend fun stop() = mutex.withLock {
            runCatching {
                job?.cancelAndJoin()

                job = null

                previewManager?.close()?.getOrThrow()

                previewManager = null
            }
        }

        override suspend fun close() = runCatching {
            coroutineScope.cancel()

            stop().getOrThrow()
        }
    }
}