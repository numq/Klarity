package factory

interface SuspendFactory<in Parameters, out Instance> {
    suspend fun create(parameters: Parameters): Result<Instance>
}