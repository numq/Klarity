package com.github.numq.klarity.core.buffer

import kotlinx.coroutines.*
import java.io.Closeable
import java.util.concurrent.LinkedBlockingQueue

internal class BlockingBuffer<T : Closeable>(override val capacity: Int) : Buffer<T> {
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val items by lazy {
        LinkedBlockingQueue<T>(capacity)
    }

    override suspend fun poll() = runCatching {
        runInterruptible(Dispatchers.IO) { items.take() }
    }

    override suspend fun put(item: T) = runCatching {
        runInterruptible(Dispatchers.IO) { items.put(item) }
    }

    override suspend fun flush() = runCatching {
        awaitAll(
            *items.map { item ->
                coroutineScope.async {
                    item.use(items::remove)
                }
            }.toTypedArray()
        )

        Unit
    }

    override suspend fun close() = runCatching {
        flush().getOrDefault(Unit)

        coroutineScope.cancel()
    }
}