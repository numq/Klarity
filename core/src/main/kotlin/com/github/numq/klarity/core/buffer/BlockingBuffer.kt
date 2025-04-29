package com.github.numq.klarity.core.buffer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.util.concurrent.LinkedBlockingQueue

internal class BlockingBuffer<T>(override val capacity: Int) : Buffer<T> {
    private val items by lazy { LinkedBlockingQueue<T>(capacity) }

    override suspend fun poll() = runCatching { runInterruptible(Dispatchers.IO) { items.take() } }

    override suspend fun put(item: T) = runCatching { runInterruptible(Dispatchers.IO) { items.put(item) } }

    override suspend fun flush() = runCatching { items.clear() }
}