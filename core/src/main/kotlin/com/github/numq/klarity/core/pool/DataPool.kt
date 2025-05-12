package com.github.numq.klarity.core.pool

import com.github.numq.klarity.core.data.Data
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

internal class DataPool(poolCapacity: Int, bufferCapacity: Int) : Pool<Data> {
    private val mutex = Mutex()

    private val items = Array(poolCapacity) { Data.allocate(bufferCapacity) }

    private lateinit var availableItems: Channel<Data>

    private val isClosed = AtomicBoolean(false)

    init {
        require(poolCapacity > 0) { "Pool capacity must be positive" }

        require(bufferCapacity > 0) { "Buffer capacity must be positive" }

        initializePool()
    }

    private fun initializePool() {
        availableItems = Channel(capacity = items.size)

        items.forEach { data ->
            availableItems.trySendBlocking(data).getOrThrow()
        }
    }

    override suspend fun acquire() = runCatching {
        check(!isClosed.get()) { "Pool is closed" }

        availableItems.receive()
    }

    override suspend fun release(item: Data) = runCatching {
        check(!isClosed.get()) { "Pool is closed" }

        availableItems.send(item)
    }

    override suspend fun reset() = runCatching {
        availableItems.close()

        initializePool()
    }

    override suspend fun close() = mutex.withLock {
        runCatching {
            if (isClosed.compareAndSet(false, true)) {
                availableItems.close()

                items.forEach(Data::close)
            }
        }
    }
}