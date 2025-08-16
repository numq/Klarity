package io.github.numq.klarity.pool

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.skia.impl.Managed
import java.util.concurrent.atomic.AtomicBoolean

internal class ChannelPool<Item : Managed>(poolCapacity: Int, private val createItem: () -> Item) : Pool<Item> {
    private val mutex = Mutex()

    private val items = List(poolCapacity) { createItem() }

    private lateinit var availableItems: Channel<Item>

    private val isClosed = AtomicBoolean(false)

    private fun initializePool() {
        availableItems = Channel(capacity = items.size)

        items.forEach { item ->
            availableItems.trySendBlocking(item).getOrThrow()
        }
    }

    init {
        require(poolCapacity > 0) { "Pool capacity must be positive" }

        initializePool()
    }

    override suspend fun acquire() = runCatching {
        check(!isClosed.get()) { "Pool is closed" }

        availableItems.receive()
    }

    override suspend fun release(item: Item) = runCatching {
        check(!isClosed.get()) { "Pool is closed" }

        availableItems.send(item)
    }

    override suspend fun reset() = mutex.withLock {
        runCatching {
            availableItems.close()

            initializePool()
        }
    }

    override suspend fun close() = mutex.withLock {
        runCatching {
            try {
                availableItems.close()
            } finally {
                items.forEach { item ->
                    if (!item.isClosed) {
                        item.close()
                    }
                }

                isClosed.set(true)
            }

            Unit
        }
    }
}