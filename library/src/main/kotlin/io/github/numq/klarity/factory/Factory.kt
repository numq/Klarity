package io.github.numq.klarity.factory

internal interface Factory<in Parameters, out Instance> {
    fun create(parameters: Parameters): Result<Instance>
}