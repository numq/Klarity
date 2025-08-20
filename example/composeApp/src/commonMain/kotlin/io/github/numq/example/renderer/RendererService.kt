package io.github.numq.example.renderer

interface RendererService {
    suspend fun create(id: String, location: String, width: Int, height: Int): Result<Unit>

    suspend fun reset(id: String): Result<Unit>

    suspend fun remove(id: String): Result<Unit>

    class Implementation(private val rendererRegistry: RendererRegistry) : RendererService {
        override suspend fun create(id: String, location: String, width: Int, height: Int) = rendererRegistry.create(
            id = id, location = location, width = width, height = height
        )

        override suspend fun reset(id: String) = rendererRegistry.reset(id = id)

        override suspend fun remove(id: String) = rendererRegistry.remove(id = id)
    }
}