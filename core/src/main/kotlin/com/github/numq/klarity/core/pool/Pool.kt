package com.github.numq.klarity.core.pool

import com.github.numq.klarity.core.data.Data

internal interface Pool<T> {
    suspend fun acquire(): Result<T>

    suspend fun release(item: T): Result<Unit>

    suspend fun reset(): Result<Unit>

    suspend fun close(): Result<Unit>

    companion object {
        fun create(poolCapacity: Int, bufferCapacity: Int): Result<Pool<Data>> = runCatching {
            DataPool(poolCapacity = poolCapacity, bufferCapacity = bufferCapacity)
        }
    }
}