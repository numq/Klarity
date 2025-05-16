package io.github.numq.klarity.pool

import org.jetbrains.skia.impl.Managed

internal interface Pool<Item : Managed> {
    suspend fun acquire(): Result<Item>

    suspend fun release(item: Item): Result<Unit>

    suspend fun reset(): Result<Unit>

    suspend fun close(): Result<Unit>

    companion object {
        fun <Item : Managed> create(poolCapacity: Int, createItem: () -> Item): Result<Pool<Item>> = runCatching {
            ChannelPool(poolCapacity = poolCapacity, createItem = createItem)
        }
    }
}