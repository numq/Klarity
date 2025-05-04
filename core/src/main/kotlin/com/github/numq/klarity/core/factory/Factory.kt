package com.github.numq.klarity.core.factory

internal interface Factory<in Parameters, out Instance> {
    fun create(parameters: Parameters): Result<Instance>
}