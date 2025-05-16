package io.github.numq.klarity.pool

import io.github.numq.klarity.factory.Factory
import org.jetbrains.skia.Data

internal class PoolFactory : Factory<PoolFactory.Parameters, Pool<Data>> {
    data class Parameters(
        val poolCapacity: Int,
        val createData: () -> Data
    )

    override fun create(parameters: Parameters): Result<Pool<Data>> = with(parameters) {
        Pool.create(poolCapacity = poolCapacity, createItem = createData)
    }
}