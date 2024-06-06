package factory

interface Factory<in Parameters, out Instance> {
    fun create(parameters: Parameters): Result<Instance>
}