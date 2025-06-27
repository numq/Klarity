package renderer

import io.github.numq.klarity.probe.ProbeManager
import io.github.numq.klarity.renderer.Renderer
import io.github.numq.klarity.snapshot.SnapshotManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface RendererRegistry {
    suspend fun add(id: String, location: String): Result<Unit>

    suspend fun get(id: String): Result<Renderer?>

    suspend fun reset(id: String): Result<Unit>

    suspend fun remove(id: String): Result<Unit>

    suspend fun close(): Result<Unit>

    class Implementation(private val snapshotManager: SnapshotManager) : RendererRegistry {
        private val mutex = Mutex()

        private val registeredRenderers = mutableMapOf<String, RegisteredRenderer>()

        override suspend fun add(id: String, location: String) = mutex.withLock {
            runCatching {
                check(registeredRenderers.keys.contains(id).not()) { "Renderer is already registered" }

                val media = ProbeManager.probe(location = location).getOrThrow()

                val format = media.videoFormat

                if (format != null) {
                    val renderer = Renderer.create(format = format).getOrThrow()

                    registeredRenderers[id] = RegisteredRenderer(location = location, renderer = renderer)
                }
            }
        }

        override suspend fun get(id: String) = mutex.withLock {
            runCatching {
                registeredRenderers[id]?.renderer
            }
        }

        override suspend fun reset(id: String) = mutex.withLock {
            runCatching {
                val registeredRenderer = registeredRenderers[id]

                if (registeredRenderer != null) {
                    val (location, renderer) = registeredRenderer

                    snapshotManager.snapshot(location = location).getOrThrow()?.use { snapshot ->
                        renderer.render(frame = snapshot.frame).getOrThrow()
                    }
                }
            }
        }

        override suspend fun remove(id: String) = mutex.withLock {
            runCatching {
                registeredRenderers.remove(id)?.renderer?.close()?.getOrThrow() ?: Unit
            }
        }

        override suspend fun close() = mutex.withLock {
            runCatching {
                registeredRenderers.values.toList().forEach { registeredRenderer ->
                    registeredRenderer.renderer.close().getOrThrow()
                }
            }
        }
    }
}