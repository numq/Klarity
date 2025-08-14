package io.github.numq.example.renderer

import io.github.numq.klarity.probe.ProbeManager
import io.github.numq.klarity.renderer.Renderer
import io.github.numq.klarity.snapshot.SnapshotManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Duration

interface RendererRegistry {
    val renderers: StateFlow<Map<String, RegisteredRenderer>>

    suspend fun add(id: String, location: String): Result<Unit>

    suspend fun get(id: String): Result<Renderer?>

    suspend fun update(id: String, timestamp: Duration): Result<Unit>

    suspend fun reset(id: String): Result<Unit>

    suspend fun remove(id: String): Result<Unit>

    suspend fun close(): Result<Unit>

    class Implementation(private val snapshotManager: SnapshotManager) : RendererRegistry {
        private val _renderers = MutableStateFlow(emptyMap<String, RegisteredRenderer>())

        override val renderers = _renderers.asStateFlow()

        override suspend fun add(id: String, location: String) = runCatching {
            check(_renderers.value.keys.contains(id).not()) { "Renderer is already registered" }

            val media = ProbeManager.probe(location = location).getOrThrow()

            val format = media.videoFormat

            if (format != null) {
                val renderer = Renderer.create(format = format).getOrThrow()

                _renderers.update { registeredRenderers ->
                    registeredRenderers.toMutableMap().apply {
                        set(id, RegisteredRenderer(location = location, renderer = renderer))
                    }
                }
            }
        }

        override suspend fun get(id: String) = runCatching {
            _renderers.value[id]?.renderer
        }

        override suspend fun update(id: String, timestamp: Duration) = runCatching {
            val registeredRenderer = _renderers.value[id]

            if (registeredRenderer != null) {
                val (location, renderer) = registeredRenderer

                snapshotManager.snapshot(location = location, keyframesOnly = false) {
                    timestamp
                }.getOrThrow()?.use { snapshot ->
                    renderer.render(frame = snapshot.frame).getOrThrow()
                }
            }
        }

        override suspend fun reset(id: String) = runCatching {
            val registeredRenderer = _renderers.value[id]

            if (registeredRenderer != null) {
                val (location, renderer) = registeredRenderer

                snapshotManager.snapshot(location = location).getOrThrow()?.use { snapshot ->
                    renderer.render(frame = snapshot.frame).getOrThrow()
                }
            }
        }

        override suspend fun remove(id: String) = runCatching {
            _renderers.update { registeredRenderers ->
                registeredRenderers.toMutableMap().apply {
                    remove(id)?.renderer?.close()?.getOrThrow()
                }
            }
        }

        override suspend fun close() = runCatching {
            _renderers.value.values.forEach { registeredRenderer ->
                registeredRenderer.renderer.close().getOrThrow()
            }
        }
    }
}