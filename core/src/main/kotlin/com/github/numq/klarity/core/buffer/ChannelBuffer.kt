package com.github.numq.klarity.core.buffer

import kotlinx.coroutines.channels.Channel

internal class ChannelBuffer<T>(override val capacity: Int) : Buffer<T> {
    private var items = Channel<T>(capacity)

    override suspend fun take() = runCatching {
        items.receive()
    }

    override suspend fun put(item: T) = runCatching {
        items.send(item)
    }

    override suspend fun clear() = runCatching {
        items.close()

        items = Channel(capacity)
    }

    override suspend fun close() = runCatching {
        items.close()

        Unit
    }
}