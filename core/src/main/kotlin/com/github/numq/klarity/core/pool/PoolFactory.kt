package com.github.numq.klarity.core.pool

import com.github.numq.klarity.core.data.Data
import com.github.numq.klarity.core.factory.Factory

internal class PoolFactory : Factory<PoolFactory.Parameters, Pool<Data>> {
    data class Parameters(
        val poolCapacity: Int,
        val bufferCapacity: Int
    )

    override fun create(parameters: Parameters): Result<Pool<Data>> = with(parameters) {
        Pool.create(poolCapacity = poolCapacity, bufferCapacity = bufferCapacity)
    }
}