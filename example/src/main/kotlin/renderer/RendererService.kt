package renderer

interface RendererService {
    suspend fun add(id: String, location: String): Result<Unit>

    suspend fun reset(id: String): Result<Unit>

    suspend fun remove(id: String): Result<Unit>

    class Implementation(private val rendererRegistry: RendererRegistry) : RendererService {
        override suspend fun add(id: String, location: String) = rendererRegistry.add(id = id, location = location)

        override suspend fun reset(id: String) = rendererRegistry.reset(id = id)

        override suspend fun remove(id: String) = rendererRegistry.remove(id = id)
    }
}