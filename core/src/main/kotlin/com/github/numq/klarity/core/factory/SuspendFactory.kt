package com.github.numq.klarity.core.factory

interface SuspendFactory<in Parameters, out Instance> {
    suspend fun create(parameters: Parameters): Result<Instance>
}