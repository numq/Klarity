package io.github.numq.example.renderer

import io.github.numq.klarity.renderer.Renderer
import io.github.numq.klarity.snapshot.SnapshotManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface RendererRegistry {
    val renderers: StateFlow<Map<String, RegisteredRenderer>>

    suspend fun create(id: String, location: String, width: Int, height: Int): Result<Unit>

    suspend fun get(id: String): Result<Renderer?>

    suspend fun reset(id: String): Result<Unit>

    suspend fun remove(id: String): Result<Unit>

    suspend fun close(): Result<Unit>

    class Implementation(private val snapshotManager: SnapshotManager) : RendererRegistry {
        private val _renderers = MutableStateFlow(emptyMap<String, RegisteredRenderer>())

        override val renderers = _renderers.asStateFlow()

        override suspend fun create(id: String, location: String, width: Int, height: Int) = runCatching {
            check(renderers.value.keys.contains(id).not()) { "Renderer is already registered" }

            val renderer = Renderer.create(width = width, height = height).getOrNull()

            if (renderer != null) {
                _renderers.update { registeredRenderers ->
                    registeredRenderers.toMutableMap().apply {
                        set(id, RegisteredRenderer(id = id, location = location, renderer = renderer))
                    }
                }
            }
        }

        override suspend fun reset(id: String) = runCatching {
            val registeredRenderer = renderers.value[id]

            if (registeredRenderer != null) {
                val (_, location, renderer) = registeredRenderer

                snapshotManager.snapshot(location = location).getOrThrow()?.use { snapshot ->
                    renderer.render(frame = snapshot.frame).getOrThrow()
                }
            }
        }

        override suspend fun get(id: String) = runCatching {
            renderers.value[id]?.renderer
        }

        override suspend fun remove(id: String) = runCatching {
            _renderers.update { registeredRenderers ->
                registeredRenderers.toMutableMap().apply {
                    remove(id)?.renderer?.close()?.getOrThrow()
                }
            }
        }

        override suspend fun close() = runCatching {
            renderers.value.values.forEach { registeredRenderer ->
                registeredRenderer.renderer.close().getOrThrow()
            }
        }
    }
}